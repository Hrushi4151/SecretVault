package com.secretvault.project.service;

import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.dto.CreateProjectRequest;
import com.secretvault.project.dto.EnvironmentSummaryResponse;
import com.secretvault.project.dto.ProjectResponse;
import com.secretvault.project.dto.UpdateProjectRequest;
import com.secretvault.project.entity.Project;
import com.secretvault.project.entity.ProjectStatus;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Service managing Project lifecycle, hierarchy validation,
 * automatic environment provisioning, and RBAC authorization.
 */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository) {
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Lists all projects within a workspace accessible to the authenticated member.
     */
    @Transactional(readOnly = true)
    public List<ProjectResponse> getProjects(UUID workspaceId, UUID userId) {
        verifyWorkspaceAccess(workspaceId, userId);

        List<Project> projects = projectRepository.findByWorkspaceId(workspaceId);
        List<ProjectResponse> responses = new ArrayList<>();

        for (Project project : projects) {
            List<EnvironmentSummaryResponse> envs = environmentRepository.findByProjectId(project.getId())
                    .stream()
                    .map(EnvironmentSummaryResponse::fromEntity)
                    .toList();
            responses.add(ProjectResponse.fromEntity(project, envs));
        }

        return responses;
    }

    /**
     * Retrieves details for a specific project within a workspace.
     */
    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(UUID workspaceId, UUID projectId, UUID userId) {
        verifyWorkspaceAccess(workspaceId, userId);

        Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Project not found in this workspace"));

        List<EnvironmentSummaryResponse> envs = environmentRepository.findByProjectId(project.getId())
                .stream()
                .map(EnvironmentSummaryResponse::fromEntity)
                .toList();

        return ProjectResponse.fromEntity(project, envs);
    }

    /**
     * Creates a new Project and automatically seeds default environments
     * (development, staging, production) within a single transaction.
     */
    @Transactional
    public ProjectResponse createProject(UUID workspaceId, CreateProjectRequest request, UUID userId) {
        WorkspaceMembership membership = verifyWorkspaceAccess(workspaceId, userId);

        if (!membership.getRole().canCreateProjects()) {
            throw ApiException.forbidden("Insufficient permissions to create projects in this workspace");
        }

        String slug = StringUtils.hasText(request.slug())
                ? request.slug().toLowerCase(Locale.ROOT)
                : generateSlug(request.name());

        if (projectRepository.existsByWorkspaceIdAndSlug(workspaceId, slug)) {
            throw ApiException.conflict("A project with slug '" + slug + "' already exists in this workspace");
        }

        Project project = new Project(
                workspaceId,
                request.name().trim(),
                slug,
                request.description() != null ? request.description().trim() : null,
                userId
        );
        project = projectRepository.save(project);

        // Auto-seed default environments
        List<Environment> defaultEnvs = List.of(
                new Environment(project.getId(), "Development", "development", EnvType.DEVELOPMENT, "Local and developer testing environment", false, userId),
                new Environment(project.getId(), "Staging", "staging", EnvType.STAGING, "Pre-production integration testing environment", false, userId),
                new Environment(project.getId(), "Production", "production", EnvType.PRODUCTION, "Live production workload", true, userId)
        );
        List<Environment> savedEnvs = environmentRepository.saveAll(defaultEnvs);

        log.info("Created project [{}] with slug [{}] and seeded {} default environments in workspace [{}] by user [{}]",
                project.getId(), project.getSlug(), savedEnvs.size(), workspaceId, userId);

        List<EnvironmentSummaryResponse> envSummaries = savedEnvs.stream()
                .map(EnvironmentSummaryResponse::fromEntity)
                .toList();

        return ProjectResponse.fromEntity(project, envSummaries);
    }

    /**
     * Updates project metadata (name, description, status).
     * Requires OWNER or ADMIN role on the workspace.
     */
    @Transactional
    public ProjectResponse updateProject(UUID workspaceId, UUID projectId, UpdateProjectRequest request, UUID userId) {
        WorkspaceMembership membership = verifyWorkspaceAccess(workspaceId, userId);

        if (!membership.getRole().canManageProjects()) {
            throw ApiException.forbidden("Only OWNER or ADMIN can modify project settings");
        }

        Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Project not found in this workspace"));

        if (StringUtils.hasText(request.name())) {
            project.setName(request.name().trim());
        }
        if (request.description() != null) {
            project.setDescription(request.description().trim());
        }
        if (request.status() != null) {
            project.setStatus(request.status());
        }

        project = projectRepository.save(project);

        log.info("Updated project [{}] in workspace [{}] by user [{}]",
                project.getId(), workspaceId, userId);

        List<EnvironmentSummaryResponse> envs = environmentRepository.findByProjectId(project.getId())
                .stream()
                .map(EnvironmentSummaryResponse::fromEntity)
                .toList();

        return ProjectResponse.fromEntity(project, envs);
    }

    /**
     * Deletes a project and all its associated environments.
     * Requires OWNER or ADMIN role on the workspace.
     */
    @Transactional
    public void deleteProject(UUID workspaceId, UUID projectId, UUID userId) {
        WorkspaceMembership membership = verifyWorkspaceAccess(workspaceId, userId);

        if (!membership.getRole().canManageProjects()) {
            throw ApiException.forbidden("Only OWNER or ADMIN can delete projects");
        }

        Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Project not found in this workspace"));

        projectRepository.delete(project);

        log.info("Deleted project [{}] from workspace [{}] by user [{}]",
                projectId, workspaceId, userId);
    }

    /**
     * Validates that the caller has an active membership in the specified workspace.
     */
    private WorkspaceMembership verifyWorkspaceAccess(UUID workspaceId, UUID userId) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw ApiException.notFound("Workspace not found");
        }
        return membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this workspace"));
    }

    private String generateSlug(String input) {
        String slug = input.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return StringUtils.hasText(slug) ? slug : "proj-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
