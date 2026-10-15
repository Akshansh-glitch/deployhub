package io.deployhub.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** A user's registered app. The name doubles as the Kubernetes resource name and URL host. */
@Entity
@Table(name = "applications")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, unique = true, length = 30)
    private String name;

    @Column(name = "repo_url", nullable = false)
    private String repoUrl;

    @Column(nullable = false)
    private String branch;

    @Column(name = "app_path", nullable = false)
    private String appPath = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Runtime runtime;

    @Column(nullable = false)
    private int port;

    @Column(name = "health_path")
    private String healthPath;

    @Column(nullable = false)
    private int replicas = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Application() {}

    public Application(Long ownerId, String name, String repoUrl, String branch, String appPath,
                       Runtime runtime, int port, String healthPath, int replicas) {
        this.ownerId = ownerId;
        this.name = name;
        this.repoUrl = repoUrl;
        this.branch = branch;
        this.appPath = appPath == null ? "" : appPath;
        this.runtime = runtime;
        this.port = port;
        this.healthPath = healthPath;
        this.replicas = replicas;
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getRepoUrl() { return repoUrl; }
    public String getBranch() { return branch; }
    public String getAppPath() { return appPath; }
    public Runtime getRuntime() { return runtime; }
    public int getPort() { return port; }
    public String getHealthPath() { return healthPath; }
    public int getReplicas() { return replicas; }
    public Instant getCreatedAt() { return createdAt; }

    public void setReplicas(int replicas) { this.replicas = replicas; }
}
