package io.secretvault.sdk.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a short-lived runtime secret lease controlling client-side freshness.
 */
public record SecretLease(
        UUID leaseId,
        String secretName,
        int version,
        Instant issuedAt,
        Instant expiresAt,
        boolean renewable
) {
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
