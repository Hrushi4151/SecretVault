package com.secretvault.workspace.invitation.dto;

import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.invitation.entity.InvitationStatus;
import com.secretvault.workspace.invitation.entity.WorkspaceInvitation;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Public response representing a workspace invitation.
 */
@Schema(description = "Workspace invitation representation")
public record InvitationResponse(
        @Schema(description = "Invitation UUID")
        UUID id,

        @Schema(description = "Workspace UUID")
        UUID workspaceId,

        @Schema(description = "Invitee email")
        String email,

        @Schema(description = "Invited by User UUID")
        UUID invitedBy,

        @Schema(description = "Assigned workspace role")
        WorkspaceRole role,

        @Schema(description = "Invitation status")
        InvitationStatus status,

        @Schema(description = "Invitation expiration timestamp")
        Instant expiresAt,

        @Schema(description = "Invitation creation timestamp")
        Instant createdAt,

        @Schema(description = "One-time plaintext acceptance token (only returned upon invitation creation)", example = "inv_sec_89dfa...")
        String rawToken
) {
    public static InvitationResponse fromEntity(WorkspaceInvitation invitation, String rawToken) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getWorkspaceId(),
                invitation.getEmail(),
                invitation.getInvitedBy(),
                invitation.getRole(),
                invitation.getStatus(),
                invitation.getExpiresAt(),
                invitation.getCreatedAt(),
                rawToken
        );
    }
}
