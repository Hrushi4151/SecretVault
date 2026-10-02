package com.secretvault.auth.mfa.entity;

import com.secretvault.encryption.model.EncryptedPayload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative entity storing the envelope-encrypted TOTP configuration for a user.
 * Plaintext TOTP secrets are never persisted; all material is encrypted via AES-256-GCM.
 */
@Entity
@Table(name = "user_mfa")
public class UserMfa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "ciphertext", nullable = false)
    private byte[] ciphertext;

    @Column(name = "encrypted_dek", nullable = false)
    private byte[] encryptedDek;

    @Column(name = "iv", nullable = false)
    private byte[] iv;

    @Column(name = "auth_tag", nullable = false)
    private byte[] authTag;

    @Column(name = "key_reference", nullable = false, length = 255)
    private String keyReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MfaStatus status = MfaStatus.PENDING_VERIFICATION;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserMfa() {
    }

    public UserMfa(UUID userId, EncryptedPayload payload) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        updateEncryptedPayload(payload);
        this.status = MfaStatus.PENDING_VERIFICATION;
        this.enrolledAt = Instant.now();
        this.failedAttempts = 0;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.enrolledAt == null) {
            this.enrolledAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Reconstitutes the stored envelope into an EncryptedPayload for in-memory decryption.
     */
    public EncryptedPayload toEncryptedPayload() {
        return new EncryptedPayload(ciphertext, encryptedDek, iv, authTag, keyReference);
    }

    /**
     * Updates the cryptographic envelope fields from a fresh EncryptedPayload.
     */
    public void updateEncryptedPayload(EncryptedPayload payload) {
        Objects.requireNonNull(payload, "EncryptedPayload must not be null");
        this.ciphertext = payload.ciphertext();
        this.encryptedDek = payload.encryptedDek();
        this.iv = payload.iv();
        this.authTag = payload.authTag();
        this.keyReference = payload.keyReference();
    }

    public void enable() {
        this.status = MfaStatus.ENABLED;
        this.verifiedAt = Instant.now();
        this.failedAttempts = 0;
    }

    public void disable() {
        this.status = MfaStatus.DISABLED;
    }

    public void incrementFailedAttempts() {
        this.failedAttempts++;
    }

    public void resetFailedAttempts() {
        this.failedAttempts = 0;
    }

    public void recordSuccessfulUse(Instant timestamp) {
        this.lastUsedAt = (timestamp != null) ? timestamp : Instant.now();
        this.failedAttempts = 0;
    }

    public boolean isEnabled() {
        return this.status == MfaStatus.ENABLED;
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
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

    public MfaStatus getStatus() {
        return status;
    }

    public void setStatus(MfaStatus status) {
        this.status = Objects.requireNonNull(status, "Status must not be null");
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(Instant enrolledAt) {
        this.enrolledAt = enrolledAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        if (failedAttempts < 0) {
            throw new IllegalArgumentException("failedAttempts cannot be negative");
        }
        this.failedAttempts = failedAttempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserMfa userMfa = (UserMfa) o;
        return Objects.equals(id, userMfa.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        // Sanitized toString that never leaks ciphertext or cryptographic keys
        return "UserMfa[id=" + id +
                ", userId=" + userId +
                ", status=" + status +
                ", keyReference=" + keyReference +
                ", enrolledAt=" + enrolledAt +
                ", verifiedAt=" + verifiedAt +
                ", lastUsedAt=" + lastUsedAt +
                ", failedAttempts=" + failedAttempts + "]";
    }
}
