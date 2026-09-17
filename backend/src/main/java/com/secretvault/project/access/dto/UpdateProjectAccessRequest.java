package com.secretvault.project.access.dto;

import com.secretvault.workspace.entity.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload to update scoped project access role for an existing project member.
 */
@Schema(description = "Update project access payload")
public record UpdateProjectAccessRequest(
        @Schema(description = "Updated project-scoped role", example = "DEVELOPER")
        @NotNull(message = "Role is required")
        WorkspaceRole role
) {
}
