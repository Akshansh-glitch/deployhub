package io.deployhub.k8s;

import io.deployhub.config.DeployHubProperties;
import io.deployhub.domain.Application;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Builds the three Kubernetes objects every DeployHub app gets: a Deployment, a Service and an Ingress.
 * Pure functions with no cluster access, so they are easy to unit-test.
 */
@Component
public class ManifestFactory {

    public static final String APP_LABEL = "app";
    public static final String RUNTIME_LABEL = "deployhub.io/runtime";

    private final DeployHubProperties.Kubernetes cfg;

    public ManifestFactory(DeployHubProperties props) {
        this.cfg = props.kubernetes();
    }

    public String namespace() {
        return cfg.namespace();
    }

    public String host(Application app) {
        return app.getName() + "." + cfg.appDomain();
    }

    public String url(Application app) {
        return "http://" + host(app);
    }

    public Map<String, String> labels(Application app) {
        return Map.of(
                APP_LABEL, app.getName(),
                RUNTIME_LABEL, app.getRuntime().key(),
                "app.kubernetes.io/managed-by", "deployhub");
    }

    public Deployment deployment(Application app, String image) {
        List<LocalObjectReference> pullSecrets = (cfg.pullSecret() == null || cfg.pullSecret().isBlank())
                ? List.of()
                : List.of(new LocalObjectReferenceBuilder().withName(cfg.pullSecret()).build());

        return new DeploymentBuilder()
                .withNewMetadata()
                    .withName(app.getName())
                    .withNamespace(cfg.namespace())
                    .withLabels(labels(app))
                .endMetadata()
                .withNewSpec()
                    .withReplicas(app.getReplicas())
                    .withRevisionHistoryLimit(5)
                    .withNewSelector().withMatchLabels(Map.of(APP_LABEL, app.getName())).endSelector()
                    // maxUnavailable=0 is what gives zero-downtime rolling updates in the demo
                    .withNewStrategy()
                        .withType("RollingUpdate")
                        .withNewRollingUpdate()
                            .withMaxSurge(new IntOrString(1))
                            .withMaxUnavailable(new IntOrString(0))
                        .endRollingUpdate()
                    .endStrategy()
                    .withNewTemplate()
                        .withNewMetadata().withLabels(labels(app)).endMetadata()
                        .withNewSpec()
                            .withImagePullSecrets(pullSecrets)
                            .addNewContainer()
                                .withName("app")
                                .withImage(image)
                                .addNewPort().withName("http").withContainerPort(app.getPort()).endPort()
                                .addNewEnv().withName("PORT").withValue(String.valueOf(app.getPort())).endEnv()
                                .withNewResources()
                                    .addToRequests("cpu", new Quantity("100m"))
                                    .addToRequests("memory", new Quantity("128Mi"))
                                    .addToLimits("cpu", new Quantity("500m"))
                                    .addToLimits("memory", new Quantity("512Mi"))
                                .endResources()
                                // startup probe gives slow starters (Spring Boot) up to 150 s before liveness kicks in
                                .withStartupProbe(probe(app, 5, 30))
                                .withReadinessProbe(probe(app, 5, 3))
                                .withLivenessProbe(probe(app, 10, 3))
                            .endContainer()
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();
    }

    public Service service(Application app) {
        return new ServiceBuilder()
                .withNewMetadata()
                    .withName(app.getName())
                    .withNamespace(cfg.namespace())
                    .withLabels(labels(app))   // the runtime label lets Prometheus find Spring Boot apps
                .endMetadata()
                .withNewSpec()
                    .withType("ClusterIP")
                    .withSelector(Map.of(APP_LABEL, app.getName()))
                    .addNewPort().withName("http").withPort(80).withTargetPort(new IntOrString("http")).endPort()
                .endSpec()
                .build();
    }

    public Ingress ingress(Application app) {
        return new IngressBuilder()
                .withNewMetadata()
                    .withName(app.getName())
                    .withNamespace(cfg.namespace())
                    .withLabels(labels(app))
                .endMetadata()
                .withNewSpec()
                    .withIngressClassName(cfg.ingressClass())
                    .addNewRule()
                        .withHost(host(app))
                        .withNewHttp()
                            .addNewPath()
                                .withPath("/")
                                .withPathType("Prefix")
                                .withNewBackend()
                                    .withNewService()
                                        .withName(app.getName())
                                        .withNewPort().withNumber(80).endPort()
                                    .endService()
                                .endBackend()
                            .endPath()
                        .endHttp()
                    .endRule()
                .endSpec()
                .build();
    }

    /** HTTP probe if the user gave a health path, otherwise "is the port open". */
    Probe probe(Application app, int periodSeconds, int failureThreshold) {
        ProbeBuilder b = new ProbeBuilder()
                .withPeriodSeconds(periodSeconds)
                .withFailureThreshold(failureThreshold)
                .withTimeoutSeconds(2);
        String path = app.getHealthPath();
        if (path != null && !path.isBlank()) {
            b.withNewHttpGet().withPath(path).withPort(new IntOrString("http")).endHttpGet();
        } else {
            b.withNewTcpSocket().withPort(new IntOrString("http")).endTcpSocket();
        }
        return b.build();
    }
}
