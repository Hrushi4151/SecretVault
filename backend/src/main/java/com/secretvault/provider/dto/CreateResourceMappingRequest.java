package com.secretvault.provider.dto;

import com.secretvault.provider.model.ProviderResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record CreateResourceMappingRequest(
        @NotNull(message = "Project ID is required")
        UUID projectId,

        @NotNull(message = "Environment ID is required")
        UUID environmentId,

        @NotNull(message = "Provider resource type is required")
        ProviderResourceType providerResourceType,

        @NotBlank(message = "Provider resource ID is required")
        String providerResourceId,

        @NotBlank(message = "Provider resource name is required")
        String providerResourceName,

        @NotBlank(message = "Provider environment is required")
        String providerEnvironment,

        Map<String, Object> metadata,

        Boolean syncEnabled
) {
}
