package com.secretvault.cli.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectDto(
        UUID id,
        UUID workspaceId,
        String name,
        String slug,
        String description,
        String status,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        List<EnvironmentSummaryDto> environments
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EnvironmentSummaryDto(
            UUID id,
            String name,
            String slug,
            String envType,
            boolean isProtected,
            String status
    ) {}
}
