package com.secretvault.provider.dto;

import com.secretvault.provider.model.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateProviderIntegrationRequest(
        @NotNull(message = "Provider type is required")
        ProviderType providerType,

        @NotBlank(message = "Display name is required")
        @Size(min = 2, max = 128, message = "Display name must be between 2 and 128 characters")
        String displayName,

        @NotBlank(message = "Provider credential / API token is required")
        String credential,

        Map<String, Object> configuration
) {
}
