package com.secretvault.project.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload to create a new Project within a Workspace.
 */
@Schema(description = "Project creation payload")
public record CreateProjectRequest(
        @Schema(description = "Project display name", example = "Payment Gateway API")
        @NotBlank(message = "Project name is required")
        @Size(min = 2, max = 255, message = "Project name must be between 2 and 255 characters")
        String name,

        @Schema(description = "URL-friendly unique slug within workspace (lowercase, alphanumeric, hyphens)", example = "payment-gateway-api")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase alphanumeric characters and hyphens")
        @Size(min = 2, max = 255, message = "Slug must be between 2 and 255 characters")
        String slug,

        @Schema(description = "Optional project description", example = "Core microservice for handling global card payments")
        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description
) {
}
