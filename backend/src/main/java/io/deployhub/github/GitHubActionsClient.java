package io.deployhub.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.deployhub.config.DeployHubProperties;
import io.deployhub.domain.Application;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Triggers the build-user-app workflow and finds its run afterwards.
 *
 * The dispatch call has not traditionally returned the run id, so the workflow sets
 * run-name: "deploy-<deployment id>" and we look the run up by its display title.
 */
@Component
public class GitHubActionsClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkflowRun(long id,
                              @JsonProperty("display_title") String displayTitle,
                              String status,
                              String conclusion,
                              @JsonProperty("html_url") String htmlUrl) {
        public boolean completed() {
            return "completed".equals(status);
        }

        public boolean succeeded() {
            return "success".equals(conclusion);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RunsPage(@JsonProperty("workflow_runs") List<WorkflowRun> workflowRuns) {}

    private final DeployHubProperties.GitHub cfg;
    private final RestClient http;

    public GitHubActionsClient(RestClient.Builder builder, DeployHubProperties props) {
        this.cfg = props.github();
        RestClient.Builder b = builder
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");
        if (cfg.configured()) {
            b.defaultHeader("Authorization", "Bearer " + cfg.token());
        }
        this.http = b.build();
    }

    public boolean configured() {
        return cfg.configured();
    }

    public static String runName(long deploymentId) {
        return "deploy-" + deploymentId;
    }

    public void dispatch(long deploymentId, Application app, String image) {
        Map<String, Object> body = Map.of(
                "ref", cfg.workflowRef(),
                "inputs", Map.of(
                        "deployment_id", String.valueOf(deploymentId),
                        "repo_url", app.getRepoUrl(),
                        "ref", app.getBranch(),
                        "app_path", app.getAppPath() == null ? "" : app.getAppPath(),
                        "runtime", app.getRuntime().key(),
                        "image", image));

        http.post()
                .uri("/repos/{owner}/{repo}/actions/workflows/{file}/dispatches",
                        cfg.owner(), cfg.repo(), cfg.workflowFile())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public Optional<WorkflowRun> findRun(long deploymentId) {
        RunsPage page = http.get()
                .uri("/repos/{owner}/{repo}/actions/workflows/{file}/runs?event=workflow_dispatch&per_page=50",
                        cfg.owner(), cfg.repo(), cfg.workflowFile())
                .retrieve()
                .body(RunsPage.class);
        if (page == null || page.workflowRuns() == null) {
            return Optional.empty();
        }
        String wanted = runName(deploymentId);
        return page.workflowRuns().stream().filter(r -> wanted.equals(r.displayTitle())).findFirst();
    }
}
