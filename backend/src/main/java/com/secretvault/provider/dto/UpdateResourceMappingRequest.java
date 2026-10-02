package com.secretvault.provider.dto;

import java.util.Map;

public record UpdateResourceMappingRequest(
        String providerResourceName,
        String providerEnvironment,
        Boolean syncEnabled,
        Map<String, Object> metadata
) {
}
