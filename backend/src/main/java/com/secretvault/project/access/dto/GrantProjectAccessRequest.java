package com.secretvault.project.access.dto;

import com.secretvault.workspace.entity.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request payload to grant scoped project access to a workspace member.
 */
@Schema(description = "Grant project access payload")
public record GrantProjectAccessRequest(
        @Schema(description = "Target user UUID (must already be a workspace member)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        @NotNull(message = "User ID is required")
        UUID userId,

        @Schema(description = "Project-scoped RBAC role", example = "DEVELOPER")
        @NotNull(message = "Role is required")
        WorkspaceRole role
) {
}
