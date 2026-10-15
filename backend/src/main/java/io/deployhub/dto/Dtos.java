package io.deployhub.dto;

import io.deployhub.domain.DeployState;
import io.deployhub.domain.Runtime;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

/** All request/response shapes of the REST API in one place, so the frontend team has a single reference. */
public final class Dtos {
    private Dtos() {}

    // ---- auth ----
    public record AuthRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100, message = "Password must be at least 8 characters") String password) {}

    public record AuthResponse(String token, String email) {}

    // ---- applications ----
    public record CreateAppRequest(
            @NotBlank @Pattern(regexp = Patterns.APP_NAME,
                    message = "Name must be lowercase letters, numbers and dashes, start with a letter, max 30 characters")
            String name,
            @NotBlank @Pattern(regexp = Patterns.GITHUB_REPO_URL,
                    message = "Use a public GitHub URL like https://github.com/user/repo")
            String repoUrl,
            @NotBlank @Pattern(regexp = Patterns.BRANCH, message = "Branch contains invalid characters")
            String branch,
            @Pattern(regexp = Patterns.APP_PATH, message = "Folder path contains invalid characters")
            String appPath,
            @NotNull Runtime runtime,
            @Min(1) @Max(65535) Integer port,
            @Pattern(regexp = Patterns.HEALTH_PATH, message = "Health check path must start with /")
            String healthPath,
            @Min(1) @Max(5) Integer replicas) {}

    public record AppView(Long id, String name, String repoUrl, String branch, String appPath,
                          Runtime runtime, int port, String healthPath, int replicas, String url,
                          Instant createdAt, DeploymentView latestDeployment) {}

    public record DeploymentView(Long id, DeployState status, String trigger, String image, String imageTag,
                                 String workflowRunUrl, String message, Instant createdAt, Instant finishedAt) {}

    public record ScaleRequest(@NotNull @Min(0) @Max(5) Integer replicas) {}

    // ---- live cluster state ----
    public record PodView(String name, String phase, boolean ready, int restarts, String reason,
                          String startedAt, String imageTag, boolean terminating) {}

    /** state: NOT_DEPLOYED | STOPPED | HEALTHY | UPDATING | UNHEALTHY */
    public record AppStatusView(String state, int desired, int ready, int updated, int available,
                                String image, String url, List<PodView> pods) {}

    public record Point(long t, double v) {}

    public record MetricsView(boolean available, List<Point> cpuMillicores, List<Point> memoryMiB,
                              List<Point> podsAvailable, List<Point> requestsPerSecond) {}

    public record LogsView(String pod, String text) {}

    public record ConfigView(String grafanaUrl, String appDomain, boolean githubConfigured) {}
}
