package com.secretvault.secret.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents an isolated feature or experimentation branch for a Secret.
 */
@Entity
@Table(
        name = "secret_branches",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_secret_branch_name", columnNames = {"secret_id", "name"})
        }
)
public class SecretBranch {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "secret_id", nullable = false)
    private UUID secretId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "base_version_id")
    private UUID baseVersionId;

    @Column(name = "head_version_id")
    private UUID headVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private BranchStatus status = BranchStatus.ACTIVE;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "merged_at")
    private Instant mergedAt;

    @Column(name = "merged_by")
    private UUID mergedBy;

    public SecretBranch() {
    }

    public SecretBranch(UUID secretId, String name, String description, UUID baseVersionId, UUID headVersionId, UUID createdBy) {
        this.secretId = secretId;
        this.name = name;
        this.description = description;
        this.baseVersionId = baseVersionId;
        this.headVersionId = headVersionId;
        this.createdBy = createdBy;
        this.status = BranchStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSecretId() {
        return secretId;
    }

    public void setSecretId(UUID secretId) {
        this.secretId = secretId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.updatedAt = Instant.now();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public UUID getBaseVersionId() {
        return baseVersionId;
    }

    public void setBaseVersionId(UUID baseVersionId) {
        this.baseVersionId = baseVersionId;
    }

    public UUID getHeadVersionId() {
        return headVersionId;
    }

    public void setHeadVersionId(UUID headVersionId) {
        this.headVersionId = headVersionId;
        this.updatedAt = Instant.now();
    }

    public BranchStatus getStatus() {
        return status;
    }

    public void setStatus(BranchStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getMergedAt() {
        return mergedAt;
    }

    public void setMergedAt(Instant mergedAt) {
        this.mergedAt = mergedAt;
    }

    public UUID getMergedBy() {
        return mergedBy;
    }

    public void setMergedBy(UUID mergedBy) {
        this.mergedBy = mergedBy;
    }
}
