package io.deployhub.service;

import io.deployhub.config.DeployHubProperties;
import io.deployhub.domain.Application;
import io.deployhub.domain.DeploymentRecord;
import io.deployhub.dto.Dtos.AppView;
import io.deployhub.dto.Dtos.CreateAppRequest;
import io.deployhub.dto.Dtos.DeploymentView;
import io.deployhub.k8s.KubernetesService;
import io.deployhub.k8s.ManifestFactory;
import io.deployhub.repo.ApplicationRepository;
import io.deployhub.repo.DeploymentRepository;
import io.deployhub.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ApplicationService {

    private final ApplicationRepository apps;
    private final DeploymentRepository deployments;
    private final KubernetesService kubernetes;
    private final ManifestFactory manifests;
    private final int maxReplicas;

    public ApplicationService(ApplicationRepository apps, DeploymentRepository deployments,
                              KubernetesService kubernetes, ManifestFactory manifests, DeployHubProperties props) {
        this.apps = apps;
        this.deployments = deployments;
        this.kubernetes = kubernetes;
        this.manifests = manifests;
        this.maxReplicas = props.kubernetes().maxReplicas();
    }

    public Application owned(Long appId, Long userId) {
        return apps.findByIdAndOwnerId(appId, userId).orElseThrow(() -> ApiException.notFound("Application"));
    }

    public List<AppView> list(Long userId) {
        return apps.findByOwnerIdOrderByCreatedAtDesc(userId).stream().map(this::view).toList();
    }

    public AppView create(Long userId, CreateAppRequest req) {
        if (apps.existsByName(req.name())) {
            throw new ApiException(HttpStatus.CONFLICT, "The name '" + req.name() + "' is taken. Names are shared across all users because they become URLs.");
        }
        String path = normalisePath(req.appPath());
        int port = req.port() != null ? req.port() : req.runtime().defaultPort();
        int replicas = req.replicas() != null ? req.replicas() : 1;
        String health = req.healthPath() == null || req.healthPath().isBlank() ? null : req.healthPath();

        Application app = new Application(userId, req.name(), stripGitSuffix(req.repoUrl()), req.branch(),
                path, req.runtime(), port, health, replicas);
        return view(apps.save(app));
    }

    @Transactional
    public void delete(Long appId, Long userId) {
        Application app = owned(appId, userId);
        kubernetes.deleteAll(app);
        apps.delete(app);   // deployments are removed by ON DELETE CASCADE
    }

    @Transactional
    public void scale(Long appId, Long userId, int replicas) {
        if (replicas > maxReplicas) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Maximum is " + maxReplicas + " replicas");
        }
        Application app = owned(appId, userId);
        app.setReplicas(replicas);
        apps.save(app);
        kubernetes.scale(app, replicas);
    }

    public AppView view(Application app) {
        DeploymentView latest = deployments.findFirstByApplicationIdOrderByIdDesc(app.getId())
                .map(ApplicationService::toView).orElse(null);
        return new AppView(app.getId(), app.getName(), app.getRepoUrl(), app.getBranch(), app.getAppPath(),
                app.getRuntime(), app.getPort(), app.getHealthPath(), app.getReplicas(),
                manifests.url(app), app.getCreatedAt(), latest);
    }

    public static DeploymentView toView(DeploymentRecord d) {
        return new DeploymentView(d.getId(), d.getStatus(), d.getTrigger().name(), d.getImage(),
                ImageNames.tagOf(d.getImage()), d.getWorkflowRunUrl(), d.getMessage(),
                d.getCreatedAt(), d.getFinishedAt());
    }

    static String normalisePath(String path) {
        if (path == null) return "";
        String p = path.trim();
        while (p.startsWith("/")) p = p.substring(1);
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        if (p.contains("..")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Folder path cannot contain '..'");
        }
        return p;
    }

    static String stripGitSuffix(String url) {
        String u = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        return u.endsWith(".git") ? u.substring(0, u.length() - 4) : u;
    }
}
