package com.secretvault.secret.entity;

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
 * Metadata tag attached to an immutable SecretVersion (e.g. 'production', 'stable', 'release-2026-09').
 */
@Entity
@Table(
        name = "secret_version_tags",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_version_tag", columnNames = {"secret_version_id", "name"})
        }
)
public class SecretVersionTag {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "secret_version_id", nullable = false)
    private UUID secretVersionId;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public SecretVersionTag() {
    }

    public SecretVersionTag(UUID secretVersionId, String name, UUID createdBy) {
        this.secretVersionId = secretVersionId;
        this.name = name;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSecretVersionId() {
        return secretVersionId;
    }

    public void setSecretVersionId(UUID secretVersionId) {
        this.secretVersionId = secretVersionId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
}
