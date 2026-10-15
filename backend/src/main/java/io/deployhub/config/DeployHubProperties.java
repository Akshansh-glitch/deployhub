package io.deployhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Everything under the "deployhub:" key in application.yml. */
@ConfigurationProperties(prefix = "deployhub")
public record DeployHubProperties(
        String jwtSecret,
        int jwtTtlHours,
        List<String> corsOrigins,
        GitHub github,
        Kubernetes kubernetes,
        Prometheus prometheus,
        Grafana grafana) {

    public record GitHub(String token, String owner, String repo, String workflowFile,
                         String workflowRef, String imageRepository) {
        public boolean configured() {
            return token != null && !token.isBlank() && owner != null && !owner.isBlank();
        }
    }

    public record Kubernetes(String namespace, String pullSecret, String appDomain,
                             String ingressClass, int maxReplicas) {}

    public record Prometheus(String url) {}

    public record Grafana(String url) {}
}
