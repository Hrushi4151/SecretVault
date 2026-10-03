package io.secretvault.sdk.model;

import java.time.Instant;
import java.util.UUID;

public record SecretVersion(
        UUID id,
        UUID secretId,
        int versionNumber,
        SecretStatus status,
        Instant createdAt,
        Instant activatedAt,
        Instant revokedAt
) {
}
