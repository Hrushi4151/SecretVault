package com.secretvault.access.privileged.entity;

import com.secretvault.access.privileged.model.PrivilegedAction;
import com.secretvault.access.privileged.model.PrivilegedPolicyScope;
import com.secretvault.access.privileged.model.PrivilegedRequestStatus;
import com.secretvault.auth.stepup.model.StepUpFactor;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Privileged Access Request Entity.
 * Tracks elevation requests through their lifecycle: PENDING -> APPROVED/REJECTED -> EXECUTED/REVOKED/EXPIRED.
 */
@Entity
@Table(name = "privileged_access_requests")
public class PrivilegedAccessRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id = UUID.randomUUID();

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "target_user_id", nullable = false)
    private UUID targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 64)
    private PrivilegedAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 32)
    private PrivilegedPolicyScope scopeType = PrivilegedPolicyScope.WORKSPACE;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "environment_id")
    private UUID environmentId;

    @Column(name = "secret_id")
    private UUID secretId;

    @Column(name = "requested_permissions")
    private String requestedPermissions;

    @Column(name = "justification", nullable = false, columnDefinition = "TEXT")
    private String justification;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PrivilegedRequestStatus status = PrivilegedRequestStatus.PENDING;

    @Column(name = "is_break_glass", nullable = false)
    private boolean isBreakGlass = false;

    @Column(name = "required_quorum", nullable = false)
    private int requiredQuorum = 1;

    @Column(name = "current_approvals_count", nullable = false)
    private int currentApprovalsCount = 0;

    @Column(name = "step_up_proof")
    private String stepUpProof;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_up_factor", length = 64)
    private StepUpFactor stepUpFactor;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "revocation_reason", columnDefinition = "TEXT")
    private String revocationReason;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public PrivilegedAccessRequest() {
    }

    public PrivilegedAccessRequest(
            UUID workspaceId,
            UUID requesterId,
            UUID targetUserId,
            PrivilegedAction action,
            PrivilegedPolicyScope scopeType,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            String requestedPermissions,
            String justification,
            int durationMinutes,
            boolean isBreakGlass,
            int requiredQuorum
    ) {
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        this.requesterId = Objects.requireNonNull(requesterId, "requesterId must not be null");
        this.targetUserId = targetUserId != null ? targetUserId : requesterId;
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.scopeType = scopeType != null ? scopeType : PrivilegedPolicyScope.WORKSPACE;
        this.projectId = projectId;
        this.environmentId = environmentId;
        this.secretId = secretId;
        this.requestedPermissions = requestedPermissions;
        this.justification = Objects.requireNonNull(justification, "justification must not be null");
        this.durationMinutes = durationMinutes;
        this.isBreakGlass = isBreakGlass;
        this.requiredQuorum = Math.max(1, requiredQuorum);
        this.status = PrivilegedRequestStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
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

    public UUID getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(UUID requesterId) {
        this.requesterId = requesterId;
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(UUID targetUserId) {
        this.targetUserId = targetUserId;
    }

    public PrivilegedAction getAction() {
        return action;
    }

    public void setAction(PrivilegedAction action) {
        this.action = action;
    }

    public PrivilegedPolicyScope getScopeType() {
        return scopeType;
    }

    public void setScopeType(PrivilegedPolicyScope scopeType) {
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

    public UUID getSecretId() {
        return secretId;
    }

    public void setSecretId(UUID secretId) {
        this.secretId = secretId;
    }

    public String getRequestedPermissions() {
        return requestedPermissions;
    }

    public void setRequestedPermissions(String requestedPermissions) {
        this.requestedPermissions = requestedPermissions;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public PrivilegedRequestStatus getStatus() {
        return status;
    }

    public void setStatus(PrivilegedRequestStatus status) {
        this.status = status;
    }

    public boolean isBreakGlass() {
        return isBreakGlass;
    }

    public void setBreakGlass(boolean breakGlass) {
        isBreakGlass = breakGlass;
    }

    public int getRequiredQuorum() {
        return requiredQuorum;
    }

    public void setRequiredQuorum(int requiredQuorum) {
        this.requiredQuorum = requiredQuorum;
    }

    public int getCurrentApprovalsCount() {
        return currentApprovalsCount;
    }

    public void setCurrentApprovalsCount(int currentApprovalsCount) {
        this.currentApprovalsCount = currentApprovalsCount;
    }

    public String getStepUpProof() {
        return stepUpProof;
    }

    public void setStepUpProof(String stepUpProof) {
        this.stepUpProof = stepUpProof;
    }

    public StepUpFactor getStepUpFactor() {
        return stepUpFactor;
    }

    public void setStepUpFactor(StepUpFactor stepUpFactor) {
        this.stepUpFactor = stepUpFactor;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public UUID getRevokedBy() {
        return revokedBy;
    }

    public void setRevokedBy(UUID revokedBy) {
        this.revokedBy = revokedBy;
    }

    public String getRevocationReason() {
        return revocationReason;
    }

    public void setRevocationReason(String revocationReason) {
        this.revocationReason = revocationReason;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
