package com.secretvault.provider.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.provider.entity.ProviderIntegration;
import com.secretvault.provider.model.IntegrationStatus;
import com.secretvault.provider.model.ProviderType;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public record ProviderIntegrationResponse(
        UUID id,
        UUID workspaceId,
        ProviderType providerType,
        String displayName,
        IntegrationStatus status,
        Map<String, Object> configuration,
        String redactedCredentialHint,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant lastValidatedAt,
        Instant lastErrorAt,
        String lastErrorCode,
        String lastErrorMessage
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ProviderIntegrationResponse fromEntity(ProviderIntegration entity) {
        Map<String, Object> config = Collections.emptyMap();
        if (entity.getConfigurationJson() != null && !entity.getConfigurationJson().isBlank()) {
            try {
                config = MAPPER.readValue(entity.getConfigurationJson(), Map.class);
            } catch (Exception ignored) {
            }
        }

        return new ProviderIntegrationResponse(
                entity.getId(),
                entity.getWorkspaceId(),
                entity.getProviderType(),
                entity.getDisplayName(),
                entity.getStatus(),
                config,
                entity.getRedactedCredentialHint(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getLastValidatedAt(),
                entity.getLastErrorAt(),
                entity.getLastErrorCode(),
                entity.getLastErrorMessage()
        );
    }
}
