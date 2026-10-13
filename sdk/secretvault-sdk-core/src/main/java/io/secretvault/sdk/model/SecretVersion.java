package io.secretvault.sdk.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record SecretVersion(
        UUID id,
        UUID secretId,
        int versionNumber,
        SecretStatus status,
        Instant createdAt,
        Instant activatedAt,
        Instant revokedAt,
        String reason,
        List<String> tags
) {
    public SecretVersion(
            UUID id,
            UUID secretId,
            int versionNumber,
            SecretStatus status,
            Instant createdAt,
            Instant activatedAt,
            Instant revokedAt
    ) {
        this(id, secretId, versionNumber, status, createdAt, activatedAt, revokedAt, null, Collections.emptyList());
    }
}
