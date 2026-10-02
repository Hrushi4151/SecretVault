package com.secretvault.sync.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Normalized representation of actual secret state observed on an external platform provider.
 * Plaintext values are never stored or logged.
 */
public record ProviderSecretState(
        UUID integrationId,
        UUID mappingId,
        String providerResourceId,
        String providerEnvironment,
        String providerSecretName,
        String providerSecretIdentifier,
        String providerFingerprint,
        boolean exists,
        String metadata,
        Instant observedAt
) {}
