package com.secretvault.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload to create a new Workspace within the caller's organization.
 */
@Schema(description = "Workspace creation payload")
public record CreateWorkspaceRequest(
        @Schema(description = "Workspace display name", example = "Core Infrastructure")
        @NotBlank(message = "Workspace name is required")
        @Size(min = 2, max = 255, message = "Workspace name must be between 2 and 255 characters")
        String name,

        @Schema(description = "URL-friendly unique slug within organization (lowercase, alphanumeric, hyphens)", example = "core-infra")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase alphanumeric characters and hyphens")
        @Size(min = 2, max = 255, message = "Slug must be between 2 and 255 characters")
        String slug
) {
}
