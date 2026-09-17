package com.secretvault.workspace.invitation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload to accept a workspace invitation via one-time token.
 */
@Schema(description = "Accept invitation payload")
public record AcceptInvitationRequest(
        @Schema(description = "One-time invitation token", example = "inv_sec_8f90a1bc...")
        @NotBlank(message = "Invitation token is required")
        String token
) {
}
