package com.secretvault.auth.mfa.model;

import java.util.UUID;

/**
 * Result of an MFA verification attempt (TOTP or recovery code).
 */
public record MfaVerificationResult(
        AuthenticationState state,
        UUID userId,
        boolean success,
        String errorMessage
) {

    public static MfaVerificationResult success(UUID userId) {
        return new MfaVerificationResult(AuthenticationState.MFA_VERIFIED, userId, true, null);
    }

    public static MfaVerificationResult failed(AuthenticationState state, String errorMessage) {
        return new MfaVerificationResult(state, null, false, errorMessage);
    }
}
