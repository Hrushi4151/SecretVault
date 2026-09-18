package com.secretvault.secret.service;

import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.access.entity.EnvironmentAccess;
import com.secretvault.environment.access.entity.PermissionLevel;
import com.secretvault.environment.access.repository.EnvironmentAccessRepository;
import com.secretvault.environment.access.service.EnvironmentAccessService;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.access.entity.ProjectAccess;
import com.secretvault.project.access.repository.ProjectAccessRepository;
import com.secretvault.project.access.service.ProjectAccessService;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Shared authorization and hierarchy validator for all secret domain operations.
 * Enforces strict tenant scoping, hierarchical validation, and role intersection.
 */
@Component
public class SecretAuthorizationHelper {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final ProjectAccessRepository projectAccessRepository;
    private final EnvironmentAccessRepository environmentAccessRepository;

    public SecretAuthorizationHelper(
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            ProjectAccessRepository projectAccessRepository,
            EnvironmentAccessRepository environmentAccessRepository
    ) {
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.projectAccessRepository = projectAccessRepository;
        this.environmentAccessRepository = environmentAccessRepository;
    }

    public record WorkspaceContext(Workspace workspace, WorkspaceMembership membership, Project project, Environment environment) {
    }

    public static String buildAad(UUID secretId, UUID environmentId, int versionNumber) {
        return secretId.toString() + ":" + environmentId.toString() + ":" + versionNumber;
    }

    public WorkspaceContext verifyHierarchy(UUID workspaceId, UUID projectId, UUID environmentId, UUID userId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> ApiException.notFound("Workspace not found"));

        WorkspaceMembership membership = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this workspace"));

        Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Project not found in this workspace"));

        Environment environment = environmentRepository.findByIdAndProjectId(environmentId, projectId)
                .orElseThrow(() -> ApiException.notFound("Environment not found in this project"));

        return new WorkspaceContext(workspace, membership, project, environment);
    }

    public WorkspaceContext verifyHierarchyAndReadAccess(UUID workspaceId, UUID projectId, UUID environmentId, UUID userId) {
        return verifyHierarchy(workspaceId, projectId, environmentId, userId);
    }

    public WorkspaceContext verifyHierarchyAndWriteAccess(UUID workspaceId, UUID projectId, UUID environmentId, UUID userId) {
        WorkspaceContext context = verifyHierarchy(workspaceId, projectId, environmentId, userId);
        WorkspaceRole wsRole = context.membership().getRole();

        // 1. VIEWER cannot write
        if (wsRole == WorkspaceRole.VIEWER) {
            throw ApiException.forbidden("VIEWER role cannot modify secrets");
        }

        // 2. Intersect with Scoped Project Access
        Optional<ProjectAccess> projAccess = projectAccessRepository.findByProjectIdAndUserId(projectId, userId);
        WorkspaceRole effProjectRole = ProjectAccessService.computeEffectiveRole(
                wsRole,
                projAccess.map(ProjectAccess::getRole).orElse(wsRole)
        );

        // 3. Intersect with Scoped Environment Access
        Optional<EnvironmentAccess> envAccess = environmentAccessRepository.findByEnvironmentIdAndUserId(environmentId, userId);
        PermissionLevel effEnvPerm = EnvironmentAccessService.computeEffectivePermission(
                effProjectRole,
                envAccess.map(EnvironmentAccess::getPermissionLevel).orElse(null)
        );

        if (effEnvPerm == PermissionLevel.READ) {
            throw ApiException.forbidden("Insufficient permissions: effective permission on this environment is READ only");
        }

        return context;
    }

    public WorkspaceContext verifyHierarchyAndRevealAccess(UUID workspaceId, UUID projectId, UUID environmentId, UUID userId) {
        WorkspaceContext context = verifyHierarchy(workspaceId, projectId, environmentId, userId);
        WorkspaceRole wsRole = context.membership().getRole();

        // VIEWER role is strictly forbidden from revealing secrets
        if (wsRole == WorkspaceRole.VIEWER) {
            throw ApiException.forbidden("VIEWER role is not authorized to reveal secret values");
        }

        return context;
    }

    public WorkspaceContext verifyHierarchyAndBranchWriteAccess(UUID workspaceId, UUID projectId, UUID environmentId, UUID userId) {
        WorkspaceContext context = verifyHierarchyAndWriteAccess(workspaceId, projectId, environmentId, userId);
        if (context.environment().getEnvType() != com.secretvault.environment.entity.EnvType.DEVELOPMENT) {
            throw ApiException.branchesNotAllowed(context.environment().getName(), context.environment().getEnvType());
        }
        return context;
    }

    public WorkspaceContext verifyHierarchyAndBranchReadAccess(UUID workspaceId, UUID projectId, UUID environmentId, UUID userId) {
        WorkspaceContext context = verifyHierarchyAndReadAccess(workspaceId, projectId, environmentId, userId);
        if (context.environment().getEnvType() != com.secretvault.environment.entity.EnvType.DEVELOPMENT) {
            throw ApiException.branchesNotAllowed(context.environment().getName(), context.environment().getEnvType());
        }
        return context;
    }
}
