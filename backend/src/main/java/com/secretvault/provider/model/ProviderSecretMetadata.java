package com.secretvault.provider.model;

import java.time.Instant;

/**
 * Metadata representation of a secret or environment variable on an external provider (zero plaintext).
 */
public record ProviderSecretMetadata(
        String key,
        String targetEnvironment,
        Instant updatedAt,
        String providerSecretId
) {
}
