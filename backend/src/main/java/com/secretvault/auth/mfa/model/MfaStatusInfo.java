package com.secretvault.auth.mfa.model;

import com.secretvault.auth.mfa.entity.MfaStatus;

import java.time.Instant;

/**
 * Non-sensitive metadata describing a user's MFA enrollment status.
 */
public record MfaStatusInfo(
        boolean enabled,
        MfaStatus status,
        Instant enrolledAt,
        Instant verifiedAt,
        Instant lastUsedAt,
        long remainingRecoveryCodes
) {
    public static MfaStatusInfo disabled() {
        return new MfaStatusInfo(false, MfaStatus.DISABLED, null, null, null, 0);
    }
}
