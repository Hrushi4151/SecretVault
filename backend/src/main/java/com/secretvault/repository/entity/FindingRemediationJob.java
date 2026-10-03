package com.secretvault.repository.entity;

import com.secretvault.repository.model.RemediationAction;
import com.secretvault.repository.model.RemediationStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "finding_remediation_jobs")
public class FindingRemediationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id = UUID.randomUUID();

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "finding_id", nullable = false)
    private UUID findingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "remediation_action", nullable = false, length = 64)
    private RemediationAction remediationAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RemediationStatus status = RemediationStatus.PENDING;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public RemediationAction getAction() {
        return remediationAction;
    }

    public void setAction(RemediationAction action) {
        this.remediationAction = action;
    }

    public UUID getInitiatedBy() {
        return actorId;
    }

    public void setInitiatedBy(UUID initiatedBy) {
        this.actorId = initiatedBy;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public String getNotes() {
        return detailsJson;
    }

    public void setNotes(String notes) {
        this.detailsJson = notes;
    }

    public FindingRemediationJob() {}

    public FindingRemediationJob(UUID workspaceId, UUID findingId, RemediationAction remediationAction, UUID actorId) {
        this.workspaceId = workspaceId;
        this.findingId = findingId;
        this.remediationAction = remediationAction;
        this.actorId = actorId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public UUID getFindingId() {
        return findingId;
    }

    public void setFindingId(UUID findingId) {
        this.findingId = findingId;
    }

    public RemediationAction getRemediationAction() {
        return remediationAction;
    }

    public void setRemediationAction(RemediationAction remediationAction) {
        this.remediationAction = remediationAction;
    }

    public RemediationStatus getStatus() {
        return status;
    }

    public void setStatus(RemediationStatus status) {
        this.status = status;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
