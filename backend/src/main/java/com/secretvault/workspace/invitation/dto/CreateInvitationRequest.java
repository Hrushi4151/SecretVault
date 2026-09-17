package com.secretvault.workspace.invitation.dto;

import com.secretvault.workspace.entity.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload to invite a new user to a workspace.
 */
@Schema(description = "Workspace invitation payload")
public record CreateInvitationRequest(
        @Schema(description = "Target invitee work email", example = "engineer@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @Schema(description = "RBAC role to grant upon acceptance", example = "DEVELOPER")
        @NotNull(message = "Role is required")
        WorkspaceRole role,

        @Schema(description = "Invitation validity in days", example = "7", defaultValue = "7")
        @Min(value = 1, message = "Expiration must be at least 1 day")
        @Max(value = 30, message = "Expiration cannot exceed 30 days")
        Integer expiresInDays
) {
}
