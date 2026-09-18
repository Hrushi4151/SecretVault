package com.secretvault.access.service;

import com.secretvault.access.dto.EffectiveAccessExplanation;
import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.model.AccessSourceType;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.access.entity.EnvironmentAccess;
import com.secretvault.environment.access.entity.PermissionLevel;
import com.secretvault.environment.access.repository.EnvironmentAccessRepository;
import com.secretvault.environment.access.service.EnvironmentAccessService;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.access.entity.ProjectAccess;
import com.secretvault.project.access.repository.ProjectAccessRepository;
import com.secretvault.project.access.service.ProjectAccessService;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Authoritative, centralized authorization decision engine for SecretVault.
 * Evaluates tenant, project, and environment boundaries, security invariants,
 * standing RBAC scoping, and future granular/JIT extension points.
 */
@Service
public class EffectiveAccessService {

    private static final Logger log = LoggerFactory.getLogger(EffectiveAccessService.class);

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final SecretRepository secretRepository;
    private final ProjectAccessRepository projectAccessRepository;
    private final EnvironmentAccessRepository environmentAccessRepository;

    public EffectiveAccessService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            SecretRepository secretRepository,
            ProjectAccessRepository projectAccessRepository,
            EnvironmentAccessRepository environmentAccessRepository
    ) {
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.secretRepository = secretRepository;
        this.projectAccessRepository = projectAccessRepository;
        this.environmentAccessRepository = environmentAccessRepository;
    }

    /**
     * Evaluates whether an authenticated actor has a specific permission on a resource target.
     * Executes the authoritative 9-step evaluation pipeline and returns a structured AccessDecision.
     */
    @Transactional(readOnly = true)
    public AccessDecision evaluateAccess(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            AccessPermission permission,
            UUID userId
    ) {
        if (permission == null) {
            return AccessDecision.deny(null, "Permission must not be null");
        }
        if (userId == null) {
            return AccessDecision.deny(permission, "Unauthenticated actor: user ID must not be null");
        }
        if (workspaceId == null) {
            return AccessDecision.deny(permission, "Tenant context missing: workspace ID must not be null");
        }

        // 1. Tenant / Workspace Membership Check
        if (!workspaceRepository.existsById(workspaceId)) {
            return AccessDecision.deny(permission, AccessScope.WORKSPACE, "Workspace not found");
        }
        Optional<WorkspaceMembership> membershipOpt = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (membershipOpt.isEmpty()) {
            return AccessDecision.deny(permission, AccessScope.WORKSPACE, "User is not an active member of this workspace");
        }
        WorkspaceMembership membership = membershipOpt.get();
        WorkspaceRole wsRole = membership.getRole();

        // 2. Secret Auto-resolution / validation (if secretId given)
        Secret secret = null;
        if (secretId != null) {
            Optional<Secret> sOpt = secretRepository.findById(secretId);
            if (sOpt.isEmpty()) {
                return AccessDecision.deny(permission, AccessScope.SECRET, "Secret not found");
            }
            secret = sOpt.get();
            if (environmentId != null && !secret.getEnvironmentId().equals(environmentId)) {
                return AccessDecision.deny(permission, AccessScope.SECRET, "Secret does not belong to the specified environment");
            }
            environmentId = secret.getEnvironmentId();
        }

        // 3. Environment Auto-resolution (if environmentId given but projectId missing)
        Environment environment = null;
        if (environmentId != null && projectId == null) {
            Optional<Environment> eOpt = environmentRepository.findById(environmentId);
            if (eOpt.isEmpty()) {
                return AccessDecision.deny(permission, AccessScope.ENVIRONMENT, "Environment not found");
            }
            environment = eOpt.get();
            projectId = environment.getProjectId();
        }

        // 4. Project Boundary Validation
        Project project = null;
        if (projectId != null) {
            Optional<Project> projOpt = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId);
            if (projOpt.isEmpty()) {
                return AccessDecision.deny(permission, AccessScope.PROJECT, "Project not found in this workspace");
            }
            project = projOpt.get();
        }

        // 5. Environment Boundary Validation
        if (environmentId != null && environment == null) {
            Optional<Environment> envOpt = environmentRepository.findByIdAndProjectId(environmentId, projectId);
            if (envOpt.isEmpty()) {
                return AccessDecision.deny(permission, AccessScope.ENVIRONMENT, "Environment not found in this project");
            }
            environment = envOpt.get();
        }

        // 6. Hard Security Invariants Check (Phase 4 Branch Policy & Deleted Status)
        if (secret != null && secret.getStatus() == SecretStatus.DELETED) {
            if (permission == AccessPermission.SECRET_UPDATE ||
                    permission == AccessPermission.SECRET_DELETE ||
                    permission == AccessPermission.SECRET_ROLLBACK ||
                    permission == AccessPermission.SECRET_BRANCH) {
                return AccessDecision.deny(permission, AccessScope.SECRET, "Cannot perform mutations on a deleted secret");
            }
        }

        if (permission == AccessPermission.SECRET_BRANCH && environment != null) {
            if (environment.getEnvType() != EnvType.DEVELOPMENT) {
                return AccessDecision.deny(
                        permission,
                        AccessScope.ENVIRONMENT,
                        "Feature branches are only permitted in DEVELOPMENT environments. Environment [" + environment.getName() + "] is of type " + environment.getEnvType() + "."
                );
            }
        }

        // 7. Compute Effective Standing RBAC Hierarchy
        WorkspaceRole effProjectRole = null;
        if (projectId != null) {
            Optional<ProjectAccess> projAccess = projectAccessRepository.findByProjectIdAndUserId(projectId, userId);
            effProjectRole = ProjectAccessService.computeEffectiveRole(
                    wsRole,
                    projAccess.map(ProjectAccess::getRole).orElse(wsRole)
            );
        }

        PermissionLevel effEnvPerm = null;
        if (environmentId != null) {
            Optional<EnvironmentAccess> envAccess = environmentAccessRepository.findByEnvironmentIdAndUserId(environmentId, userId);
            effEnvPerm = EnvironmentAccessService.computeEffectivePermission(
                    effProjectRole,
                    envAccess.map(EnvironmentAccess::getPermissionLevel).orElse(null)
            );
        }

        // 8. Evaluate Permission Against Standing Scoped Access
        AccessDecision standingDecision = evaluateStandingPermission(
                permission, wsRole, effProjectRole, effEnvPerm, project, environment, secret
        );
        if (standingDecision.allowed()) {
            return standingDecision;
        }

        // 9. Future Granular Grant Extension Point (Phase 5.2 Hook)
        AccessDecision granularDecision = evaluateGranularGrantExtension(
                workspaceId, projectId, environmentId, secretId, permission, userId
        );
        if (granularDecision != null && granularDecision.allowed()) {
            return granularDecision;
        }

        // 10. Future JIT Elevation Extension Point (Phase 5.3 Hook)
        AccessDecision jitDecision = evaluateJitGrantExtension(
                workspaceId, projectId, environmentId, secretId, permission, userId
        );
        if (jitDecision != null && jitDecision.allowed()) {
            return jitDecision;
        }

        // 11. Default Deny
        return standingDecision;
    }

    /**
     * Asserts that an authenticated actor possesses a specific permission, throwing an ApiException if denied.
     */
    @Transactional(readOnly = true)
    public void checkPermission(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            AccessPermission permission,
            UUID userId
    ) {
        AccessDecision decision = evaluateAccess(workspaceId, projectId, environmentId, secretId, permission, userId);
        if (!decision.allowed()) {
            if (decision.deniedReason() != null && decision.deniedReason().contains("Feature branches are only permitted")) {
                Environment env = environmentId != null ? environmentRepository.findById(environmentId).orElse(null) : null;
                throw ApiException.branchesNotAllowed(
                        env != null ? env.getName() : "Environment",
                        env != null ? env.getEnvType() : EnvType.PRODUCTION
                );
            }
            if (decision.deniedReason() != null && decision.deniedReason().contains("not found")) {
                throw ApiException.notFound(decision.deniedReason());
            }
            throw ApiException.forbidden(decision.deniedReason());
        }
    }

    /**
     * Explains the effective state and lineage for all canonical permissions on a given resource target.
     * Provides the foundation for Access Reviews and Governance Auditing.
     */
    @Transactional(readOnly = true)
    public List<EffectiveAccessExplanation> explainAccess(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            UUID userId
    ) {
        List<EffectiveAccessExplanation> explanations = new ArrayList<>();
        for (AccessPermission perm : AccessPermission.values()) {
            AccessDecision decision = evaluateAccess(workspaceId, projectId, environmentId, secretId, perm, userId);
            explanations.add(EffectiveAccessExplanation.fromDecision(perm, decision));
        }
        return explanations;
    }

    /**
     * Computes the complete set of permissions currently authorized for the actor on the specified target.
     */
    @Transactional(readOnly = true)
    public Set<AccessPermission> getEffectivePermissions(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            UUID userId
    ) {
        Set<AccessPermission> grantedPerms = EnumSet.noneOf(AccessPermission.class);
        for (AccessPermission perm : AccessPermission.values()) {
            AccessDecision decision = evaluateAccess(workspaceId, projectId, environmentId, secretId, perm, userId);
            if (decision.allowed()) {
                grantedPerms.add(perm);
            }
        }
        return grantedPerms;
    }

    private AccessDecision evaluateStandingPermission(
            AccessPermission permission,
            WorkspaceRole wsRole,
            WorkspaceRole effProjectRole,
            PermissionLevel effEnvPerm,
            Project project,
            Environment environment,
            Secret secret
    ) {
        AccessScope targetScope = secret != null ? AccessScope.SECRET
                : environment != null ? AccessScope.ENVIRONMENT
                : project != null ? AccessScope.PROJECT
                : AccessScope.WORKSPACE;

        switch (permission) {
            case SECRET_READ:
                if (effEnvPerm != null) {
                    return AccessDecision.allow(
                            permission,
                            AccessScope.ENVIRONMENT,
                            AccessSourceType.ENVIRONMENT_ACCESS,
                            effEnvPerm.name(),
                            "Environment permission " + effEnvPerm + " permits metadata reading"
                    );
                }
                if (effProjectRole != null) {
                    return AccessDecision.allow(
                            permission,
                            AccessScope.PROJECT,
                            AccessSourceType.PROJECT_ACCESS,
                            effProjectRole.name(),
                            "Project role " + effProjectRole + " permits secret discovery"
                    );
                }
                return AccessDecision.allow(
                        permission,
                        AccessScope.WORKSPACE,
                        AccessSourceType.WORKSPACE_ROLE,
                        wsRole.name(),
                        "Workspace role " + wsRole + " permits metadata reading"
                );

            case SECRET_REVEAL:
                if (wsRole == WorkspaceRole.VIEWER || effProjectRole == WorkspaceRole.VIEWER) {
                    return AccessDecision.deny(
                            permission,
                            targetScope,
                            "VIEWER role is strictly forbidden from revealing secret values"
                    );
                }
                if (effEnvPerm == PermissionLevel.READ) {
                    return AccessDecision.deny(
                            permission,
                            targetScope,
                            "Insufficient permissions: effective permission on this environment is READ only"
                    );
                }
                if (effEnvPerm == PermissionLevel.WRITE || effEnvPerm == PermissionLevel.MANAGE) {
                    return AccessDecision.allow(
                            permission,
                            AccessScope.ENVIRONMENT,
                            AccessSourceType.ENVIRONMENT_ACCESS,
                            effEnvPerm.name(),
                            "Environment " + effEnvPerm + " permission authorizes secret reveal"
                    );
                }
                if (wsRole == WorkspaceRole.OWNER || wsRole == WorkspaceRole.ADMIN) {
                    return AccessDecision.allow(
                            permission,
                            AccessScope.WORKSPACE,
                            AccessSourceType.WORKSPACE_ROLE,
                            wsRole.name(),
                            "Workspace " + wsRole + " role authorizes secret reveal"
                    );
                }
                return AccessDecision.deny(permission, targetScope, "Insufficient permissions to reveal secret value");

            case SECRET_CREATE:
            case SECRET_UPDATE:
            case SECRET_DELETE:
            case SECRET_ROLLBACK:
                if (wsRole == WorkspaceRole.VIEWER || effProjectRole == WorkspaceRole.VIEWER) {
                    return AccessDecision.deny(
                            permission,
                            targetScope,
                            "VIEWER role cannot modify secrets"
                    );
                }
                if (effEnvPerm == PermissionLevel.READ) {
                    return AccessDecision.deny(
                            permission,
                            targetScope,
                            "Insufficient permissions: effective permission on this environment is READ only"
                    );
                }
                if (effEnvPerm == PermissionLevel.WRITE || effEnvPerm == PermissionLevel.MANAGE) {
                    return AccessDecision.allow(
                            permission,
                            AccessScope.ENVIRONMENT,
                            AccessSourceType.ENVIRONMENT_ACCESS,
                            effEnvPerm.name(),
                            "Environment " + effEnvPerm + " permission authorizes secret mutation"
                    );
                }
                if (wsRole == WorkspaceRole.OWNER || wsRole == WorkspaceRole.ADMIN) {
                    return AccessDecision.allow(
                            permission,
                            AccessScope.WORKSPACE,
                            AccessSourceType.WORKSPACE_ROLE,
                            wsRole.name(),
                            "Workspace " + wsRole + " role authorizes secret mutation"
                    );
                }
                return AccessDecision.deny(permission, targetScope, "Insufficient permissions to modify secrets");

            case SECRET_BRANCH:
                if (environment != null && environment.getEnvType() != EnvType.DEVELOPMENT) {
                    return AccessDecision.deny(
                            permission,
                            AccessScope.ENVIRONMENT,
                            "Feature branches are only permitted in DEVELOPMENT environments. Environment [" + environment.getName() + "] is of type " + environment.getEnvType() + "."
                    );
                }
                if (wsRole == WorkspaceRole.VIEWER || effProjectRole == WorkspaceRole.VIEWER || effEnvPerm == PermissionLevel.READ) {
                    return AccessDecision.deny(
                            permission,
                            targetScope,
                            "Insufficient permissions to manage secret branches"
                    );
                }
                return AccessDecision.allow(
                        permission,
                        AccessScope.ENVIRONMENT,
                        AccessSourceType.ENVIRONMENT_ACCESS,
                        effEnvPerm != null ? effEnvPerm.name() : wsRole.name(),
                        "Authorized to branch secrets in DEVELOPMENT environment"
                );

            case ENVIRONMENT_PROMOTE:
                if (wsRole == WorkspaceRole.VIEWER || effProjectRole == WorkspaceRole.VIEWER || effEnvPerm == PermissionLevel.READ) {
                    return AccessDecision.deny(
                            permission,
                            targetScope,
                            "Insufficient permissions: promotion requires WRITE access"
                    );
                }
                return AccessDecision.allow(
                        permission,
                        AccessScope.ENVIRONMENT,
                        AccessSourceType.ENVIRONMENT_ACCESS,
                        effEnvPerm != null ? effEnvPerm.name() : wsRole.name(),
                        "Authorized to promote secrets from environment"
                );

            case ENVIRONMENT_MANAGE:
                if (wsRole.canManageEnvironments() || (effEnvPerm == PermissionLevel.MANAGE)) {
                    return AccessDecision.allow(
                            permission,
                            AccessScope.ENVIRONMENT,
                            AccessSourceType.ENVIRONMENT_ACCESS,
                            effEnvPerm != null ? effEnvPerm.name() : wsRole.name(),
                            "Authorized to manage environment configuration"
                    );
                }
                return AccessDecision.deny(permission, targetScope, "Only OWNER, ADMIN, or MANAGE permission can configure environments");

            case ACCESS_MANAGE:
            case JIT_APPROVE:
            case ACCESS_REVIEW_MANAGE:
                if (wsRole == WorkspaceRole.OWNER || wsRole == WorkspaceRole.ADMIN || (effProjectRole == WorkspaceRole.ADMIN)) {
                    return AccessDecision.allow(
                            permission,
                            AccessScope.WORKSPACE,
                            AccessSourceType.WORKSPACE_ROLE,
                            wsRole.name(),
                            "Workspace governance role authorizes access administration"
                    );
                }
                return AccessDecision.deny(permission, targetScope, "Governance permissions require OWNER or ADMIN authority");

            case JIT_REQUEST:
                return AccessDecision.allow(
                        permission,
                        AccessScope.WORKSPACE,
                        AccessSourceType.WORKSPACE_ROLE,
                        wsRole.name(),
                        "Active workspace members are permitted to submit JIT access requests"
                );

            default:
                return AccessDecision.deny(permission, targetScope, "Default policy denies unmapped permission: " + permission);
        }
    }

    /**
     * Extension point hook for Phase 5.2 Granular Access Grants.
     * Will query granular grants table once implemented in Phase 5.2.
     */
    private AccessDecision evaluateGranularGrantExtension(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            AccessPermission permission,
            UUID userId
    ) {
        // Extension point: Phase 5.2 will inject GranularGrantRepository and evaluate specific grants here.
        return null;
    }

    /**
     * Extension point hook for Phase 5.3 Just-In-Time (JIT) Temporary Access.
     * Will query active, non-expired JIT requests once implemented in Phase 5.3.
     */
    private AccessDecision evaluateJitGrantExtension(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            AccessPermission permission,
            UUID userId
    ) {
        // Extension point: Phase 5.3 will inject JitAccessRequestRepository and evaluate active JIT elevations here.
        return null;
    }
}
