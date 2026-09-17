package com.secretvault.project.dto;

import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.entity.EnvironmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Lightweight environment summary nested within project responses.
 */
@Schema(description = "Environment summary within a project")
public record EnvironmentSummaryResponse(
        @Schema(description = "Environment UUID")
        UUID id,

        @Schema(description = "Environment display name")
        String name,

        @Schema(description = "Environment slug")
        String slug,

        @Schema(description = "Environment tier type")
        EnvType envType,

        @Schema(description = "Whether environment requires elevated approval for secret mutation")
        boolean isProtected,

        @Schema(description = "Environment lifecycle status")
        EnvironmentStatus status
) {
    public static EnvironmentSummaryResponse fromEntity(Environment environment) {
        return new EnvironmentSummaryResponse(
                environment.getId(),
                environment.getName(),
                environment.getSlug(),
                environment.getEnvType(),
                environment.isProtected(),
                environment.getStatus()
        );
    }
}
