package com.secretvault.workspace.dto;

import com.secretvault.workspace.entity.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload to add or invite a user to a Workspace with an assigned RBAC role.
 */
@Schema(description = "Add workspace member payload")
public record AddMemberRequest(
        @Schema(description = "Email of the registered user to add", example = "bob@example.com")
        @NotBlank(message = "User email is required")
        @Email(message = "Invalid email format")
        String email,

        @Schema(description = "RBAC role to grant within the workspace", example = "DEVELOPER")
        @NotNull(message = "Role is required")
        WorkspaceRole role
) {
}
