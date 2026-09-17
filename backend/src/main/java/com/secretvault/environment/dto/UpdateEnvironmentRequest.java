package com.secretvault.environment.dto;

import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.EnvironmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Request payload to update an existing Environment.
 */
@Schema(description = "Environment update payload")
public record UpdateEnvironmentRequest(
        @Schema(description = "Environment display name", example = "Production Primary")
        @Size(min = 2, max = 255, message = "Environment name must be between 2 and 255 characters")
        String name,

        @Schema(description = "Optional environment description", example = "Updated primary production cluster")
        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        @Schema(description = "Deployment tier type", example = "PRODUCTION")
        EnvType envType,

        @Schema(description = "Protection status", example = "true")
        Boolean isProtected,

        @Schema(description = "Lifecycle status", example = "ACTIVE")
        EnvironmentStatus status
) {
}
