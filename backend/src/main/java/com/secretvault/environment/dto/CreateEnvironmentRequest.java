package com.secretvault.environment.dto;

import com.secretvault.environment.entity.EnvType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload to create a new Environment within a Project.
 */
@Schema(description = "Environment creation payload")
public record CreateEnvironmentRequest(
        @Schema(description = "Environment display name", example = "Production US-East")
        @NotBlank(message = "Environment name is required")
        @Size(min = 2, max = 255, message = "Environment name must be between 2 and 255 characters")
        String name,

        @Schema(description = "URL-friendly unique slug within project (lowercase, alphanumeric, hyphens)", example = "prod-us-east")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase alphanumeric characters and hyphens")
        @Size(min = 2, max = 255, message = "Slug must be between 2 and 255 characters")
        String slug,

        @Schema(description = "Deployment tier type", example = "PRODUCTION")
        @NotNull(message = "Environment type is required")
        EnvType envType,

        @Schema(description = "Optional environment description", example = "Production workload in us-east-1")
        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        @Schema(description = "Whether environment is protected against unauthorized modifications", example = "true")
        Boolean isProtected
) {
}
