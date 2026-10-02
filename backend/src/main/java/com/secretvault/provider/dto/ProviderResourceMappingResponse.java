package com.secretvault.provider.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.ProviderResourceType;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public record ProviderResourceMappingResponse(
        UUID id,
        UUID workspaceId,
        UUID integrationId,
        UUID projectId,
        UUID environmentId,
        ProviderResourceType providerResourceType,
        String providerResourceId,
        String providerResourceName,
        String providerEnvironment,
        Map<String, Object> metadata,
        boolean syncEnabled,
        Instant createdAt,
        Instant updatedAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ProviderResourceMappingResponse fromEntity(ProviderResourceMapping entity) {
        Map<String, Object> meta = Collections.emptyMap();
        if (entity.getMetadataJson() != null && !entity.getMetadataJson().isBlank()) {
            try {
                meta = MAPPER.readValue(entity.getMetadataJson(), Map.class);
            } catch (Exception ignored) {
            }
        }

        return new ProviderResourceMappingResponse(
                entity.getId(),
                entity.getWorkspaceId(),
                entity.getIntegrationId(),
                entity.getProjectId(),
                entity.getEnvironmentId(),
                entity.getProviderResourceType(),
                entity.getProviderResourceId(),
                entity.getProviderResourceName(),
                entity.getProviderEnvironment(),
                meta,
                entity.isSyncEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
