package com.secretvault.common.cache.dto;

import com.secretvault.environment.entity.EnvType;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Safe, non-sensitive summary DTO for Environment caching.
 * Does NOT contain secrets or effective permissions.
 */
public record EnvironmentSummaryCacheDto(
        UUID id,
        UUID projectId,
        String name,
        String slug,
        EnvType envType,
        String description,
        boolean isProtected,
        Instant createdAt
) implements Serializable {
}
