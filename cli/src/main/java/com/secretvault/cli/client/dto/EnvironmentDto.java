package com.secretvault.cli.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EnvironmentDto(
        UUID id,
        UUID projectId,
        String name,
        String slug,
        String envType,
        String description,
        boolean isProtected,
        String status,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {}
