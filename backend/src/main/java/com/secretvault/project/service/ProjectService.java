package com.secretvault.project.service;

import com.secretvault.access.grant.entity.AccessGrant;
import com.secretvault.access.grant.repository.AccessGrantRepository;
import com.secretvault.access.jit.entity.JitAccessRequest;
import com.secretvault.access.jit.entity.JitStatus;
import com.secretvault.access.jit.repository.JitAccessRequestRepository;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.access.entity.EnvironmentAccess;
import com.secretvault.environment.access.repository.EnvironmentAccessRepository;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.access.entity.ProjectAccess;
import com.secretvault.project.access.repository.ProjectAccessRepository;
import com.secretvault.project.dto.CreateProjectRequest;
import com.secretvault.project.dto.EnvironmentSummaryResponse;
import com.secretvault.project.dto.ProjectResponse;
import com.secretvault.project.dto.UpdateProjectRequest;
import com.secretvault.project.entity.Project;
import com.secretvault.project.entity.ProjectStatus;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final ProjectAccessRepository projectAccessRepository;
    private final EnvironmentAccessRepository environmentAccessRepository;
    private final AccessGrantRepository accessGrantRepository;
    private final JitAccessRequestRepository jitRepository;
    private final EffectiveAccessService effectiveAccessService;

    public ProjectService(
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository
    ) {
        this(projectRepository, environmentRepository, workspaceRepository, membershipRepository,
             null, null, null, null, null);
    }

    public ProjectService(
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            ProjectAccessRepository projectAccessRepository,
            EnvironmentAccessRepository environmentAccessRepository,
            AccessGrantRepository accessGrantRepository,
            JitAccessRequestRepository jitRepository
    ) {
        this(projectRepository, environmentRepository, workspaceRepository, membershipRepository,
             projectAccessRepository, environmentAccessRepository, accessGrantRepository, jitRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ProjectService(
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ProjectAccessRepository projectAccessRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) EnvironmentAccessRepository environmentAccessRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) AccessGrantRepository accessGrantRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) JitAccessRequestRepository jitRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) EffectiveAccessService effectiveAccessService
    ) {
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.projectAccessRepository = projectAccessRepository;
        this.environmentAccessRepository = environmentAccessRepository;
        this.accessGrantRepository = accessGrantRepository;
        this.jitRepository = jitRepository;
        this.effectiveAccessService = effectiveAccessService;
    }

    /**
     * Lists all projects within a workspace accessible to the authenticated member.
     * Evaluates server-side project authorization so unassigned/unauthorized projects are not exposed.
     */
    @Transactional(readOnly = true)
    public List<ProjectResponse> getProjects(UUID workspaceId, UUID userId) {
        WorkspaceMembership membership = verifyWorkspaceAccess(workspaceId, userId);

        List<Project> projects = projectRepository.findByWorkspaceId(workspaceId);
        List<ProjectResponse> responses = new ArrayList<>();

        for (Project project : projects) {
            if (!isUserAuthorizedForProject(workspaceId, project, membership, userId)) {
                continue;
            }

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
     * Enforces IDOR protection by rejecting requests for unauthorized projects.
     */
    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(UUID workspaceId, UUID projectId, UUID userId) {
        WorkspaceMembership membership = verifyWorkspaceAccess(workspaceId, userId);

        Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Project not found in this workspace"));

        if (!isUserAuthorizedForProject(workspaceId, project, membership, userId)) {
            throw ApiException.forbidden("You are not authorized to access this project");
        }

        List<EnvironmentSummaryResponse> envs = environmentRepository.findByProjectId(project.getId())
                .stream()
                .map(EnvironmentSummaryResponse::fromEntity)
                .toList();

        return ProjectResponse.fromEntity(project, envs);
    }

    /**
     * Evaluates whether a user has authorized access or involvement in a project.
     */
    public boolean isUserAuthorizedForProject(
            UUID workspaceId,
            Project project,
            WorkspaceMembership membership,
            UUID userId
    ) {
        if (effectiveAccessService != null) {
            return effectiveAccessService.isUserAuthorizedForProject(workspaceId, project, membership, userId);
        }

        WorkspaceRole wsRole = membership.getRole();
        if (wsRole == WorkspaceRole.OWNER || wsRole == WorkspaceRole.ADMIN) {
            return true;
        }

        UUID projectId = project.getId();

        // 1. Check explicit ProjectAccess
        if (projectAccessRepository != null) {
            Optional<ProjectAccess> projAccess = projectAccessRepository.findByProjectIdAndUserId(projectId, userId);
            if (projAccess.isPresent() && projAccess.get().getRole() != null) {
                return true;
            }
        }

        // 2. Check explicit EnvironmentAccess on any of this project's environments
        List<Environment> projectEnvironments = environmentRepository.findByProjectId(projectId);
        List<UUID> projectEnvIds = projectEnvironments.stream().map(Environment::getId).toList();

        if (environmentAccessRepository != null && !projectEnvIds.isEmpty()) {
            List<EnvironmentAccess> userEnvAccesses = environmentAccessRepository.findByUserId(userId);
            boolean hasEnvAccess = userEnvAccesses.stream()
                    .anyMatch(ea -> projectEnvIds.contains(ea.getEnvironmentId()) && ea.getPermissionLevel() != null);
            if (hasEnvAccess) {
                return true;
            }
        }

        // 3. Check Granular AccessGrants
        if (accessGrantRepository != null) {
            List<AccessGrant> userGrants = accessGrantRepository.findByWorkspaceIdAndUserId(workspaceId, userId);
            boolean hasProjectGrant = userGrants.stream().anyMatch(g ->
                    (g.getProjectId() != null && g.getProjectId().equals(projectId)) ||
                    (g.getEnvironmentId() != null && projectEnvIds.contains(g.getEnvironmentId()))
            );
            if (hasProjectGrant) {
                return true;
            }
        }

        // 4. Check active unexpired JIT access requests
        if (jitRepository != null) {
            List<JitAccessRequest> userJits = jitRepository.findByWorkspaceIdAndUserId(workspaceId, userId);
            Instant now = Instant.now();
            boolean hasActiveJit = userJits.stream().anyMatch(j ->
                    j.getStatus() == JitStatus.APPROVED &&
                    j.getExpiresAt() != null && j.getExpiresAt().isAfter(now) &&
                    ((j.getProjectId() != null && j.getProjectId().equals(projectId)) ||
                     (j.getEnvironmentId() != null && projectEnvIds.contains(j.getEnvironmentId())))
            );
            if (hasActiveJit) {
                return true;
            }
        }

        // 5. Check if user has ANY scoped access records in this workspace.
        // If user has scoped records on other projects/environments in this workspace,
        // then access is constrained to only authorized targets (this project is unassigned -> false).
        boolean hasAnyScopedRecordsInWorkspace = hasAnyScopedAccessInWorkspace(workspaceId, userId);
        if (hasAnyScopedRecordsInWorkspace) {
            return false;
        }

        // 6. Default fallback: Unrestricted standing member has default access
        return true;
    }

    private boolean hasAnyScopedAccessInWorkspace(UUID workspaceId, UUID userId) {
        if (projectAccessRepository != null) {
            List<ProjectAccess> userProjAccesses = projectAccessRepository.findByUserId(userId);
            List<Project> wsProjects = projectRepository.findByWorkspaceId(workspaceId);
            Set<UUID> wsProjIds = wsProjects.stream().map(Project::getId).collect(Collectors.toSet());
            boolean hasProjInWs = userProjAccesses.stream().anyMatch(pa -> wsProjIds.contains(pa.getProjectId()));
            if (hasProjInWs) return true;

            if (environmentAccessRepository != null) {
                List<EnvironmentAccess> userEnvAccesses = environmentAccessRepository.findByUserId(userId);
                List<UUID> wsEnvIds = new ArrayList<>();
                for (Project p : wsProjects) {
                    wsEnvIds.addAll(environmentRepository.findByProjectId(p.getId()).stream().map(Environment::getId).toList());
                }
                boolean hasEnvInWs = userEnvAccesses.stream().anyMatch(ea -> wsEnvIds.contains(ea.getEnvironmentId()));
                if (hasEnvInWs) return true;
            }

            if (accessGrantRepository != null) {
                List<AccessGrant> userGrants = accessGrantRepository.findByWorkspaceIdAndUserId(workspaceId, userId);
                if (!userGrants.isEmpty()) return true;
            }
        }
        return false;
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
