package com.secretvault.auth.mfa.model;

import com.secretvault.auth.mfa.entity.MfaStatus;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result returned upon successful MFA activation.
 * Contains the single-use backup recovery codes returned once for user backup.
 * Invariant: Recovery codes are never logged, never stored in Redis, and only hashes are persisted in DB.
 */
public record MfaActivationResult(
        MfaStatus status,
        List<String> recoveryCodes
) {

    public MfaActivationResult {
        Objects.requireNonNull(status, "status must not be null");
        recoveryCodes = (recoveryCodes != null) ? List.copyOf(recoveryCodes) : Collections.emptyList();
    }

    @Override
    public String toString() {
        return "MfaActivationResult[status=" + status +
                ", recoveryCodesCount=" + recoveryCodes.size() +
                ", recoveryCodes=[REDACTED]]";
    }
}
