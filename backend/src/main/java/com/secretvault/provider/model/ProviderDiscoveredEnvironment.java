package com.secretvault.provider.model;

import java.util.Map;

/**
 * Discovered environment tier or deployment target on an external platform provider.
 */
public record ProviderDiscoveredEnvironment(
        String providerEnvironmentId,
        String name,
        String target,
        Map<String, Object> metadata
) {
    public ProviderDiscoveredEnvironment {
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
