package com.secretvault.cli.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkspaceDto(
        UUID id,
        UUID organizationId,
        String name,
        String slug,
        String role,
        boolean isDefault,
        Instant createdAt
) {}
