package com.secretvault.sync.entity;

import com.secretvault.sync.model.ReconciliationPolicy;
import com.secretvault.sync.model.SyncJobStatus;
import com.secretvault.sync.model.SyncScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Synchronization execution job record tracking status, operations summary, and results.
 */
@Entity
@Table(name = "sync_jobs")
public class SyncJob {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 32)
    private SyncScope scope = SyncScope.WORKSPACE;

    @Column(name = "scope_resource_id")
    private UUID scopeResourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SyncJobStatus status = SyncJobStatus.QUEUED;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_policy", nullable = false, length = 64)
    private ReconciliationPolicy reconciliationPolicy = ReconciliationPolicy.SAFE_RECONCILIATION;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "total_operations", nullable = false)
    private int totalOperations = 0;

    @Column(name = "successful_operations", nullable = false)
    private int successfulOperations = 0;

    @Column(name = "failed_operations", nullable = false)
    private int failedOperations = 0;

    @Column(name = "blocked_operations", nullable = false)
    private int blockedOperations = 0;

    @Column(name = "drift_count", nullable = false)
    private int driftCount = 0;

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public SyncJob() {}

    public SyncJob(
            UUID workspaceId,
            SyncScope scope,
            UUID scopeResourceId,
            boolean dryRun,
            ReconciliationPolicy reconciliationPolicy,
            UUID requestedBy
    ) {
        this.workspaceId = workspaceId;
        this.scope = scope;
        this.scopeResourceId = scopeResourceId;
        this.dryRun = dryRun;
        this.reconciliationPolicy = reconciliationPolicy;
        this.requestedBy = requestedBy;
        this.status = SyncJobStatus.QUEUED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void start() {
        this.status = SyncJobStatus.RUNNING;
        this.startedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void complete(int total, int success, int failed, int blocked, int drift) {
        this.totalOperations = total;
        this.successfulOperations = success;
        this.failedOperations = failed;
        this.blockedOperations = blocked;
        this.driftCount = drift;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
        if (failed > 0 && success > 0) {
            this.status = SyncJobStatus.PARTIAL;
        } else if (failed > 0 && success == 0) {
            this.status = SyncJobStatus.FAILED;
        } else {
            this.status = SyncJobStatus.COMPLETED;
        }
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }

    public SyncScope getScope() { return scope; }
    public void setScope(SyncScope scope) { this.scope = scope; }

    public UUID getScopeResourceId() { return scopeResourceId; }
    public void setScopeResourceId(UUID scopeResourceId) { this.scopeResourceId = scopeResourceId; }

    public SyncJobStatus getStatus() { return status; }
    public void setStatus(SyncJobStatus status) { this.status = status; }

    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }

    public ReconciliationPolicy getReconciliationPolicy() { return reconciliationPolicy; }
    public void setReconciliationPolicy(ReconciliationPolicy reconciliationPolicy) { this.reconciliationPolicy = reconciliationPolicy; }

    public UUID getRequestedBy() { return requestedBy; }
    public void setRequestedBy(UUID requestedBy) { this.requestedBy = requestedBy; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public int getTotalOperations() { return totalOperations; }
    public void setTotalOperations(int totalOperations) { this.totalOperations = totalOperations; }

    public int getSuccessfulOperations() { return successfulOperations; }
    public void setSuccessfulOperations(int successfulOperations) { this.successfulOperations = successfulOperations; }

    public int getFailedOperations() { return failedOperations; }
    public void setFailedOperations(int failedOperations) { this.failedOperations = failedOperations; }

    public int getBlockedOperations() { return blockedOperations; }
    public void setBlockedOperations(int blockedOperations) { this.blockedOperations = blockedOperations; }

    public int getDriftCount() { return driftCount; }
    public void setDriftCount(int driftCount) { this.driftCount = driftCount; }

    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String errorSummary) { this.errorSummary = errorSummary; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
