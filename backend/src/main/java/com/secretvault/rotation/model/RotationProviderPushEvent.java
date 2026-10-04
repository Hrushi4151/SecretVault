package com.secretvault.rotation.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Event published during secret rotation to trigger external cloud provider synchronization
 * (e.g. Vercel, Render).
 *
 * <p><strong>CRITICAL SECURITY INVARIANT:</strong>
 * This event contract explicitly contains NO secret plaintext, generated password, DEK, KEK,
 * provider access token, or credential material. All secret material and credentials MUST be
 * resolved and decrypted strictly in memory at execution time with zero leakage.
 */
public record RotationProviderPushEvent(
        UUID secretId,
        int versionNumber,
        UUID providerMappingId,
        UUID rotationJobId,
        UUID workspaceId,
        Instant timestamp
) {
    public RotationProviderPushEvent {
        Objects.requireNonNull(secretId, "secretId must not be null");
        Objects.requireNonNull(providerMappingId, "providerMappingId must not be null");
        if (rotationJobId == null) {
            rotationJobId = UUID.randomUUID();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public RotationProviderPushEvent(UUID secretId, int versionNumber, UUID providerMappingId, UUID rotationJobId, UUID workspaceId) {
        this(secretId, versionNumber, providerMappingId, rotationJobId, workspaceId, Instant.now());
    }
}
