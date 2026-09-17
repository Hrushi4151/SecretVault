package com.secretvault.project.dto;

import com.secretvault.project.entity.ProjectStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Request payload to update an existing Project within a Workspace.
 */
@Schema(description = "Project update payload")
public record UpdateProjectRequest(
        @Schema(description = "Project display name", example = "Payment Gateway Service")
        @Size(min = 2, max = 255, message = "Project name must be between 2 and 255 characters")
        String name,

        @Schema(description = "Optional project description", example = "Updated description for card processing")
        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        @Schema(description = "Lifecycle status", example = "ACTIVE")
        ProjectStatus status
) {
}
