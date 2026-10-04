package com.secretvault.rotation.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Metadata-only event published during secret rotation to trigger external cloud provider synchronization
 * (e.g. Vercel, Render).
 *
 * <p><strong>CRITICAL SECURITY INVARIANT:</strong>
 * This event contract explicitly contains NO secret plaintext, generated password, DEK, KEK,
 * provider access token, or credential material. All secret material and credentials MUST be
 * resolved and decrypted strictly in memory at execution time with zero leakage.
 */
public record RotationProviderPushEvent(
        UUID secretId,
        String secretName,
        int versionNumber,
        int previousVersionNumber,
        UUID providerMappingId,
        UUID rotationJobId,
        UUID workspaceId,
        UUID projectId,
        UUID environmentId,
        UUID policyId,
        String triggerType,
        Instant activatedAt,
        Instant timestamp
) {
    public RotationProviderPushEvent {
        Objects.requireNonNull(secretId, "secretId must not be null");
        if (rotationJobId == null) {
            rotationJobId = UUID.randomUUID();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (activatedAt == null) {
            activatedAt = timestamp;
        }
    }

    /**
     * Provider-mapping specific constructor for direct provider push notifications.
     */
    public RotationProviderPushEvent(UUID secretId, int versionNumber, UUID providerMappingId, UUID rotationJobId, UUID workspaceId) {
        this(secretId, null, versionNumber, 0, providerMappingId, rotationJobId, workspaceId, null, null, null, null, Instant.now(), Instant.now());
    }

    /**
     * Environment-level domain event constructor for rotation lifecycle and outbox integration.
     */
    public RotationProviderPushEvent(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            String secretName,
            int versionNumber,
            int previousVersionNumber,
            UUID rotationJobId,
            UUID policyId,
            String triggerType,
            Instant activatedAt
    ) {
        this(secretId, secretName, versionNumber, previousVersionNumber, null, rotationJobId, workspaceId, projectId, environmentId, policyId, triggerType, activatedAt, Instant.now());
    }
}
