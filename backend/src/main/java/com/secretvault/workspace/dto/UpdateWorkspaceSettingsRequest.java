package com.secretvault.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Request payload to update workspace configuration settings and security policies.
 */
@Schema(description = "Update workspace settings payload")
public record UpdateWorkspaceSettingsRequest(
        @Schema(description = "Workspace display name", example = "Core Infrastructure Engineering")
        @Size(min = 2, max = 255, message = "Workspace name must be between 2 and 255 characters")
        String name,

        @Schema(description = "Project creation policy (ALL_MEMBERS | ADMIN_ONLY)", example = "ALL_MEMBERS")
        String projectCreationPolicy,

        @Schema(description = "Environment creation policy (ADMIN_ONLY)", example = "ADMIN_ONLY")
        String environmentCreationPolicy,

        @Schema(description = "Whether production environments require protected status", example = "true")
        Boolean productionProtectionEnforced
) {
}
