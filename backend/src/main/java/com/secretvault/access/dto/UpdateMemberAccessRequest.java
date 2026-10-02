package com.secretvault.access.dto;

import com.secretvault.environment.access.entity.PermissionLevel;
import com.secretvault.workspace.entity.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

@Schema(description = "Transactional update request for a member's scoped project and environment access")
public record UpdateMemberAccessRequest(
        @Schema(description = "List of project access configurations")
        @NotNull
        @Valid
        List<ProjectAccessConfig> projectConfigs
) {
    public record ProjectAccessConfig(
            @NotNull
            UUID projectId,

            @Schema(description = "Scoped project role: VIEWER, DEVELOPER, ADMIN, or null to revoke")
            WorkspaceRole role,

            @Valid
            List<EnvironmentAccessConfig> environmentConfigs
    ) {}

    public record EnvironmentAccessConfig(
            @NotNull
            UUID environmentId,

            @Schema(description = "Scoped environment permission: READ, WRITE, MANAGE, or null to revoke")
            PermissionLevel permissionLevel
    ) {}
}
