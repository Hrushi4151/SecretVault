package com.secretvault.access.service;

import com.secretvault.access.dto.EffectiveAccessExplanation;
import com.secretvault.access.grant.entity.AccessGrant;
import com.secretvault.access.grant.repository.AccessGrantRepository;
import com.secretvault.access.jit.entity.JitAccessRequest;
import com.secretvault.access.jit.repository.JitAccessRequestRepository;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.secretvault.access.jit.entity.JitStatus;

/**
 * Authoritative, centralized authorization decision engine for SecretVault.
 * Evaluates tenant, project, and environment boundaries, security invariants,
 * standing RBAC scoping, granular resource grants, and active JIT temporary elevations.
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
    private final AccessGrantRepository accessGrantRepository;
    private final JitAccessRequestRepository jitRepository;
    private final java.time.Clock clock;
    private final com.secretvault.machine.repository.MachineIdentityRepository machineIdentityRepository;
    private final com.secretvault.machine.repository.MachineAccessGrantRepository machineAccessGrantRepository;
    private final com.secretvault.access.privileged.repository.PrivilegedAccessElevationRepository privilegedElevationRepository;

    public EffectiveAccessService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            SecretRepository secretRepository,
            ProjectAccessRepository projectAccessRepository,
            EnvironmentAccessRepository environmentAccessRepository,
            AccessGrantRepository accessGrantRepository,
            JitAccessRequestRepository jitRepository
    ) {
        this(workspaceRepository, membershipRepository, projectRepository, environmentRepository,
             secretRepository, projectAccessRepository, environmentAccessRepository,
             accessGrantRepository, jitRepository, java.time.Clock.systemUTC(), null, null, null);
    }

    public EffectiveAccessService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            SecretRepository secretRepository,
            ProjectAccessRepository projectAccessRepository,
            EnvironmentAccessRepository environmentAccessRepository,
            AccessGrantRepository accessGrantRepository,
            JitAccessRequestRepository jitRepository,
            java.time.Clock clock
    ) {
        this(workspaceRepository, membershipRepository, projectRepository, environmentRepository,
             secretRepository, projectAccessRepository, environmentAccessRepository,
             accessGrantRepository, jitRepository, clock, null, null, null);
    }

    public EffectiveAccessService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            SecretRepository secretRepository,
            ProjectAccessRepository projectAccessRepository,
            EnvironmentAccessRepository environmentAccessRepository,
            AccessGrantRepository accessGrantRepository,
            JitAccessRequestRepository jitRepository,
            java.time.Clock clock,
            com.secretvault.machine.repository.MachineIdentityRepository machineIdentityRepository,
            com.secretvault.machine.repository.MachineAccessGrantRepository machineAccessGrantRepository
    ) {
        this(workspaceRepository, membershipRepository, projectRepository, environmentRepository,
             secretRepository, projectAccessRepository, environmentAccessRepository,
             accessGrantRepository, jitRepository, clock, machineIdentityRepository, machineAccessGrantRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EffectiveAccessService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            SecretRepository secretRepository,
            ProjectAccessRepository projectAccessRepository,
            EnvironmentAccessRepository environmentAccessRepository,
            AccessGrantRepository accessGrantRepository,
            JitAccessRequestRepository jitRepository,
            java.time.Clock clock,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.secretvault.machine.repository.MachineIdentityRepository machineIdentityRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.secretvault.machine.repository.MachineAccessGrantRepository machineAccessGrantRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.secretvault.access.privileged.repository.PrivilegedAccessElevationRepository privilegedElevationRepository
    ) {
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.secretRepository = secretRepository;
        this.projectAccessRepository = projectAccessRepository;
        this.environmentAccessRepository = environmentAccessRepository;
        this.accessGrantRepository = accessGrantRepository;
        this.jitRepository = jitRepository;
        this.clock = clock != null ? clock : java.time.Clock.systemUTC();
        this.machineIdentityRepository = machineIdentityRepository;
        this.machineAccessGrantRepository = machineAccessGrantRepository;
        this.privilegedElevationRepository = privilegedElevationRepository;
    }

    /**
     * Evaluates whether an authenticated actor has a specific permission on a resource target.
     * Executes the authoritative evaluation pipeline for both Human Users and Machine Identities.
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

        // 1. Tenant / Workspace Check
        if (!workspaceRepository.existsById(workspaceId)) {
            return AccessDecision.deny(permission, AccessScope.WORKSPACE, "Workspace not found");
        }

        // Check if actor is a Machine Identity (Phase 9)
        if (machineIdentityRepository != null) {
            Optional<com.secretvault.machine.entity.MachineIdentity> machineOpt = machineIdentityRepository.findByIdAndDeletedAtIsNull(userId);
            if (machineOpt.isPresent()) {
                return evaluateMachineAccess(workspaceId, projectId, environmentId, secretId, permission, machineOpt.get());
            }
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
            if (wsRole != WorkspaceRole.OWNER && wsRole != WorkspaceRole.ADMIN) {
                if (!isUserAuthorizedForProject(workspaceId, project, membership, userId)) {
                    // Actor has no standing authorization for this project.
                    // Still evaluate granular grants or active JIT elevations if any exist specifically for this resource target.
                    AccessDecision granularDecision = evaluateGranularGrantExtension(
                            workspaceId, projectId, environmentId, secretId, permission, userId
                    );
                    if (granularDecision != null && granularDecision.allowed()) {
                        return granularDecision;
                    }
                    AccessDecision jitDecision = evaluateJitGrantExtension(
                            workspaceId, projectId, environmentId, secretId, permission, userId
                    );
                    if (jitDecision != null && jitDecision.allowed()) {
                        return jitDecision;
                    }
                    AccessDecision privDecision = evaluatePrivilegedElevationExtension(
                            workspaceId, projectId, environmentId, secretId, permission, userId
                    );
                    if (privDecision != null && privDecision.allowed()) {
                        return privDecision;
                    }
                    return AccessDecision.deny(permission, AccessScope.PROJECT, "You are not authorized to access this project");
                }
            }

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

        // 9. Granular Access Grants Extension Point (Phase 5.2)
        AccessDecision granularDecision = evaluateGranularGrantExtension(
                workspaceId, projectId, environmentId, secretId, permission, userId
        );
        if (granularDecision != null && granularDecision.allowed()) {
            return granularDecision;
        }

        // 10. Just-In-Time (JIT) Temporary Elevation Extension Point (Phase 5.3)
        AccessDecision jitDecision = evaluateJitGrantExtension(
                workspaceId, projectId, environmentId, secretId, permission, userId
        );
        if (jitDecision != null && jitDecision.allowed()) {
            return jitDecision;
        }

        // 11. Privileged Access & Break-Glass Temporary Elevation Extension Point (Phase 5.8.4)
        AccessDecision privDecision = evaluatePrivilegedElevationExtension(
                workspaceId, projectId, environmentId, secretId, permission, userId
        );
        if (privDecision != null && privDecision.allowed()) {
            return privDecision;
        }

        // 12. Default Deny
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
            case SECURITY_MANAGE:
            case INTEGRATION_MANAGE:
            case DRIFT_MANAGE:
                if (wsRole == WorkspaceRole.OWNER || wsRole == WorkspaceRole.ADMIN || (effProjectRole == WorkspaceRole.ADMIN)) {
                    return AccessDecision.allow(
                            permission,
                            AccessScope.WORKSPACE,
                            AccessSourceType.WORKSPACE_ROLE,
                            wsRole.name(),
                            "Workspace governance role authorizes security, integrations, drift management, and access administration"
                    );
                }
                return AccessDecision.deny(permission, targetScope, "Governance, drift, and integration administration require OWNER or ADMIN authority");

            case INTEGRATION_SYNC:
            case SYNC_EXECUTE:
                if (wsRole == WorkspaceRole.OWNER || wsRole == WorkspaceRole.ADMIN) {
                    return AccessDecision.allow(
                            permission,
                            AccessScope.WORKSPACE,
                            AccessSourceType.WORKSPACE_ROLE,
                            wsRole.name(),
                            "Workspace " + wsRole + " authorizes provider secret synchronization"
                    );
                }
                if (effEnvPerm == PermissionLevel.WRITE || effEnvPerm == PermissionLevel.MANAGE) {
                    return AccessDecision.allow(
                            permission,
                            AccessScope.ENVIRONMENT,
                            AccessSourceType.ENVIRONMENT_ACCESS,
                            effEnvPerm.name(),
                            "Environment " + effEnvPerm + " authorizes provider secret synchronization"
                    );
                }
                return AccessDecision.deny(permission, targetScope, "Provider secret synchronization requires OWNER, ADMIN, or environment WRITE/MANAGE permission");

            case JIT_REQUEST:
            case SECURITY_VIEW:
            case INTEGRATION_VIEW:
            case SYNC_VIEW:
            case SYNC_DRY_RUN:
            case DRIFT_VIEW:
                return AccessDecision.allow(
                        permission,
                        AccessScope.WORKSPACE,
                        AccessSourceType.WORKSPACE_ROLE,
                        wsRole.name(),
                        "Active workspace members are permitted to view integrations, sync, drift status, security posture, and submit JIT requests"
                );

            default:
                return AccessDecision.deny(permission, targetScope, "Default policy denies unmapped permission: " + permission);
        }
    }

    /**
     * Evaluates Granular Access Grants (Phase 5.2).
     */
    private AccessDecision evaluateGranularGrantExtension(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            AccessPermission permission,
            UUID userId
    ) {
        if (accessGrantRepository == null) {
            return null;
        }

        List<AccessGrant> grants = accessGrantRepository.findByWorkspaceIdAndUserIdAndPermission(workspaceId, userId, permission);
        if (grants.isEmpty()) {
            return null;
        }

        for (AccessGrant grant : grants) {
            switch (grant.getScopeType()) {
                case WORKSPACE -> {
                    return AccessDecision.allow(
                            permission,
                            AccessScope.WORKSPACE,
                            AccessSourceType.GRANULAR_GRANT,
                            grant.getId().toString(),
                            "Explicit workspace granular grant authorizes " + permission.getCode()
                    );
                }
                case PROJECT -> {
                    if (projectId != null && projectId.equals(grant.getProjectId())) {
                        return AccessDecision.allow(
                            permission,
                            AccessScope.PROJECT,
                            AccessSourceType.GRANULAR_GRANT,
                            grant.getId().toString(),
                            "Explicit project granular grant authorizes " + permission.getCode()
                        );
                    }
                }
                case ENVIRONMENT -> {
                    if (environmentId != null && environmentId.equals(grant.getEnvironmentId())) {
                        return AccessDecision.allow(
                            permission,
                            AccessScope.ENVIRONMENT,
                            AccessSourceType.GRANULAR_GRANT,
                            grant.getId().toString(),
                            "Explicit environment granular grant authorizes " + permission.getCode()
                        );
                    }
                }
                case SECRET -> {
                    if (secretId != null && secretId.equals(grant.getSecretId())) {
                        return AccessDecision.allow(
                            permission,
                            AccessScope.SECRET,
                            AccessSourceType.GRANULAR_GRANT,
                            grant.getId().toString(),
                            "Explicit secret granular grant authorizes " + permission.getCode()
                        );
                    }
                }
            }
        }
        return null;
    }

    /**
     * Evaluates active Just-In-Time (JIT) Temporary Elevations (Phase 5.3).
     */
    private AccessDecision evaluateJitGrantExtension(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            AccessPermission permission,
            UUID userId
    ) {
        if (jitRepository == null || environmentId == null) {
            return null;
        }

        List<JitAccessRequest> activeGrants = jitRepository.findActiveGrantsForEnvAndPerm(
                workspaceId, userId, environmentId, permission, clock.instant()
        );

        for (JitAccessRequest req : activeGrants) {
            if (req.getSecretId() == null || (secretId != null && req.getSecretId().equals(secretId))) {
                return AccessDecision.allow(
                        permission,
                        AccessScope.ENVIRONMENT,
                        AccessSourceType.JIT_GRANT,
                        req.getId().toString(),
                        "Active JIT temporary elevation authorizes " + permission.getCode() + " until " + req.getExpiresAt()
                );
            }
        }
        return null;
    }

    /**
     * Evaluates active Privileged Access & Break-Glass Temporary Elevations (Phase 5.8.4).
     */
    private AccessDecision evaluatePrivilegedElevationExtension(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            AccessPermission permission,
            UUID userId
    ) {
        if (privilegedElevationRepository == null) {
            return null;
        }

        Instant now = clock.instant();
        List<com.secretvault.access.privileged.entity.PrivilegedAccessElevation> activeElevations =
                privilegedElevationRepository.findActiveElevationsForUserAndPermission(workspaceId, userId, permission, now);

        if (activeElevations.isEmpty()) {
            return null;
        }

        for (com.secretvault.access.privileged.entity.PrivilegedAccessElevation elev : activeElevations) {
            if (!elev.isActive(now)) {
                continue;
            }

            boolean matchesScope = false;
            AccessScope targetScope = AccessScope.WORKSPACE;

            switch (elev.getScopeType()) {
                case WORKSPACE -> {
                    matchesScope = true;
                    targetScope = AccessScope.WORKSPACE;
                }
                case PROJECT -> {
                    if (projectId != null && projectId.equals(elev.getProjectId())) {
                        matchesScope = true;
                        targetScope = AccessScope.PROJECT;
                    }
                }
                case ENVIRONMENT -> {
                    if (environmentId != null && environmentId.equals(elev.getEnvironmentId())) {
                        matchesScope = true;
                        targetScope = AccessScope.ENVIRONMENT;
                    }
                }
                case SECRET -> {
                    if (secretId != null && secretId.equals(elev.getSecretId())) {
                        matchesScope = true;
                        targetScope = AccessScope.SECRET;
                    }
                }
            }

            if (matchesScope) {
                AccessSourceType sourceType = elev.isBreakGlass() ? AccessSourceType.BREAK_GLASS : AccessSourceType.PRIVILEGED_ELEVATION;
                String sourceDesc = (elev.isBreakGlass() ? "Active Break-Glass emergency access" : "Active temporary privileged elevation") +
                        " authorizes " + permission.getCode() + " until " + elev.getExpiresAt();
                return AccessDecision.allow(
                        permission,
                        targetScope,
                        sourceType,
                        elev.getId().toString(),
                        sourceDesc
                );
            }
        }
        return null;
    }

    /**
     * Determines whether a user has authorized access or involvement in a project.
     * Evaluates:
     * 1. Full workspace administrators (OWNER, ADMIN) -> always true.
     * 2. Explicit ProjectAccess record on project -> true.
     * 3. Explicit EnvironmentAccess record on any environment in project -> true.
     * 4. Granular AccessGrant scoped to project or any of its environments -> true.
     * 5. Active unexpired JIT access request for project or any of its environments -> true.
     * 6. If user has any scoped access records elsewhere in this workspace -> false (unassigned project).
     * 7. Standing unrestricted workspace member with no scoped assignments in workspace -> true.
     */
    public boolean isUserAuthorizedForProject(UUID workspaceId, Project project, WorkspaceMembership membership, UUID userId) {
        WorkspaceRole wsRole = membership.getRole();
        if (wsRole == WorkspaceRole.OWNER || wsRole == WorkspaceRole.ADMIN) {
            return true;
        }

        UUID projectId = project.getId();

        // 1. Explicit ProjectAccess
        if (projectAccessRepository != null) {
            Optional<ProjectAccess> projAccess = projectAccessRepository.findByProjectIdAndUserId(projectId, userId);
            if (projAccess.isPresent() && projAccess.get().getRole() != null) {
                return true;
            }
        }

        // 2. Explicit EnvironmentAccess on any project environment
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

        // 3. Granular AccessGrants
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

        // 4. Active unexpired JIT grant
        if (jitRepository != null) {
            List<JitAccessRequest> userJits = jitRepository.findByWorkspaceIdAndUserId(workspaceId, userId);
            Instant now = clock.instant();
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

        // 5. Active unexpired Privileged Elevation / Break-Glass grant
        if (privilegedElevationRepository != null) {
            List<com.secretvault.access.privileged.entity.PrivilegedAccessElevation> userElevations =
                    privilegedElevationRepository.findActiveElevationsForUser(workspaceId, userId, clock.instant());
            boolean hasElevOnProject = userElevations.stream().anyMatch(e ->
                    (e.getScopeType() == com.secretvault.access.privileged.model.PrivilegedPolicyScope.WORKSPACE) ||
                    (e.getProjectId() != null && e.getProjectId().equals(projectId)) ||
                    (e.getEnvironmentId() != null && projectEnvIds.contains(e.getEnvironmentId()))
            );
            if (hasElevOnProject) {
                return true;
            }
        }

        // 6. Scoped check: if user has any scoped records in this workspace, unassigned projects are false
        if (hasAnyScopedAccessInWorkspace(workspaceId, userId)) {
            return false;
        }

        // 7. Standing unrestricted member
        return true;
    }

    public boolean hasAnyScopedAccessInWorkspace(UUID workspaceId, UUID userId) {
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

    private AccessDecision evaluateMachineAccess(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            AccessPermission permission,
            com.secretvault.machine.entity.MachineIdentity machine
    ) {
        // 1. Tenant / Workspace Isolation Check
        if (!machine.getWorkspaceId().equals(workspaceId)) {
            return AccessDecision.deny(permission, AccessScope.WORKSPACE, "Cross-tenant access denied: Machine identity does not belong to this workspace");
        }

        // 2. Machine Lifecycle Status Check
        if (!machine.isUsable(clock.instant())) {
            return AccessDecision.deny(permission, AccessScope.WORKSPACE, "Machine identity is " + machine.getStatus());
        }

        // 3. Secret Auto-resolution / validation
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

        // 4. Environment Auto-resolution
        Environment environment = null;
        if (environmentId != null && projectId == null) {
            Optional<Environment> eOpt = environmentRepository.findById(environmentId);
            if (eOpt.isEmpty()) {
                return AccessDecision.deny(permission, AccessScope.ENVIRONMENT, "Environment not found");
            }
            environment = eOpt.get();
            projectId = environment.getProjectId();
        }

        // 5. Project Boundary Validation
        Project project = null;
        if (projectId != null) {
            Optional<Project> projOpt = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId);
            if (projOpt.isEmpty()) {
                return AccessDecision.deny(permission, AccessScope.PROJECT, "Project not found in this workspace");
            }
            project = projOpt.get();
        }

        // 6. Environment Boundary Validation
        if (environmentId != null && environment == null) {
            Optional<Environment> envOpt = environmentRepository.findByIdAndProjectId(environmentId, projectId);
            if (envOpt.isEmpty()) {
                return AccessDecision.deny(permission, AccessScope.ENVIRONMENT, "Environment not found in this project");
            }
            environment = envOpt.get();
        }

        // 7. Hard Security Invariants Check
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

        // 8. Machine Identity Grants Evaluation
        if (machineAccessGrantRepository != null) {
            List<com.secretvault.machine.entity.MachineAccessGrant> grants =
                    machineAccessGrantRepository.findByWorkspaceIdAndMachineIdentityId(workspaceId, machine.getId());

            String secretKeyName = secret != null ? secret.getName() : null;

            // Check DENY grants first (DENY > ALLOW precedence)
            for (com.secretvault.machine.entity.MachineAccessGrant grant : grants) {
                if ("DENY".equalsIgnoreCase(grant.getEffect()) && matchesMachineGrantScope(grant, projectId, environmentId, secretId, secretKeyName)) {
                    if (isPermissionMatch(grant.getPermission(), permission)) {
                        return AccessDecision.deny(permission, grant.getScopeType(), "Explicitly denied by machine access policy");
                    }
                }
            }

            // Check ALLOW grants
            for (com.secretvault.machine.entity.MachineAccessGrant grant : grants) {
                if ("ALLOW".equalsIgnoreCase(grant.getEffect()) && matchesMachineGrantScope(grant, projectId, environmentId, secretId, secretKeyName)) {
                    if (isPermissionMatch(grant.getPermission(), permission)) {
                        return AccessDecision.allow(
                                permission,
                                grant.getScopeType(),
                                AccessSourceType.GRANULAR_GRANT,
                                grant.getId() != null ? grant.getId().toString() : UUID.randomUUID().toString(),
                                "Granted via Machine Access Grant [" + grant.getScopeType() + "]"
                        );
                    }
                }
            }
        }

        AccessScope targetScope = secret != null ? AccessScope.SECRET
                : environment != null ? AccessScope.ENVIRONMENT
                : project != null ? AccessScope.PROJECT
                : AccessScope.WORKSPACE;

        return AccessDecision.deny(
                permission,
                targetScope,
                "No standing grant: Machine identity [" + machine.getName() + "] does not possess explicit grant for [" + permission.getCode() + "] on this resource target"
        );
    }

    private boolean isPermissionMatch(String grantPerm, AccessPermission requiredPerm) {
        if (grantPerm == null || requiredPerm == null) return false;
        String cleanGrant = grantPerm.trim().toLowerCase();
        String cleanRequired = requiredPerm.getCode().trim().toLowerCase();
        return cleanGrant.equals(cleanRequired) || cleanGrant.equals("*");
    }

    private boolean matchesMachineGrantScope(
            com.secretvault.machine.entity.MachineAccessGrant grant,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            String secretKeyName
    ) {
        return switch (grant.getScopeType()) {
            case WORKSPACE -> true;
            case PROJECT -> projectId != null && projectId.equals(grant.getProjectId());
            case ENVIRONMENT -> {
                if (environmentId == null || !environmentId.equals(grant.getEnvironmentId())) {
                    yield false;
                }
                if (grant.getSecretPattern() != null && !grant.getSecretPattern().isBlank() && secretKeyName != null) {
                    yield isSecretPatternMatch(grant.getSecretPattern(), secretKeyName);
                }
                yield true;
            }
            case SECRET -> {
                if (environmentId == null || !environmentId.equals(grant.getEnvironmentId())) {
                    yield false;
                }
                if (grant.getSecretId() != null && secretId != null && grant.getSecretId().equals(secretId)) {
                    yield true;
                }
                if (grant.getSecretPattern() != null && secretKeyName != null) {
                    yield isSecretPatternMatch(grant.getSecretPattern(), secretKeyName);
                }
                yield false;
            }
        };
    }

    private boolean isSecretPatternMatch(String pattern, String secretName) {
        if (pattern == null || pattern.isBlank() || secretName == null) return true;
        for (String part : pattern.split(",")) {
            String p = part.trim();
            if (p.equals("*") || p.equalsIgnoreCase(secretName)) {
                return true;
            }
            if (p.endsWith("*") && secretName.toLowerCase().startsWith(p.substring(0, p.length() - 1).toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
