package com.secretvault.provider.model;

import java.util.Map;

/**
 * Discovered project, service, or repository on an external platform provider.
 */
public record ProviderDiscoveredResource(
        String providerResourceId,
        String name,
        ProviderResourceType type,
        String status,
        String region,
        Map<String, Object> metadata
) {
    public ProviderDiscoveredResource {
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
