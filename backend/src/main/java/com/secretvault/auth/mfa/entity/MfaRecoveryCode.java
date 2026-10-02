package com.secretvault.auth.mfa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative entity storing a hashed single-use backup recovery code for MFA.
 * Plaintext recovery codes are never stored in the database.
 */
@Entity
@Table(name = "mfa_recovery_codes", uniqueConstraints = {
        @UniqueConstraint(name = "uq_mfa_recovery_code_index", columnNames = {"user_mfa_id", "code_index"})
})
public class MfaRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_mfa_id", nullable = false)
    private UUID userMfaId;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "code_index", nullable = false)
    private int codeIndex;

    @Column(name = "used", nullable = false)
    private boolean used = false;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public MfaRecoveryCode() {
    }

    public MfaRecoveryCode(UUID userMfaId, String codeHash, int codeIndex) {
        this.userMfaId = Objects.requireNonNull(userMfaId, "userMfaId must not be null");
        this.codeHash = Objects.requireNonNull(codeHash, "codeHash must not be null");
        if (codeIndex < 0) {
            throw new IllegalArgumentException("codeIndex must be non-negative");
        }
        this.codeIndex = codeIndex;
        this.used = false;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void markUsed(Instant timestamp) {
        this.used = true;
        this.usedAt = (timestamp != null) ? timestamp : Instant.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserMfaId() {
        return userMfaId;
    }

    public void setUserMfaId(UUID userMfaId) {
        this.userMfaId = userMfaId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public int getCodeIndex() {
        return codeIndex;
    }

    public void setCodeIndex(int codeIndex) {
        if (codeIndex < 0) {
            throw new IllegalArgumentException("codeIndex must be non-negative");
        }
        this.codeIndex = codeIndex;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
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
        MfaRecoveryCode that = (MfaRecoveryCode) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        // Sanitized toString that never leaks the recovery code hash
        return "MfaRecoveryCode[id=" + id +
                ", userMfaId=" + userMfaId +
                ", codeIndex=" + codeIndex +
                ", used=" + used +
                ", usedAt=" + usedAt + "]";
    }
}
