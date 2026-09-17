package com.secretvault.environment.access.dto;

import com.secretvault.environment.access.entity.PermissionLevel;
import com.secretvault.workspace.entity.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Public response DTO representing a user's scoped access grant to an environment.
 */
@Schema(description = "Environment access representation")
public record EnvironmentAccessResponse(
        @Schema(description = "EnvironmentAccess record UUID")
        UUID id,

        @Schema(description = "Environment UUID")
        UUID environmentId,

        @Schema(description = "User UUID")
        UUID userId,

        @Schema(description = "User email address")
        String email,

        @Schema(description = "User full name")
        String fullName,

        @Schema(description = "Explicit environment permission grant")
        PermissionLevel permissionLevel,

        @Schema(description = "User's parent workspace role")
        WorkspaceRole workspaceRole,

        @Schema(description = "Effective permission level (intersection of workspace role and env scope)")
        PermissionLevel effectivePermission,

        @Schema(description = "Timestamp when environment access was granted")
        Instant grantedAt
) {
}
