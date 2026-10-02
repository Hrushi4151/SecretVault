package com.secretvault.access.dto;

import com.secretvault.access.grant.dto.AccessGrantResponse;
import com.secretvault.access.jit.dto.JitAccessRequestResponse;
import com.secretvault.environment.access.entity.PermissionLevel;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.workspace.entity.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Comprehensive access configuration and standing/effective permissions overview for a workspace member")
public record MemberAccessOverviewResponse(
        @Schema(description = "Member identity summary")
        MemberSummary member,

        @Schema(description = "Configured project and environment access hierarchy")
        List<ProjectAccessSummary> projects,

        @Schema(description = "Active granular access grants for this user")
        List<AccessGrantResponse> granularGrants,

        @Schema(description = "Active JIT temporary access elevations for this user")
        List<JitAccessRequestResponse> activeJitGrants
) {
    public record MemberSummary(
            UUID id,
            String fullName,
            String email,
            WorkspaceRole workspaceRole
    ) {}

    public record ProjectAccessSummary(
            UUID projectId,
            String projectName,
            String projectSlug,
            String description,
            WorkspaceRole projectRole,
            WorkspaceRole effectiveProjectRole,
            List<EnvironmentAccessSummary> environments
    ) {}

    public record EnvironmentAccessSummary(
            UUID environmentId,
            String name,
            EnvType envType,
            boolean isProduction,
            boolean isProtected,
            PermissionLevel environmentPermission,
            PermissionLevel effectivePermission
    ) {}
}
