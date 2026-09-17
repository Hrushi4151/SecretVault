package com.secretvault.project.access.dto;

import com.secretvault.workspace.entity.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Public DTO representing a member with scoped access to a project.
 */
@Schema(description = "Project member access representation")
public record ProjectMemberResponse(
        @Schema(description = "ProjectAccess record UUID")
        UUID id,

        @Schema(description = "Project UUID")
        UUID projectId,

        @Schema(description = "User UUID")
        UUID userId,

        @Schema(description = "User email address")
        String email,

        @Schema(description = "User full name")
        String fullName,

        @Schema(description = "Project-scoped role grant")
        WorkspaceRole projectRole,

        @Schema(description = "User's parent workspace role")
        WorkspaceRole workspaceRole,

        @Schema(description = "Effective role evaluated at project level")
        WorkspaceRole effectiveRole,

        @Schema(description = "Timestamp when project access was granted")
        Instant grantedAt
) {
}
