package com.secretvault.workspace.dto;

import com.secretvault.workspace.entity.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Payload to update a workspace member's assigned RBAC role.
 */
@Schema(description = "Update member role payload")
public record UpdateMemberRoleRequest(
        @Schema(description = "New RBAC role", example = "ADMIN")
        @NotNull(message = "Role is required")
        WorkspaceRole role
) {
}
