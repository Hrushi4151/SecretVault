package com.secretvault.workspace.invitation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "User lookup response for team invitations")
public record UserLookupResponse(
        @Schema(description = "Whether the user exists in SecretVault")
        boolean exists,

        @Schema(description = "User summary details if user exists")
        UserSummary user,

        @Schema(description = "Whether user is already an active member of this workspace")
        boolean isMember,

        @Schema(description = "Whether a pending invitation already exists for this email in this workspace")
        boolean hasPendingInvitation
) {
    public record UserSummary(
            @Schema(description = "User UUID")
            UUID id,

            @Schema(description = "User display name")
            String name,

            @Schema(description = "User email")
            String email
    ) {}

    public static UserLookupResponse found(UUID id, String name, String email, boolean isMember, boolean hasPendingInvitation) {
        return new UserLookupResponse(true, new UserSummary(id, name, email), isMember, hasPendingInvitation);
    }

    public static UserLookupResponse notFound(boolean isMember, boolean hasPendingInvitation) {
        return new UserLookupResponse(false, null, isMember, hasPendingInvitation);
    }
}
