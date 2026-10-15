package io.deployhub.web;

import io.deployhub.config.AuthUser;
import io.deployhub.domain.Application;
import io.deployhub.dto.Dtos.*;
import io.deployhub.k8s.KubernetesService;
import io.deployhub.metrics.MetricsService;
import io.deployhub.service.ApplicationService;
import io.deployhub.service.DeploymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/apps")
public class ApplicationController {

    private final ApplicationService apps;
    private final DeploymentService deployments;
    private final KubernetesService kubernetes;
    private final MetricsService metrics;

    public ApplicationController(ApplicationService apps, DeploymentService deployments,
                                 KubernetesService kubernetes, MetricsService metrics) {
        this.apps = apps;
        this.deployments = deployments;
        this.kubernetes = kubernetes;
        this.metrics = metrics;
    }

    @GetMapping
    public List<AppView> list(@AuthenticationPrincipal AuthUser user) {
        return apps.list(user.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppView create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreateAppRequest req) {
        return apps.create(user.id(), req);
    }

    @GetMapping("/{id}")
    public AppView get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return apps.view(apps.owned(id, user.id()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        apps.delete(id, user.id());
    }

    // ---- deployments ----

    @PostMapping("/{id}/deploy")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DeploymentView deploy(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return deployments.deploy(apps.owned(id, user.id()));
    }

    @GetMapping("/{id}/deployments")
    public List<DeploymentView> history(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return deployments.history(apps.owned(id, user.id()));
    }

    @PostMapping("/{id}/deployments/{deploymentId}/rollback")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DeploymentView rollback(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                   @PathVariable Long deploymentId) {
        return deployments.rollback(apps.owned(id, user.id()), deploymentId);
    }

    // ---- live state ----

    @GetMapping("/{id}/status")
    public AppStatusView status(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return kubernetes.status(apps.owned(id, user.id()));
    }

    @PatchMapping("/{id}/scale")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void scale(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                      @Valid @RequestBody ScaleRequest req) {
        apps.scale(id, user.id(), req.replicas());
    }

    @DeleteMapping("/{id}/pods/{pod}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePod(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @PathVariable String pod) {
        if (!kubernetes.deletePod(apps.owned(id, user.id()), pod)) {
            throw ApiException.notFound("Pod");
        }
    }

    @GetMapping("/{id}/pods/{pod}/logs")
    public LogsView logs(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @PathVariable String pod,
                         @RequestParam(defaultValue = "200") int tail) {
        Application app = apps.owned(id, user.id());
        String text = kubernetes.logs(app, pod, Math.min(Math.max(tail, 10), 2000))
                .orElseThrow(() -> ApiException.notFound("Pod"));
        return new LogsView(pod, text);
    }

    @GetMapping("/{id}/metrics")
    public MetricsView metrics(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                               @RequestParam(defaultValue = "15") int minutes) {
        return metrics.fetch(apps.owned(id, user.id()), Math.min(Math.max(minutes, 5), 360));
    }
}
