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
 * Immutable historical version entity storing encrypted secret payload.
 * Plaintext values are never stored.
 */
@Entity
@Table(
        name = "secret_versions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_secret_version", columnNames = {"secret_id", "version_number"})
        }
)
public class SecretVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "secret_id", nullable = false)
    private UUID secretId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "version_type", nullable = false, length = 32)
    private VersionType versionType = VersionType.VALUE_UPDATE;

    @Column(name = "ciphertext", nullable = false)
    private byte[] ciphertext;

    @Column(name = "encrypted_dek", nullable = false)
    private byte[] encryptedDek;

    @Column(name = "iv", nullable = false)
    private byte[] iv;

    @Column(name = "auth_tag", nullable = false)
    private byte[] authTag;

    @Column(name = "key_reference", nullable = false)
    private String keyReference;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "reason")
    private String reason;

    @Column(name = "source_version_id")
    private UUID sourceVersionId;

    @Column(name = "source_secret_id")
    private UUID sourceSecretId;

    @Column(name = "source_environment_id")
    private UUID sourceEnvironmentId;

    @Column(name = "branch_id")
    private UUID branchId;

    public SecretVersion() {
    }

    public SecretVersion(
            UUID secretId,
            Integer versionNumber,
            byte[] ciphertext,
            byte[] encryptedDek,
            byte[] iv,
            byte[] authTag,
            String keyReference,
            UUID createdBy,
            String reason
    ) {
        this(secretId, versionNumber, VersionType.VALUE_UPDATE, ciphertext, encryptedDek, iv, authTag, keyReference, createdBy, reason, null, null, null, null);
    }

    public SecretVersion(
            UUID secretId,
            Integer versionNumber,
            VersionType versionType,
            byte[] ciphertext,
            byte[] encryptedDek,
            byte[] iv,
            byte[] authTag,
            String keyReference,
            UUID createdBy,
            String reason
    ) {
        this(secretId, versionNumber, versionType, ciphertext, encryptedDek, iv, authTag, keyReference, createdBy, reason, null, null, null, null);
    }

    public SecretVersion(
            UUID secretId,
            Integer versionNumber,
            VersionType versionType,
            byte[] ciphertext,
            byte[] encryptedDek,
            byte[] iv,
            byte[] authTag,
            String keyReference,
            UUID createdBy,
            String reason,
            UUID sourceVersionId,
            UUID sourceSecretId,
            UUID sourceEnvironmentId,
            UUID branchId
    ) {
        this.secretId = secretId;
        this.versionNumber = versionNumber;
        this.versionType = versionType != null ? versionType : VersionType.VALUE_UPDATE;
        this.ciphertext = ciphertext;
        this.encryptedDek = encryptedDek;
        this.iv = iv;
        this.authTag = authTag;
        this.keyReference = keyReference;
        this.createdBy = createdBy;
        this.reason = reason;
        this.sourceVersionId = sourceVersionId;
        this.sourceSecretId = sourceSecretId;
        this.sourceEnvironmentId = sourceEnvironmentId;
        this.branchId = branchId;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSecretId() {
        return secretId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public VersionType getVersionType() {
        return versionType;
    }

    public byte[] getCiphertext() {
        return ciphertext;
    }

    public byte[] getEncryptedDek() {
        return encryptedDek;
    }

    public byte[] getIv() {
        return iv;
    }

    public byte[] getAuthTag() {
        return authTag;
    }

    public String getKeyReference() {
        return keyReference;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getReason() {
        return reason;
    }

    public UUID getSourceVersionId() {
        return sourceVersionId;
    }

    public UUID getSourceSecretId() {
        return sourceSecretId;
    }

    public UUID getSourceEnvironmentId() {
        return sourceEnvironmentId;
    }

    public UUID getBranchId() {
        return branchId;
    }
}
