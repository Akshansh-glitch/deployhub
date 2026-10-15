package io.deployhub;

import io.deployhub.config.DeployHubProperties;
import io.deployhub.domain.Application;
import io.deployhub.domain.Runtime;
import io.deployhub.k8s.ManifestFactory;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ManifestFactoryTest {

    private static ManifestFactory factory(String pullSecret) {
        var props = new DeployHubProperties("x".repeat(40), 1, List.of(),
                new DeployHubProperties.GitHub("t", "Owner", "deployhub", "build-user-app.yml", "main", "deployhub-apps"),
                new DeployHubProperties.Kubernetes("apps", pullSecret, "127.0.0.1.nip.io", "nginx", 5),
                new DeployHubProperties.Prometheus("http://localhost:9090"),
                new DeployHubProperties.Grafana("http://localhost:3000"));
        return new ManifestFactory(props);
    }

    private static Application app(String healthPath) {
        return new Application(1L, "hello", "https://github.com/a/b", "main", "", Runtime.SPRINGBOOT, 8080, healthPath, 2);
    }

    @Test
    void deploymentUsesZeroDowntimeRollingUpdate() {
        Deployment d = factory("ghcr-pull").deployment(app(null), "ghcr.io/owner/deployhub-apps:hello-d1");

        assertThat(d.getMetadata().getNamespace()).isEqualTo("apps");
        assertThat(d.getSpec().getReplicas()).isEqualTo(2);
        assertThat(d.getSpec().getStrategy().getRollingUpdate().getMaxUnavailable().getIntVal()).isZero();
        assertThat(d.getSpec().getSelector().getMatchLabels()).containsEntry("app", "hello");
        assertThat(d.getSpec().getTemplate().getMetadata().getLabels())
                .containsEntry("deployhub.io/runtime", "springboot");
        assertThat(d.getSpec().getTemplate().getSpec().getImagePullSecrets()).hasSize(1);
    }

    @Test
    void containerExposesNamedPortWithLimitsAndProbes() {
        Container c = factory("").deployment(app(null), "img:1").getSpec().getTemplate().getSpec().getContainers().get(0);

        assertThat(c.getPorts().get(0).getName()).isEqualTo("http");
        assertThat(c.getPorts().get(0).getContainerPort()).isEqualTo(8080);
        assertThat(c.getResources().getLimits()).containsKeys("cpu", "memory");
        assertThat(c.getReadinessProbe().getTcpSocket()).isNotNull();
        assertThat(c.getStartupProbe()).isNotNull();
    }

    @Test
    void healthPathSwitchesToHttpProbe() {
        Container c = factory("").deployment(app("/actuator/health"), "img:1")
                .getSpec().getTemplate().getSpec().getContainers().get(0);
        assertThat(c.getReadinessProbe().getHttpGet().getPath()).isEqualTo("/actuator/health");
    }

    @Test
    void noPullSecretWhenNotConfigured() {
        Deployment d = factory("").deployment(app(null), "img:1");
        assertThat(d.getSpec().getTemplate().getSpec().getImagePullSecrets()).isEmpty();
    }

    @Test
    void ingressRoutesHostToService() {
        var ingress = factory("").ingress(app(null));
        var rule = ingress.getSpec().getRules().get(0);
        assertThat(rule.getHost()).isEqualTo("hello.127.0.0.1.nip.io");
        assertThat(rule.getHttp().getPaths().get(0).getBackend().getService().getName()).isEqualTo("hello");
        assertThat(ingress.getSpec().getIngressClassName()).isEqualTo("nginx");
    }

    @Test
    void serviceCarriesRuntimeLabelForPrometheus() {
        var svc = factory("").service(app(null));
        assertThat(svc.getMetadata().getLabels()).containsEntry("deployhub.io/runtime", "springboot");
        assertThat(svc.getSpec().getPorts().get(0).getName()).isEqualTo("http");
    }
}
