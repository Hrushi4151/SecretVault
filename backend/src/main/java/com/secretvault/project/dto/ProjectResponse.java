package com.secretvault.project.dto;

import com.secretvault.project.entity.Project;
import com.secretvault.project.entity.ProjectStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public response representing a Project and its metadata.
 */
@Schema(description = "Project representation")
public record ProjectResponse(
        @Schema(description = "Project UUID")
        UUID id,

        @Schema(description = "Parent Workspace UUID")
        UUID workspaceId,

        @Schema(description = "Project display name")
        String name,

        @Schema(description = "URL-friendly project slug")
        String slug,

        @Schema(description = "Project description")
        String description,

        @Schema(description = "Project lifecycle status")
        ProjectStatus status,

        @Schema(description = "Creator User UUID")
        UUID createdBy,

        @Schema(description = "Project creation timestamp")
        Instant createdAt,

        @Schema(description = "Project last update timestamp")
        Instant updatedAt,

        @Schema(description = "Environments configured under this project")
        List<EnvironmentSummaryResponse> environments
) {
    public static ProjectResponse fromEntity(Project project, List<EnvironmentSummaryResponse> environments) {
        return new ProjectResponse(
                project.getId(),
                project.getWorkspaceId(),
                project.getName(),
                project.getSlug(),
                project.getDescription(),
                project.getStatus(),
                project.getCreatedBy(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                environments != null ? environments : List.of()
        );
    }
}
