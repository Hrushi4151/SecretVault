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
        this.secretId = secretId;
        this.versionNumber = versionNumber;
        this.ciphertext = ciphertext;
        this.encryptedDek = encryptedDek;
        this.iv = iv;
        this.authTag = authTag;
        this.keyReference = keyReference;
        this.createdBy = createdBy;
        this.reason = reason;
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
}
