package com.secretvault.rotation.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata-only contract for external platform synchronization after successful rotation activation.
 * Strictly zero secret plaintext.
 */
public record RotationProviderPushEvent(
        UUID workspaceId,
        UUID projectId,
        UUID environmentId,
        UUID secretId,
        String secretName,
        int newVersionNumber,
        int previousVersionNumber,
        UUID rotationJobId,
        UUID policyId,
        String triggerType,
        Instant activatedAt
) {}
