package com.secretvault.auth.mfa.model;

import java.util.Objects;

/**
 * Transient DTO returned once during MFA enrollment initiation.
 * Contains the Base32 TOTP secret and provisioning URI for authenticator QR generation.
 * Invariant: Never logged, never persisted in Redis or audit logs.
 */
public record MfaEnrollmentResponse(
        String secret,
        String provisioningUri,
        String issuer,
        String accountName
) {

    public MfaEnrollmentResponse {
        Objects.requireNonNull(secret, "secret must not be null");
        Objects.requireNonNull(provisioningUri, "provisioningUri must not be null");
        Objects.requireNonNull(issuer, "issuer must not be null");
        Objects.requireNonNull(accountName, "accountName must not be null");
    }

    @Override
    public String toString() {
        return "MfaEnrollmentResponse[issuer=" + issuer +
                ", accountName=" + accountName +
                ", secret=[REDACTED], provisioningUri=[REDACTED]]";
    }
}
