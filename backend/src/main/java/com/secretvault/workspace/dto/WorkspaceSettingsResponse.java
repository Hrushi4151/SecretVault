package com.secretvault.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Public response representing workspace configuration settings and security policies.
 */
@Schema(description = "Workspace configuration settings")
public record WorkspaceSettingsResponse(
        @Schema(description = "Workspace UUID")
        UUID id,

        @Schema(description = "Workspace display name")
        String name,

        @Schema(description = "Workspace URL slug")
        String slug,

        @Schema(description = "Policy governing who can create projects (ALL_MEMBERS | ADMIN_ONLY)", example = "ALL_MEMBERS")
        String projectCreationPolicy,

        @Schema(description = "Policy governing who can create custom environments (ADMIN_ONLY)", example = "ADMIN_ONLY")
        String environmentCreationPolicy,

        @Schema(description = "Whether production environments enforce elevated protection", example = "true")
        boolean productionProtectionEnforced
) {
}
