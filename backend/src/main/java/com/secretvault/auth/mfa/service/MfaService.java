package com.secretvault.auth.mfa.service;

import com.secretvault.auth.mfa.model.MfaActivationResult;
import com.secretvault.auth.mfa.model.MfaChallengeInfo;
import com.secretvault.auth.mfa.model.MfaEnrollmentResponse;
import com.secretvault.auth.mfa.model.MfaStatusInfo;
import com.secretvault.auth.mfa.model.MfaVerificationResult;

import java.util.UUID;

/**
 * Service contract managing MFA enrollment, activation, login challenges,
 * TOTP verification, recovery-code consumption, and lifecycle management.
 */
public interface MfaService {

    /**
     * Determines whether the user has MFA actively enabled.
     * Authoritative rule: status == ENABLED.
     */
    boolean isMfaEnabled(UUID userId);

    /**
     * Returns the safe MFA status and metadata for a user.
     */
    MfaStatusInfo getStatus(UUID userId);

    /**
     * Begins MFA enrollment for an authenticated user.
     * Generates a fresh TOTP secret, encrypts it with AES-256-GCM envelope encryption,
     * stores PENDING_VERIFICATION state in PostgreSQL, and returns transient QR provisioning data.
     */
    MfaEnrollmentResponse beginEnrollment(UUID userId);

    /**
     * Activates MFA for a user by verifying their initial TOTP code against the pending secret.
     * On success, transitions state to ENABLED, generates a fresh batch of 10 recovery codes,
     * hashes and stores them in PostgreSQL, and returns plaintext recovery codes once.
     */
    MfaActivationResult activateMfa(UUID userId, String verificationCode);

    /**
     * Creates a short-lived, single-use MFA login challenge in Redis for an MFA-enabled user.
     */
    MfaChallengeInfo createLoginChallenge(UUID userId);

    /**
     * Verifies a TOTP verification code against an active login challenge.
     * On success, atomically consumes the Redis challenge and marks the MFA session verified.
     */
    MfaVerificationResult verifyLoginTotp(String challengeId, UUID userId, String code);

    /**
     * Verifies a TOTP verification code against an active login challenge by resolving the user from the challenge.
     */
    MfaVerificationResult verifyLoginTotp(String challengeId, String code);

    /**
     * Verifies a single-use backup recovery code against an active login challenge.
     * On success, atomically marks the recovery code as used in PostgreSQL,
     * atomically consumes the Redis challenge, and marks the MFA session verified.
     */
    MfaVerificationResult verifyLoginRecoveryCode(String challengeId, UUID userId, String recoveryCode);

    /**
     * Verifies a single-use backup recovery code against an active login challenge by resolving the user from the challenge.
     */
    MfaVerificationResult verifyLoginRecoveryCode(String challengeId, String recoveryCode);

    /**
     * Disables MFA for a user.
     * Purges recovery codes and transitions UserMfa status to DISABLED.
     */
    void disableMfa(UUID userId);
}
