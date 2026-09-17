package com.secretvault.workspace.dto;

import com.secretvault.workspace.entity.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Public response representing a Workspace member and their permissions.
 */
@Schema(description = "Workspace member representation")
public record MemberResponse(
        @Schema(description = "Membership record UUID")
        UUID id,

        @Schema(description = "Member User UUID")
        UUID userId,

        @Schema(description = "Member email address")
        String email,

        @Schema(description = "Member full name")
        String fullName,

        @Schema(description = "Assigned workspace role")
        WorkspaceRole role,

        @Schema(description = "Timestamp when membership was granted")
        Instant joinedAt
) {
}
