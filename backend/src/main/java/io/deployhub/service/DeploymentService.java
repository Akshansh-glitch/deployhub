package io.deployhub.service;

import io.deployhub.config.DeployHubProperties;
import io.deployhub.domain.Application;
import io.deployhub.domain.DeployState;
import io.deployhub.domain.DeploymentRecord;
import io.deployhub.domain.DeploymentRecord.Trigger;
import io.deployhub.dto.Dtos.DeploymentView;
import io.deployhub.github.GitHubActionsClient;
import io.deployhub.k8s.KubernetesService;
import io.deployhub.k8s.KubernetesService.Rollout;
import io.deployhub.repo.ApplicationRepository;
import io.deployhub.repo.DeploymentRepository;
import io.deployhub.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

/**
 * The deployment pipeline:
 *  1. deploy()  creates a record and asks GitHub Actions to build the image  (QUEUED -> BUILDING)
 *  2. poll()    every 5 s checks running builds; when one succeeds, applies it to Kubernetes
 *                                                                           (BUILDING -> IMAGE_PUSHED -> DEPLOYING)
 *  3. poll()    then watches the rollout until all pods are ready           (DEPLOYING -> RUNNING)
 */
@Service
public class DeploymentService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentService.class);

    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration RUN_LOOKUP_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration ROLLOUT_TIMEOUT = Duration.ofMinutes(5);
    /** A pod in CrashLoopBackOff/ImagePullBackOff for this long fails the deployment. */
    private static final Duration FAILURE_GRACE = Duration.ofSeconds(60);

    private static final EnumSet<DeployState> ACTIVE =
            EnumSet.of(DeployState.QUEUED, DeployState.BUILDING, DeployState.IMAGE_PUSHED, DeployState.DEPLOYING);

    private final DeploymentRepository deployments;
    private final ApplicationRepository apps;
    private final GitHubActionsClient github;
    private final KubernetesService kubernetes;
    private final DeployHubProperties.GitHub githubCfg;

    public DeploymentService(DeploymentRepository deployments, ApplicationRepository apps,
                             GitHubActionsClient github, KubernetesService kubernetes, DeployHubProperties props) {
        this.deployments = deployments;
        this.apps = apps;
        this.github = github;
        this.kubernetes = kubernetes;
        this.githubCfg = props.github();
    }

    public List<DeploymentView> history(Application app) {
        return deployments.findByApplicationIdOrderByIdDesc(app.getId()).stream()
                .map(ApplicationService::toView).toList();
    }

    /** Starts build + deploy for the app's configured branch. */
    public DeploymentView deploy(Application app) {
        if (!github.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GitHub is not configured on the server. Set GITHUB_TOKEN and GITHUB_OWNER for the backend.");
        }
        ensureNoActiveDeployment(app);

        DeploymentRecord d = deployments.save(new DeploymentRecord(app.getId(), DeployState.QUEUED, Trigger.BUILD));
        d.setImage(ImageNames.forDeployment(githubCfg.owner(), githubCfg.imageRepository(), app.getName(), d.getId()));
        try {
            github.dispatch(d.getId(), app, d.getImage());
            d.transition(DeployState.BUILDING, "Build requested from GitHub Actions");
        } catch (RestClientResponseException e) {
            log.warn("Workflow dispatch failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            d.transition(DeployState.BUILD_FAILED, dispatchHint(e));
        }
        return ApplicationService.toView(deployments.save(d));
    }

    /** Re-deploys the image of an earlier successful deployment, skipping the build. */
    public DeploymentView rollback(Application app, Long targetDeploymentId) {
        DeploymentRecord target = deployments.findById(targetDeploymentId)
                .filter(t -> t.getApplicationId().equals(app.getId()))
                .orElseThrow(() -> ApiException.notFound("Deployment"));
        if (target.getImage() == null
                || !(target.getStatus() == DeployState.RUNNING || target.getStatus() == DeployState.SUPERSEDED)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only a deployment that ran successfully can be rolled back to");
        }
        ensureNoActiveDeployment(app);

        DeploymentRecord d = new DeploymentRecord(app.getId(), DeployState.IMAGE_PUSHED, Trigger.ROLLBACK);
        d.setImage(target.getImage());
        d = deployments.save(d);
        startRollout(d, app, "Rolling back to deployment #" + target.getId());
        return ApplicationService.toView(d);
    }

    @Scheduled(fixedDelayString = "${deployhub.poll-interval-ms:5000}")
    public void poll() {
        for (DeploymentRecord d : deployments.findByStatusIn(List.of(DeployState.BUILDING, DeployState.DEPLOYING))) {
            try {
                Application app = apps.findById(d.getApplicationId()).orElse(null);
                if (app == null) continue;
                if (d.getStatus() == DeployState.BUILDING) pollBuild(d, app);
                else pollRollout(d, app);
            } catch (Exception e) {
                log.warn("Polling deployment {} failed: {}", d.getId(), e.getMessage());
            }
        }
    }

    // ---- internals ----

    private void pollBuild(DeploymentRecord d, Application app) {
        Duration age = Duration.between(d.getCreatedAt(), Instant.now());
        var run = github.findRun(d.getId());

        if (run.isEmpty()) {
            if (age.compareTo(RUN_LOOKUP_TIMEOUT) > 0) {
                d.transition(DeployState.BUILD_FAILED, "GitHub never started the build workflow. Check the Actions tab of the DeployHub repo.");
                deployments.save(d);
            }
            return;
        }
        var r = run.get();
        if (d.getWorkflowRunId() == null) {
            d.setWorkflowRun(r.id(), r.htmlUrl());
            deployments.save(d);
        }
        if (!r.completed()) {
            if (age.compareTo(BUILD_TIMEOUT) > 0) {
                d.transition(DeployState.BUILD_FAILED, "Build timed out after " + BUILD_TIMEOUT.toMinutes() + " minutes");
                deployments.save(d);
            }
            return;
        }
        if (!r.succeeded()) {
            d.transition(DeployState.BUILD_FAILED, "Build finished with result '" + r.conclusion() + "'. Open the workflow run for the logs.");
            deployments.save(d);
            return;
        }
        d.transition(DeployState.IMAGE_PUSHED, "Image pushed: " + ImageNames.tagOf(d.getImage()));
        deployments.save(d);
        startRollout(d, app, "Rolling out " + ImageNames.tagOf(d.getImage()));
    }

    private void startRollout(DeploymentRecord d, Application app, String message) {
        try {
            kubernetes.apply(app, d.getImage());
            d.transition(DeployState.DEPLOYING, message);
        } catch (Exception e) {
            log.warn("Applying manifests failed", e);
            d.transition(DeployState.DEPLOY_FAILED, "Kubernetes rejected the deployment: " + e.getMessage());
        }
        deployments.save(d);
    }

    private void pollRollout(DeploymentRecord d, Application app) {
        Duration elapsed = Duration.between(d.getUpdatedAt(), Instant.now());
        Rollout r = kubernetes.rollout(app, d.getImage());
        switch (r.phase()) {
            case COMPLETE -> {
                for (DeploymentRecord old : deployments.findByApplicationIdAndStatus(app.getId(), DeployState.RUNNING)) {
                    old.transition(DeployState.SUPERSEDED, null);
                    deployments.save(old);
                }
                d.transition(DeployState.RUNNING, r.detail());
                deployments.save(d);
            }
            case REPLACED -> {
                d.transition(DeployState.SUPERSEDED, r.detail());
                deployments.save(d);
            }
            case FAILING -> {
                if (elapsed.compareTo(FAILURE_GRACE) > 0) {
                    d.transition(DeployState.DEPLOY_FAILED, r.detail() + ". Previous version keeps serving traffic.");
                    deployments.save(d);
                }
            }
            case PROGRESSING -> {
                if (elapsed.compareTo(ROLLOUT_TIMEOUT) > 0) {
                    d.transition(DeployState.DEPLOY_FAILED, "Rollout did not finish in 5 minutes (" + r.detail() + ")");
                    deployments.save(d);
                }
            }
        }
    }

    private void ensureNoActiveDeployment(Application app) {
        if (deployments.existsByApplicationIdAndStatusIn(app.getId(), ACTIVE)) {
            throw new ApiException(HttpStatus.CONFLICT, "A deployment is already in progress for this app");
        }
    }

    private static String dispatchHint(RestClientResponseException e) {
        int code = e.getStatusCode().value();
        return switch (code) {
            case 401 -> "GitHub rejected the token (401). Check GITHUB_TOKEN.";
            case 403 -> "GitHub token lacks permission (403). It needs Actions: read and write on the DeployHub repo.";
            case 404 -> "Workflow not found (404). Check GITHUB_OWNER/GITHUB_REPO and that build-user-app.yml is on the default branch.";
            case 422 -> "GitHub refused the inputs (422): " + e.getResponseBodyAsString();
            default -> "Could not start the build (GitHub returned " + code + ")";
        };
    }
}
