package com.secretvault.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "otp_hash", nullable = false, length = 255)
    private String otpHash;

    @Column(name = "reset_token", nullable = false, unique = true, length = 255)
    private String resetToken;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "otp_verified", nullable = false)
    private boolean otpVerified = false;

    @Column(name = "mfa_required", nullable = false)
    private boolean mfaRequired = false;

    @Column(name = "mfa_verified", nullable = false)
    private boolean mfaVerified = false;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public PasswordResetToken() {}

    public PasswordResetToken(UUID userId, String email, String otpHash, String resetToken, boolean mfaRequired, Instant expiresAt) {
        this.userId = userId;
        this.email = email;
        this.otpHash = otpHash;
        this.resetToken = resetToken;
        this.mfaRequired = mfaRequired;
        this.mfaVerified = !mfaRequired; // If MFA not required, default mfaVerified to true once OTP is verified
        this.otpVerified = false;
        this.attempts = 0;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getOtpHash() { return otpHash; }
    public void setOtpHash(String otpHash) { this.otpHash = otpHash; }

    public String getResetToken() { return resetToken; }
    public void setResetToken(String resetToken) { this.resetToken = resetToken; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public boolean isOtpVerified() { return otpVerified; }
    public void setOtpVerified(boolean otpVerified) { this.otpVerified = otpVerified; }

    public boolean isMfaRequired() { return mfaRequired; }
    public void setMfaRequired(boolean mfaRequired) { this.mfaRequired = mfaRequired; }

    public boolean isMfaVerified() { return mfaVerified; }
    public void setMfaVerified(boolean mfaVerified) { this.mfaVerified = mfaVerified; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
