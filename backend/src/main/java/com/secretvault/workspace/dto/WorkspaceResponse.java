package com.secretvault.workspace.dto;

import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Public response representing a Workspace and the caller's role within it.
 */
@Schema(description = "Workspace representation")
public record WorkspaceResponse(
        @Schema(description = "Workspace UUID")
        UUID id,

        @Schema(description = "Parent Organization UUID")
        UUID organizationId,

        @Schema(description = "Workspace display name")
        String name,

        @Schema(description = "URL-friendly workspace slug")
        String slug,

        @Schema(description = "User's role within this workspace")
        WorkspaceRole role,

        @Schema(description = "Whether this is the user's default workspace")
        boolean isDefault,

        @Schema(description = "Workspace creation timestamp")
        Instant createdAt
) {
    public static WorkspaceResponse fromEntity(Workspace workspace, WorkspaceRole role) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getOrganizationId(),
                workspace.getName(),
                workspace.getSlug(),
                role,
                workspace.isDefault(),
                workspace.getCreatedAt()
        );
    }
}
