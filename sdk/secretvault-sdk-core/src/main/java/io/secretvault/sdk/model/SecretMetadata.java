package io.secretvault.sdk.model;

import java.time.Instant;
import java.util.UUID;

public record SecretMetadata(
        UUID id,
        String name,
        int currentVersion,
        SecretStatus status,
        String description,
        UUID environmentId,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt
) {
    public boolean isActive() {
        return status == SecretStatus.ACTIVE;
    }
}
