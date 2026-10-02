package com.secretvault.provider.dto;

import com.secretvault.provider.model.IntegrationStatus;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record UpdateProviderIntegrationRequest(
        @Size(min = 2, max = 128, message = "Display name must be between 2 and 128 characters")
        String displayName,

        IntegrationStatus status,

        String newCredential,

        Map<String, Object> configuration
) {
}
