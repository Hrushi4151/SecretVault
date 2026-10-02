package com.secretvault.secret.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Shared authorization and hierarchy validator for all secret domain operations.
 * Integrates with EffectiveAccessService to enforce strict tenant scoping, hierarchical validation,
 * and role/grant intersection.
 */
@Component
public class SecretAuthorizationHelper {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final EffectiveAccessService effectiveAccessService;
    private final com.secretvault.machine.repository.MachineIdentityRepository machineIdentityRepository;

    public SecretAuthorizationHelper(
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            EffectiveAccessService effectiveAccessService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.secretvault.machine.repository.MachineIdentityRepository machineIdentityRepository
    ) {
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.effectiveAccessService = effectiveAccessService;
        this.machineIdentityRepository = machineIdentityRepository;
    }

    public record WorkspaceContext(Workspace workspace, WorkspaceMembership membership, Project project, Environment environment) {
    }

    public static String buildAad(UUID secretId, UUID environmentId, int versionNumber) {
        return secretId.toString() + ":" + environmentId.toString() + ":" + versionNumber;
    }

    public WorkspaceContext verifyHierarchy(UUID workspaceId, UUID projectId, UUID environmentId, UUID userId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> ApiException.notFound("Workspace not found"));

        WorkspaceMembership membership = null;
        boolean isMachine = false;

        if (machineIdentityRepository != null) {
            Optional<com.secretvault.machine.entity.MachineIdentity> machineOpt = machineIdentityRepository.findByIdAndDeletedAtIsNull(userId);
            if (machineOpt.isPresent()) {
                com.secretvault.machine.entity.MachineIdentity machine = machineOpt.get();
                if (!machine.getWorkspaceId().equals(workspaceId)) {
                    throw ApiException.forbidden("Cross-tenant access denied: Machine identity does not belong to this workspace");
                }
                if (!machine.isUsable(java.time.Instant.now())) {
                    throw ApiException.forbidden("Machine identity is " + machine.getStatus());
                }
                isMachine = true;
            }
        }

        if (!isMachine) {
            membership = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                    .orElseThrow(() -> ApiException.forbidden("You are not a member of this workspace"));
        }

        Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Project not found in this workspace"));

        Environment environment = environmentRepository.findByIdAndProjectId(environmentId, projectId)
                .orElseThrow(() -> ApiException.notFound("Environment not found in this project"));

        return new WorkspaceContext(workspace, membership, project, environment);
    }

    public WorkspaceContext verifyHierarchyAndReadAccess(UUID workspaceId, UUID projectId, UUID environmentId, UUID userId) {
        WorkspaceContext context = verifyHierarchy(workspaceId, projectId, environmentId, userId);
        effectiveAccessService.checkPermission(workspaceId, projectId, environmentId, null, AccessPermission.SECRET_READ, userId);
        return context;
    }

    public WorkspaceContext verifyHierarchyAndWriteAccess(UUID workspaceId, UUID projectId, UUID environmentId, UUID userId) {
        WorkspaceContext context = verifyHierarchy(workspaceId, projectId, environmentId, userId);
        effectiveAccessService.checkPermission(workspaceId, projectId, environmentId, null, AccessPermission.SECRET_UPDATE, userId);
        return context;
    }

    public WorkspaceContext verifyHierarchyAndRevealAccess(UUID workspaceId, UUID projectId, UUID environmentId, UUID userId) {
        WorkspaceContext context = verifyHierarchy(workspaceId, projectId, environmentId, userId);
        effectiveAccessService.checkPermission(workspaceId, projectId, environmentId, null, AccessPermission.SECRET_REVEAL, userId);
        return context;
    }

    public WorkspaceContext verifyHierarchyAndBranchWriteAccess(UUID workspaceId, UUID projectId, UUID environmentId, UUID userId) {
        WorkspaceContext context = verifyHierarchy(workspaceId, projectId, environmentId, userId);
        effectiveAccessService.checkPermission(workspaceId, projectId, environmentId, null, AccessPermission.SECRET_BRANCH, userId);
        return context;
    }

    public WorkspaceContext verifyHierarchyAndBranchReadAccess(UUID workspaceId, UUID projectId, UUID environmentId, UUID userId) {
        WorkspaceContext context = verifyHierarchy(workspaceId, projectId, environmentId, userId);
        if (context.environment().getEnvType() != com.secretvault.environment.entity.EnvType.DEVELOPMENT) {
            throw ApiException.branchesNotAllowed(context.environment().getName(), context.environment().getEnvType());
        }
        effectiveAccessService.checkPermission(workspaceId, projectId, environmentId, null, AccessPermission.SECRET_READ, userId);
        return context;
    }
}
