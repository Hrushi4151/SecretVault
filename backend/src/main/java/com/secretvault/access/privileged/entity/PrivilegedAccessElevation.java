package com.secretvault.access.privileged.entity;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.privileged.model.ElevationStatus;
import com.secretvault.access.privileged.model.PrivilegedAction;
import com.secretvault.access.privileged.model.PrivilegedPolicyScope;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Privileged Access Elevation Entity.
 * Represents an active, time-bounded elevation capability evaluated by {@link com.secretvault.access.service.EffectiveAccessService}.
 */
@Entity
@Table(name = "privileged_access_elevations")
public class PrivilegedAccessElevation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id = UUID.randomUUID();

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "granted_permission", length = 64)
    private AccessPermission grantedPermission;

    @Column(name = "is_break_glass", nullable = false)
    private boolean isBreakGlass = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ElevationStatus status = ElevationStatus.ACTIVE;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "revocation_reason", columnDefinition = "TEXT")
    private String revocationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public PrivilegedAccessElevation() {
    }

    public PrivilegedAccessElevation(
            UUID workspaceId,
            UUID requestId,
            UUID userId,
            PrivilegedAction action,
            PrivilegedPolicyScope scopeType,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            AccessPermission grantedPermission,
            boolean isBreakGlass,
            Instant startsAt,
            Instant expiresAt
    ) {
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        this.requestId = requestId;
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.scopeType = scopeType != null ? scopeType : PrivilegedPolicyScope.WORKSPACE;
        this.projectId = projectId;
        this.environmentId = environmentId;
        this.secretId = secretId;
        this.grantedPermission = grantedPermission;
        this.isBreakGlass = isBreakGlass;
        this.status = ElevationStatus.ACTIVE;
        this.startsAt = startsAt != null ? startsAt : Instant.now();
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.createdAt = Instant.now();
    }

    public boolean isActive(Instant now) {
        return status == ElevationStatus.ACTIVE &&
                revokedAt == null &&
                now.isAfter(startsAt.minusSeconds(1)) &&
                now.isBefore(expiresAt);
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

    public UUID getRequestId() {
        return requestId;
    }

    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
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

    public AccessPermission getGrantedPermission() {
        return grantedPermission;
    }

    public void setGrantedPermission(AccessPermission grantedPermission) {
        this.grantedPermission = grantedPermission;
    }

    public boolean isBreakGlass() {
        return isBreakGlass;
    }

    public void setBreakGlass(boolean breakGlass) {
        isBreakGlass = breakGlass;
    }

    public ElevationStatus getStatus() {
        return status;
    }

    public void setStatus(ElevationStatus status) {
        this.status = status;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
