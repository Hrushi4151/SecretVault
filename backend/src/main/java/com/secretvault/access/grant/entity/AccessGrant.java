package com.secretvault.access.grant.entity;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Granular resource-level access grant entity.
 * Represents an explicit permission granted to a specific user on a defined scope.
 */
@Entity
@Table(name = "access_grants", uniqueConstraints = {
        @UniqueConstraint(name = "uq_access_grant", columnNames = {
                "workspace_id", "user_id", "scope_type", "project_id", "environment_id", "secret_id", "permission"
        })
})
public class AccessGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 32)
    private AccessScope scopeType;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "environment_id")
    private UUID environmentId;

    @Column(name = "secret_id")
    private UUID secretId;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 64)
    private AccessPermission permission;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public AccessGrant() {
    }

    public AccessGrant(
            UUID workspaceId,
            UUID userId,
            AccessScope scopeType,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            AccessPermission permission,
            UUID grantedBy
    ) {
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.scopeType = Objects.requireNonNull(scopeType, "scopeType must not be null");
        this.projectId = projectId;
        this.environmentId = environmentId;
        this.secretId = secretId;
        this.permission = Objects.requireNonNull(permission, "permission must not be null");
        this.grantedBy = grantedBy;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
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

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
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

    public UUID getSecretId() {
        return secretId;
    }

    public void setSecretId(UUID secretId) {
        this.secretId = secretId;
    }

    public AccessPermission getPermission() {
        return permission;
    }

    public void setPermission(AccessPermission permission) {
        this.permission = permission;
    }

    public UUID getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(UUID grantedBy) {
        this.grantedBy = grantedBy;
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
