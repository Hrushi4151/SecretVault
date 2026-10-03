package com.secretvault.rotation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps secret dependencies consumed by registered application/consumer workloads.
 * Enables zero-downtime rotation impact analysis and consumer notification.
 */
@Entity
@Table(
        name = "secret_dependencies",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_consumer_secret_dep", columnNames = {"consumer_id", "secret_id"})
        }
)
public class SecretDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "consumer_id", nullable = false)
    private UUID consumerId;

    @Column(name = "secret_id", nullable = false)
    private UUID secretId;

    @Column(name = "alias_name", length = 128)
    private String aliasName;

    @Column(name = "is_required", nullable = false)
    private boolean isRequired = true;

    @Column(name = "last_consumed_version")
    private Integer lastConsumedVersion;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public SecretDependency() {
    }

    public SecretDependency(UUID workspaceId, UUID consumerId, UUID secretId, String aliasName, boolean isRequired) {
        this.workspaceId = workspaceId;
        this.consumerId = consumerId;
        this.secretId = secretId;
        this.aliasName = aliasName;
        this.isRequired = isRequired;
        this.lastAccessedAt = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getConsumerId() {
        return consumerId;
    }

    public UUID getSecretId() {
        return secretId;
    }

    public String getAliasName() {
        return aliasName;
    }

    public void setAliasName(String aliasName) {
        this.aliasName = aliasName;
    }

    public boolean isRequired() {
        return isRequired;
    }

    public void setRequired(boolean required) {
        isRequired = required;
    }

    public Integer getLastConsumedVersion() {
        return lastConsumedVersion;
    }

    public void setLastConsumedVersion(Integer lastConsumedVersion) {
        this.lastConsumedVersion = lastConsumedVersion;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(Instant lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
