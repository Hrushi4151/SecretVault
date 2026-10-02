package com.secretvault.auth.mfa.entity;

/**
 * Lifecycle states of an account's MFA configuration.
 */
public enum MfaStatus {
    /**
     * MFA enrollment initiated, TOTP secret generated & encrypted, awaiting first verification.
     */
    PENDING_VERIFICATION,

    /**
     * MFA fully verified and actively enforced for the user account.
     */
    ENABLED,

    /**
     * MFA disabled by the user or security administrator.
     */
    DISABLED
}
