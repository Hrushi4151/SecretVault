package com.secretvault.common.cache.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Safe, non-sensitive summary DTO for Project caching.
 * Does NOT contain memberships, access roles, or secrets.
 */
public record ProjectSummaryCacheDto(
        UUID id,
        UUID workspaceId,
        String name,
        String slug,
        String description,
        Instant createdAt
) implements Serializable {
}
