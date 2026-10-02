package com.secretvault.common.cache.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Safe, non-sensitive summary DTO for Workspace caching.
 * Does NOT contain memberships, roles, or authorization decisions.
 */
public record WorkspaceSummaryCacheDto(
        UUID id,
        String name,
        String slug,
        String description,
        UUID organizationId,
        Instant createdAt
) implements Serializable {
}
