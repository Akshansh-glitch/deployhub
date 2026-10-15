package io.deployhub.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** One attempt to get a version of an app running. Named *Record to avoid clashing with the k8s Deployment type. */
@Entity
@Table(name = "deployments")
public class DeploymentRecord {

    public enum Trigger { BUILD, ROLLBACK }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    private String image;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeployState status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Trigger trigger = Trigger.BUILD;

    @Column(name = "workflow_run_id")
    private Long workflowRunId;

    @Column(name = "workflow_run_url")
    private String workflowRunUrl;

    @Column(length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected DeploymentRecord() {}

    public DeploymentRecord(Long applicationId, DeployState status, Trigger trigger) {
        this.applicationId = applicationId;
        this.status = status;
        this.trigger = trigger;
    }

    /** Moves to a new state, records when it happened and an optional human-readable message. */
    public void transition(DeployState next, String message) {
        this.status = next;
        this.updatedAt = Instant.now();
        if (message != null) {
            this.message = message.length() > 1000 ? message.substring(0, 1000) : message;
        }
        if (!next.isActive() && finishedAt == null) {
            this.finishedAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public Long getApplicationId() { return applicationId; }
    public String getImage() { return image; }
    public DeployState getStatus() { return status; }
    public Trigger getTrigger() { return trigger; }
    public Long getWorkflowRunId() { return workflowRunId; }
    public String getWorkflowRunUrl() { return workflowRunUrl; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getFinishedAt() { return finishedAt; }

    public void setImage(String image) { this.image = image; }
    public void setWorkflowRun(Long id, String url) {
        this.workflowRunId = id;
        this.workflowRunUrl = url;
    }
}
