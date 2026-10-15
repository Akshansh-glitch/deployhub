package io.deployhub.k8s;

import io.deployhub.domain.Application;
import io.deployhub.dto.Dtos.AppStatusView;
import io.deployhub.dto.Dtos.PodView;
import io.deployhub.service.ImageNames;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Everything DeployHub does to the cluster goes through this class. */
@Service
public class KubernetesService {

    /** Container states that mean "this rollout is not going to succeed on its own". */
    private static final Set<String> FAILURE_REASONS = Set.of(
            "ImagePullBackOff", "ErrImagePull", "InvalidImageName",
            "CrashLoopBackOff", "CreateContainerConfigError", "CreateContainerError");

    public enum Phase { PROGRESSING, COMPLETE, FAILING, REPLACED }

    public record Rollout(Phase phase, String detail) {}

    private final KubernetesClient client;
    private final ManifestFactory manifests;

    public KubernetesService(KubernetesClient client, ManifestFactory manifests) {
        this.client = client;
        this.manifests = manifests;
    }

    /** Create or update Deployment + Service + Ingress. Changing the image triggers a rolling update. */
    public void apply(Application app, String image) {
        client.resource(manifests.service(app)).forceConflicts().serverSideApply();
        client.resource(manifests.ingress(app)).forceConflicts().serverSideApply();
        client.resource(manifests.deployment(app, image)).forceConflicts().serverSideApply();
    }

    public Rollout rollout(Application app, String expectedImage) {
        Deployment d = deployment(app);
        if (d == null) {
            return new Rollout(Phase.PROGRESSING, "Waiting for Deployment to appear");
        }
        String current = d.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
        if (!Objects.equals(current, expectedImage)) {
            return new Rollout(Phase.REPLACED, "A newer deployment replaced this one");
        }

        int desired = nz(d.getSpec().getReplicas());
        var s = d.getStatus();
        long generation = d.getMetadata().getGeneration() == null ? 0 : d.getMetadata().getGeneration();
        long observed = s == null || s.getObservedGeneration() == null ? -1 : s.getObservedGeneration();
        int total = s == null ? 0 : nz(s.getReplicas());
        int updated = s == null ? 0 : nz(s.getUpdatedReplicas());
        int available = s == null ? 0 : nz(s.getAvailableReplicas());

        if (observed >= generation && updated == desired && available == desired && total == desired) {
            return new Rollout(Phase.COMPLETE, desired + "/" + desired + " pods ready");
        }

        for (Pod pod : pods(app)) {
            if (!pod.getSpec().getContainers().isEmpty()
                    && Objects.equals(pod.getSpec().getContainers().get(0).getImage(), expectedImage)) {
                Optional<String> problem = failureReason(pod);
                if (problem.isPresent()) {
                    return new Rollout(Phase.FAILING, problem.get());
                }
            }
        }
        return new Rollout(Phase.PROGRESSING, updated + "/" + desired + " pods updated, " + available + " available");
    }

    public AppStatusView status(Application app) {
        String url = manifests.url(app);
        Deployment d = deployment(app);
        if (d == null) {
            return new AppStatusView("NOT_DEPLOYED", app.getReplicas(), 0, 0, 0, null, url, List.of());
        }
        int desired = nz(d.getSpec().getReplicas());
        var s = d.getStatus();
        int total = s == null ? 0 : nz(s.getReplicas());
        int ready = s == null ? 0 : nz(s.getReadyReplicas());
        int updated = s == null ? 0 : nz(s.getUpdatedReplicas());
        int available = s == null ? 0 : nz(s.getAvailableReplicas());

        String state;
        if (desired == 0) state = "STOPPED";
        else if (available == desired && updated == desired && total == desired) state = "HEALTHY";
        else if (updated < desired || total > desired) state = "UPDATING";
        else state = "UNHEALTHY";

        String image = d.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
        List<PodView> pods = pods(app).stream().map(this::toView).toList();
        return new AppStatusView(state, desired, ready, updated, available, image, url, pods);
    }

    public void scale(Application app, int replicas) {
        if (deployment(app) != null) {
            client.apps().deployments().inNamespace(manifests.namespace()).withName(app.getName()).scale(replicas);
        }
    }

    /** Returns false if the pod does not exist or belongs to a different app. */
    public boolean deletePod(Application app, String podName) {
        Pod pod = client.pods().inNamespace(manifests.namespace()).withName(podName).get();
        if (pod == null || !app.getName().equals(label(pod, ManifestFactory.APP_LABEL))) {
            return false;
        }
        client.pods().inNamespace(manifests.namespace()).withName(podName).delete();
        return true;
    }

    public Optional<String> logs(Application app, String podName, int tailLines) {
        Pod pod = client.pods().inNamespace(manifests.namespace()).withName(podName).get();
        if (pod == null || !app.getName().equals(label(pod, ManifestFactory.APP_LABEL))) {
            return Optional.empty();
        }
        return Optional.ofNullable(client.pods().inNamespace(manifests.namespace())
                .withName(podName).tailingLines(tailLines).getLog());
    }

    public void deleteAll(Application app) {
        String ns = manifests.namespace();
        client.network().v1().ingresses().inNamespace(ns).withName(app.getName()).delete();
        client.services().inNamespace(ns).withName(app.getName()).delete();
        client.apps().deployments().inNamespace(ns).withName(app.getName()).delete();
    }

    // ---- helpers ----

    private Deployment deployment(Application app) {
        return client.apps().deployments().inNamespace(manifests.namespace()).withName(app.getName()).get();
    }

    private List<Pod> pods(Application app) {
        return client.pods().inNamespace(manifests.namespace())
                .withLabel(ManifestFactory.APP_LABEL, app.getName())
                .list().getItems();
    }

    private PodView toView(Pod pod) {
        List<ContainerStatus> statuses = pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null
                ? List.of() : pod.getStatus().getContainerStatuses();
        boolean ready = !statuses.isEmpty() && statuses.stream().allMatch(cs -> Boolean.TRUE.equals(cs.getReady()));
        int restarts = statuses.stream().mapToInt(cs -> nz(cs.getRestartCount())).sum();
        String reason = statuses.stream().map(KubernetesService::stateReason)
                .filter(Objects::nonNull).findFirst().orElse(null);
        String image = pod.getSpec().getContainers().isEmpty() ? null : pod.getSpec().getContainers().get(0).getImage();
        return new PodView(
                pod.getMetadata().getName(),
                pod.getStatus() == null ? "Unknown" : pod.getStatus().getPhase(),
                ready,
                restarts,
                reason,
                pod.getStatus() == null ? null : pod.getStatus().getStartTime(),
                ImageNames.tagOf(image),
                pod.getMetadata().getDeletionTimestamp() != null);
    }

    private static Optional<String> failureReason(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
            return Optional.empty();
        }
        for (ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
            var waiting = cs.getState() == null ? null : cs.getState().getWaiting();
            if (waiting != null && FAILURE_REASONS.contains(waiting.getReason())) {
                String msg = waiting.getMessage() == null ? "" : ": " + waiting.getMessage();
                return Optional.of(waiting.getReason() + msg);
            }
        }
        return Optional.empty();
    }

    private static String stateReason(ContainerStatus cs) {
        if (cs.getState() == null) return null;
        if (cs.getState().getWaiting() != null) return cs.getState().getWaiting().getReason();
        if (cs.getState().getTerminated() != null) return cs.getState().getTerminated().getReason();
        return null;
    }

    private static String label(Pod pod, String key) {
        return pod.getMetadata().getLabels() == null ? null : pod.getMetadata().getLabels().get(key);
    }

    private static int nz(Integer i) {
        return i == null ? 0 : i;
    }
}
