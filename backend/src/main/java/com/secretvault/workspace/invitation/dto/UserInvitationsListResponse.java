package com.secretvault.workspace.invitation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "List of pending invitations for the authenticated user")
public record UserInvitationsListResponse(
        @Schema(description = "List of pending invitations")
        List<UserInvitationItemResponse> items
) {}
