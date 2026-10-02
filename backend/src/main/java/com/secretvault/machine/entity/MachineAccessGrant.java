package com.secretvault.machine.entity;

import com.secretvault.access.model.AccessScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "machine_access_grants")
public class MachineAccessGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "machine_identity_id", nullable = false)
    private UUID machineIdentityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 32)
    private AccessScope scopeType;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "environment_id")
    private UUID environmentId;

    @Column(name = "secret_id")
    private UUID secretId;

    @Column(name = "secret_pattern", length = 256)
    private String secretPattern;

    @Column(name = "permission", nullable = false, length = 64)
    private String permission;

    @Column(name = "effect", nullable = false, length = 16)
    private String effect = "ALLOW"; // 'ALLOW', 'DENY'

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public MachineAccessGrant() {
    }

    public MachineAccessGrant(UUID workspaceId, UUID machineIdentityId, AccessScope scopeType,
                              UUID projectId, UUID environmentId, UUID secretId,
                              String secretPattern, String permission, String effect, UUID grantedBy) {
        this.workspaceId = workspaceId;
        this.machineIdentityId = machineIdentityId;
        this.scopeType = scopeType;
        this.projectId = projectId;
        this.environmentId = environmentId;
        this.secretId = secretId;
        this.secretPattern = secretPattern;
        this.permission = permission;
        this.effect = effect != null ? effect : "ALLOW";
        this.grantedBy = grantedBy;
        this.createdAt = Instant.now();
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

    public UUID getMachineIdentityId() {
        return machineIdentityId;
    }

    public void setMachineIdentityId(UUID machineIdentityId) {
        this.machineIdentityId = machineIdentityId;
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

    public String getSecretPattern() {
        return secretPattern;
    }

    public void setSecretPattern(String secretPattern) {
        this.secretPattern = secretPattern;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public String getEffect() {
        return effect;
    }

    public void setEffect(String effect) {
        this.effect = effect;
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
