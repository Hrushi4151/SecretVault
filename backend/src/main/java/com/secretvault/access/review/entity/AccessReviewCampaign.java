package com.secretvault.access.review.entity;

import com.secretvault.access.model.AccessScope;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Access Review Campaign Entity.
 * Represents a time-bound governance campaign to recertify or revoke standing and temporary permissions.
 */
@Entity
@Table(name = "access_review_campaigns")
public class AccessReviewCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 32)
    private AccessScope scopeType = AccessScope.WORKSPACE;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "environment_id")
    private UUID environmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CampaignStatus status = CampaignStatus.OPEN;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "due_date", nullable = false)
    private Instant dueDate;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "total_items_count", nullable = false)
    private int totalItemsCount = 0;

    @Column(name = "decided_items_count", nullable = false)
    private int decidedItemsCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public AccessReviewCampaign() {
    }

    public AccessReviewCampaign(
            UUID workspaceId,
            String name,
            String description,
            AccessScope scopeType,
            UUID projectId,
            UUID environmentId,
            UUID createdBy,
            Instant dueDate
    ) {
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = description;
        this.scopeType = Objects.requireNonNullElse(scopeType, AccessScope.WORKSPACE);
        this.projectId = projectId;
        this.environmentId = environmentId;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
        this.dueDate = Objects.requireNonNull(dueDate, "dueDate must not be null");
        this.status = CampaignStatus.OPEN;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isComplete() {
        return totalItemsCount > 0 && decidedItemsCount >= totalItemsCount;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public AccessScope getScopeType() {
        return scopeType;
    }

    public void setScopeType(AccessScope scopeType) {
        this.scopeType = scopeType;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public UUID getEnvironmentId() {
        return environmentId;
    }

    public void setEnvironmentId(UUID environmentId) {
        this.environmentId = environmentId;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public void setStatus(CampaignStatus status) {
        this.status = status;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getDueDate() {
        return dueDate;
    }

    public void setDueDate(Instant dueDate) {
        this.dueDate = dueDate;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public int getTotalItemsCount() {
        return totalItemsCount;
    }

    public void setTotalItemsCount(int totalItemsCount) {
        this.totalItemsCount = totalItemsCount;
    }

    public int getDecidedItemsCount() {
        return decidedItemsCount;
    }

    public void setDecidedItemsCount(int decidedItemsCount) {
        this.decidedItemsCount = decidedItemsCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
