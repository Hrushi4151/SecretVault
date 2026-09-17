package com.secretvault.environment.dto;

import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.entity.EnvironmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Public response representing an Environment.
 */
@Schema(description = "Environment representation")
public record EnvironmentResponse(
        @Schema(description = "Environment UUID")
        UUID id,

        @Schema(description = "Parent Project UUID")
        UUID projectId,

        @Schema(description = "Environment display name")
        String name,

        @Schema(description = "URL-friendly environment slug")
        String slug,

        @Schema(description = "Deployment tier type")
        EnvType envType,

        @Schema(description = "Environment description")
        String description,

        @Schema(description = "Whether environment is protected")
        boolean isProtected,

        @Schema(description = "Environment lifecycle status")
        EnvironmentStatus status,

        @Schema(description = "Creator User UUID")
        UUID createdBy,

        @Schema(description = "Environment creation timestamp")
        Instant createdAt,

        @Schema(description = "Environment last update timestamp")
        Instant updatedAt
) {
    public static EnvironmentResponse fromEntity(Environment environment) {
        return new EnvironmentResponse(
                environment.getId(),
                environment.getProjectId(),
                environment.getName(),
                environment.getSlug(),
                environment.getEnvType(),
                environment.getDescription(),
                environment.isProtected(),
                environment.getStatus(),
                environment.getCreatedBy(),
                environment.getCreatedAt(),
                environment.getUpdatedAt()
        );
    }
}
