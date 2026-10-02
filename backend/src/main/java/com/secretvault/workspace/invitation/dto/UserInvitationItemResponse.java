package com.secretvault.workspace.invitation.dto;

import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.invitation.entity.InvitationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Pending workspace invitation for the authenticated user")
public record UserInvitationItemResponse(
        @Schema(description = "Invitation UUID")
        UUID id,

        @Schema(description = "Workspace UUID")
        UUID workspaceId,

        @Schema(description = "Workspace display name")
        String workspaceName,

        @Schema(description = "User who sent the invitation")
        InviterSummary invitedBy,

        @Schema(description = "Assigned workspace role")
        WorkspaceRole role,

        @Schema(description = "Invitation status")
        InvitationStatus status,

        @Schema(description = "Expiration timestamp")
        Instant expiresAt,

        @Schema(description = "Creation timestamp")
        Instant createdAt
) {
    public record InviterSummary(
            @Schema(description = "Inviter user UUID")
            UUID id,

            @Schema(description = "Inviter display name")
            String name,

            @Schema(description = "Inviter email")
            String email
    ) {}
}
