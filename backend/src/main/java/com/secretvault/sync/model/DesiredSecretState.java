package com.secretvault.sync.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable representation of a secret's desired state in SecretVault.
 * Never stores or exposes plaintext secret values.
 */
public record DesiredSecretState(
        UUID workspaceId,
        UUID projectId,
        UUID environmentId,
        UUID secretId,
        String secretName,
        int versionNumber,
        String desiredFingerprint,
        boolean enabled,
        UUID mappingId,
        Instant lastChangedAt
) {}
