package com.secretvault.environment.service;

import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.dto.CreateEnvironmentRequest;
import com.secretvault.environment.dto.EnvironmentResponse;
import com.secretvault.environment.dto.UpdateEnvironmentRequest;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Service managing Environment lifecycle and hierarchy authorization:
 * User -> Workspace Membership -> Workspace -> Project -> Environment.
 */
@Service
public class EnvironmentService {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentService.class);

    private final EnvironmentRepository environmentRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;

    public EnvironmentService(
            EnvironmentRepository environmentRepository,
            ProjectRepository projectRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository) {
        this.environmentRepository = environmentRepository;
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
    }

    /**
     * Lists all environments belonging to the specified project.
     * Validates workspace membership and project ownership.
     */
    @Transactional(readOnly = true)
    public List<EnvironmentResponse> getEnvironments(UUID workspaceId, UUID projectId, UUID userId) {
        verifyHierarchyAccess(workspaceId, projectId, userId);

        return environmentRepository.findByProjectId(projectId)
                .stream()
                .map(EnvironmentResponse::fromEntity)
                .toList();
    }

    /**
     * Retrieves details for a specific environment.
     * Validates that environment belongs to project, and project belongs to workspace.
     */
    @Transactional(readOnly = true)
    public EnvironmentResponse getEnvironmentById(UUID workspaceId, UUID projectId, UUID environmentId, UUID userId) {
        verifyHierarchyAccess(workspaceId, projectId, userId);

        Environment environment = environmentRepository.findByIdAndProjectId(environmentId, projectId)
                .orElseThrow(() -> ApiException.notFound("Environment not found in this project"));

        return EnvironmentResponse.fromEntity(environment);
    }

    /**
     * Creates a new Environment within a project.
     * Requires OWNER or ADMIN role on the workspace.
     */
    @Transactional
    public EnvironmentResponse createEnvironment(UUID workspaceId, UUID projectId, CreateEnvironmentRequest request, UUID userId) {
        WorkspaceMembership membership = verifyHierarchyAccess(workspaceId, projectId, userId);

        if (!membership.getRole().canManageEnvironments()) {
            throw ApiException.forbidden("Only OWNER or ADMIN can create custom environments");
        }

        String slug = StringUtils.hasText(request.slug())
                ? request.slug().toLowerCase(Locale.ROOT)
                : generateSlug(request.name());

        if (environmentRepository.existsByProjectIdAndSlug(projectId, slug)) {
            throw ApiException.conflict("An environment with slug '" + slug + "' already exists in this project");
        }

        boolean isProtected = Boolean.TRUE.equals(request.isProtected());

        Environment environment = new Environment(
                projectId,
                request.name().trim(),
                slug,
                request.envType(),
                request.description() != null ? request.description().trim() : null,
                isProtected,
                userId
        );
        environment = environmentRepository.save(environment);

        log.info("Created environment [{}] ({}) in project [{}] workspace [{}] by user [{}]",
                environment.getId(), environment.getSlug(), projectId, workspaceId, userId);

        return EnvironmentResponse.fromEntity(environment);
    }

    /**
     * Updates environment metadata, protection status, or tier type.
     * Requires OWNER or ADMIN role on the workspace.
     */
    @Transactional
    public EnvironmentResponse updateEnvironment(UUID workspaceId, UUID projectId, UUID environmentId, UpdateEnvironmentRequest request, UUID userId) {
        WorkspaceMembership membership = verifyHierarchyAccess(workspaceId, projectId, userId);

        if (!membership.getRole().canManageEnvironments()) {
            throw ApiException.forbidden("Only OWNER or ADMIN can modify environments");
        }

        Environment environment = environmentRepository.findByIdAndProjectId(environmentId, projectId)
                .orElseThrow(() -> ApiException.notFound("Environment not found in this project"));

        if (StringUtils.hasText(request.name())) {
            environment.setName(request.name().trim());
        }
        if (request.description() != null) {
            environment.setDescription(request.description().trim());
        }
        if (request.envType() != null) {
            environment.setEnvType(request.envType());
        }
        if (request.isProtected() != null) {
            environment.setProtected(request.isProtected());
        }
        if (request.status() != null) {
            environment.setStatus(request.status());
        }

        environment = environmentRepository.save(environment);

        log.info("Updated environment [{}] in project [{}] by user [{}]",
                environment.getId(), projectId, userId);

        return EnvironmentResponse.fromEntity(environment);
    }

    /**
     * Deletes an environment.
     * Requires OWNER or ADMIN role on the workspace.
     */
    @Transactional
    public void deleteEnvironment(UUID workspaceId, UUID projectId, UUID environmentId, UUID userId) {
        WorkspaceMembership membership = verifyHierarchyAccess(workspaceId, projectId, userId);

        if (!membership.getRole().canManageEnvironments()) {
            throw ApiException.forbidden("Only OWNER or ADMIN can delete environments");
        }

        Environment environment = environmentRepository.findByIdAndProjectId(environmentId, projectId)
                .orElseThrow(() -> ApiException.notFound("Environment not found in this project"));

        environmentRepository.delete(environment);

        log.info("Deleted environment [{}] from project [{}] by user [{}]",
                environmentId, projectId, userId);
    }

    /**
     * Validates the complete chain: Workspace exists -> User is member -> Project belongs to Workspace.
     */
    private WorkspaceMembership verifyHierarchyAccess(UUID workspaceId, UUID projectId, UUID userId) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw ApiException.notFound("Workspace not found");
        }

        WorkspaceMembership membership = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this workspace"));

        Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Project not found in this workspace"));

        return membership;
    }

    private String generateSlug(String input) {
        String slug = input.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return StringUtils.hasText(slug) ? slug : "env-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
