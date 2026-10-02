package com.secretvault.access.service;

import com.secretvault.access.dto.MemberAccessOverviewResponse;
import com.secretvault.access.dto.UpdateMemberAccessRequest;
import com.secretvault.access.grant.dto.AccessGrantResponse;
import com.secretvault.access.grant.entity.AccessGrant;
import com.secretvault.access.grant.repository.AccessGrantRepository;
import com.secretvault.access.jit.dto.JitAccessRequestResponse;
import com.secretvault.access.jit.entity.JitAccessRequest;
import com.secretvault.access.jit.repository.JitAccessRequestRepository;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.repository.UserRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service managing per-member project, environment, granular grant, and JIT access configuration.
 */
@Service
public class MemberAccessService {

    private static final Logger log = LoggerFactory.getLogger(MemberAccessService.class);

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final ProjectAccessRepository projectAccessRepository;
    private final EnvironmentAccessRepository environmentAccessRepository;
    private final AccessGrantRepository accessGrantRepository;
    private final JitAccessRequestRepository jitRepository;
    private final SecretRepository secretRepository;
    private final AuditService auditService;

    public MemberAccessService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            UserRepository userRepository,
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            ProjectAccessRepository projectAccessRepository,
            EnvironmentAccessRepository environmentAccessRepository,
            AccessGrantRepository accessGrantRepository,
            JitAccessRequestRepository jitRepository,
            SecretRepository secretRepository,
            AuditService auditService
    ) {
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.projectAccessRepository = projectAccessRepository;
        this.environmentAccessRepository = environmentAccessRepository;
        this.accessGrantRepository = accessGrantRepository;
        this.jitRepository = jitRepository;
        this.secretRepository = secretRepository;
        this.auditService = auditService;
    }

    /**
     * Retrieves the complete access profile of a workspace member across all projects and environments.
     */
    @Transactional(readOnly = true)
    public MemberAccessOverviewResponse getMemberAccess(UUID workspaceId, UUID targetUserId, UUID actorUserId) {
        WorkspaceMembership actorMembership = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this workspace"));

        boolean isSelf = targetUserId.equals(actorUserId);
        if (!isSelf && !actorMembership.getRole().canManageWorkspace()) {
            throw ApiException.forbidden("Only OWNER or ADMIN can inspect another member's access profile");
        }

        WorkspaceMembership targetMembership = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> ApiException.notFound("Target member not found in this workspace"));

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> ApiException.notFound("Target user not found"));

        WorkspaceRole wsRole = targetMembership.getRole();

        List<Project> workspaceProjects = projectRepository.findByWorkspaceId(workspaceId);
        if (workspaceProjects == null) {
            workspaceProjects = Collections.emptyList();
        }

        List<ProjectAccess> userProjectAccesses = projectAccessRepository.findByUserId(targetUserId);
        Map<UUID, WorkspaceRole> projectRoleMap = new java.util.HashMap<>();
        if (userProjectAccesses != null) {
            for (ProjectAccess pa : userProjectAccesses) {
                if (pa.getProjectId() != null && pa.getRole() != null) {
                    projectRoleMap.put(pa.getProjectId(), pa.getRole());
                }
            }
        }

        List<EnvironmentAccess> userEnvAccesses = environmentAccessRepository.findByUserId(targetUserId);
        Map<UUID, PermissionLevel> envPermMap = new java.util.HashMap<>();
        if (userEnvAccesses != null) {
            for (EnvironmentAccess ea : userEnvAccesses) {
                if (ea.getEnvironmentId() != null && ea.getPermissionLevel() != null) {
                    envPermMap.put(ea.getEnvironmentId(), ea.getPermissionLevel());
                }
            }
        }

        List<MemberAccessOverviewResponse.ProjectAccessSummary> projectSummaries = new ArrayList<>();

        for (Project project : workspaceProjects) {
            WorkspaceRole explicitProjRole = projectRoleMap.get(project.getId());
            WorkspaceRole effectiveProjRole = ProjectAccessService.computeEffectiveRole(wsRole, explicitProjRole != null ? explicitProjRole : wsRole);

            List<Environment> environments = environmentRepository.findByProjectId(project.getId());
            if (environments == null) {
                environments = Collections.emptyList();
            }

            List<MemberAccessOverviewResponse.EnvironmentAccessSummary> envSummaries = new ArrayList<>();

            for (Environment env : environments) {
                PermissionLevel explicitEnvPerm = envPermMap.get(env.getId());
                boolean isProd = env.getEnvType() == EnvType.PRODUCTION;
                boolean isProtected = env.isProtected() || isProd;
                PermissionLevel effectiveEnvPerm = EnvironmentAccessService.computeEffectivePermission(
                        effectiveProjRole,
                        explicitEnvPerm
                );
                envSummaries.add(new MemberAccessOverviewResponse.EnvironmentAccessSummary(
                        env.getId(),
                        env.getName(),
                        env.getEnvType(),
                        isProd,
                        isProtected,
                        explicitEnvPerm,
                        effectiveEnvPerm
                ));
            }

            projectSummaries.add(new MemberAccessOverviewResponse.ProjectAccessSummary(
                    project.getId(),
                    project.getName(),
                    project.getSlug(),
                    project.getDescription(),
                    explicitProjRole,
                    effectiveProjRole,
                    envSummaries
            ));
        }

        // Granular grants
        List<AccessGrant> grants = accessGrantRepository != null
                ? accessGrantRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                : Collections.emptyList();

        Map<UUID, String> projectNames = new java.util.HashMap<>();
        for (Project p : workspaceProjects) {
            if (p.getId() != null) {
                projectNames.put(p.getId(), p.getName() != null ? p.getName() : "Unnamed Project");
            }
        }

        List<AccessGrantResponse> grantResponses = grants.stream().map(g -> {
            String pName = g.getProjectId() != null ? projectNames.get(g.getProjectId()) : null;
            String eName = g.getEnvironmentId() != null ? environmentRepository.findById(g.getEnvironmentId()).map(Environment::getName).orElse(null) : null;
            String sKey = g.getSecretId() != null ? secretRepository.findById(g.getSecretId()).map(Secret::getName).orElse(null) : null;
            return AccessGrantResponse.fromEntity(g, targetUser.getEmail(), targetUser.getFullName(), pName, eName, sKey);
        }).toList();

        // Active JIT grants
        List<JitAccessRequest> activeJits = jitRepository != null
                ? jitRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId).stream()
                        .filter(JitAccessRequest::isCurrentlyActive)
                        .toList()
                : Collections.emptyList();

        List<JitAccessRequestResponse> jitResponses = activeJits.stream().map(j -> {
            String pName = j.getProjectId() != null ? projectNames.get(j.getProjectId()) : null;
            Environment env = j.getEnvironmentId() != null ? environmentRepository.findById(j.getEnvironmentId()).orElse(null) : null;
            String eName = env != null ? env.getName() : null;
            boolean isProt = env != null && (env.isProtected() || env.getEnvType() == EnvType.PRODUCTION);
            String sKey = j.getSecretId() != null ? secretRepository.findById(j.getSecretId()).map(Secret::getName).orElse(null) : null;
            String approverEmail = j.getApproverId() != null ? userRepository.findById(j.getApproverId()).map(User::getEmail).orElse(null) : null;
            return JitAccessRequestResponse.fromEntity(j, targetUser.getEmail(), targetUser.getFullName(), pName, eName, isProt, sKey, approverEmail);
        }).toList();

        MemberAccessOverviewResponse.MemberSummary memberSummary = new MemberAccessOverviewResponse.MemberSummary(
                targetUser.getId(),
                targetUser.getFullName(),
                targetUser.getEmail(),
                wsRole
        );

        return new MemberAccessOverviewResponse(
                memberSummary,
                projectSummaries,
                grantResponses,
                jitResponses
        );
    }

    /**
     * Atomically and transactionally configures a member's scoped project and environment permissions.
     */
    @Transactional
    public MemberAccessOverviewResponse updateMemberAccess(
            UUID workspaceId,
            UUID targetUserId,
            UpdateMemberAccessRequest request,
            UUID actorUserId
    ) {
        WorkspaceMembership actorMembership = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this workspace"));

        if (!actorMembership.getRole().canManageWorkspace()) {
            throw ApiException.forbidden("Only OWNER or ADMIN can configure member project and environment access");
        }

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> ApiException.notFound("Workspace not found"));

        if (!membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, targetUserId)) {
            throw ApiException.notFound("Target member not found in this workspace");
        }

        // Validate Hierarchy: Ensure all submitted projects belong to this workspace
        if (request.projectConfigs() != null) {
            for (UpdateMemberAccessRequest.ProjectAccessConfig pConfig : request.projectConfigs()) {
                Project project = projectRepository.findById(pConfig.projectId())
                        .orElseThrow(() -> ApiException.notFound("Project [" + pConfig.projectId() + "] not found"));

                if (!project.getWorkspaceId().equals(workspaceId)) {
                    throw ApiException.badRequest("Project [" + project.getName() + "] does not belong to this workspace");
                }

                // Apply or revoke project access
                Optional<ProjectAccess> existingProjAccessOpt = projectAccessRepository.findByProjectIdAndUserId(project.getId(), targetUserId);
                if (pConfig.role() != null) {
                    ProjectAccess projAccess = existingProjAccessOpt.orElseGet(() -> new ProjectAccess(project.getId(), targetUserId, pConfig.role(), actorUserId));
                    projAccess.setRole(pConfig.role());
                    projectAccessRepository.save(projAccess);

                    auditService.recordAudit(
                            workspace.getOrganizationId(),
                            workspaceId,
                            actorUserId,
                            "USER",
                            AuditAction.PROJECT_ACCESS_GRANTED,
                            "PROJECT_ACCESS",
                            projAccess.getId(),
                            null,
                            null,
                            "SUCCESS"
                    );
                } else if (existingProjAccessOpt.isPresent()) {
                    ProjectAccess toDelete = existingProjAccessOpt.get();
                    projectAccessRepository.delete(toDelete);

                    auditService.recordAudit(
                            workspace.getOrganizationId(),
                            workspaceId,
                            actorUserId,
                            "USER",
                            AuditAction.PROJECT_ACCESS_REVOKED,
                            "PROJECT_ACCESS",
                            toDelete.getId(),
                            null,
                            null,
                            "SUCCESS"
                    );
                }

                // Process Environment Access Configs for this Project
                if (pConfig.environmentConfigs() != null) {
                    for (UpdateMemberAccessRequest.EnvironmentAccessConfig envConfig : pConfig.environmentConfigs()) {
                        Environment env = environmentRepository.findById(envConfig.environmentId())
                                .orElseThrow(() -> ApiException.notFound("Environment [" + envConfig.environmentId() + "] not found"));

                        if (!env.getProjectId().equals(project.getId())) {
                            throw ApiException.badRequest("Environment [" + env.getName() + "] does not belong to project [" + project.getName() + "]");
                        }

                        Optional<EnvironmentAccess> existingEnvAccessOpt = environmentAccessRepository.findByEnvironmentIdAndUserId(env.getId(), targetUserId);
                        if (envConfig.permissionLevel() != null) {
                            EnvironmentAccess envAccess = existingEnvAccessOpt.orElseGet(() -> new EnvironmentAccess(env.getId(), targetUserId, envConfig.permissionLevel(), actorUserId));
                            envAccess.setPermissionLevel(envConfig.permissionLevel());
                            environmentAccessRepository.save(envAccess);

                            auditService.recordAudit(
                                    workspace.getOrganizationId(),
                                    workspaceId,
                                    actorUserId,
                                    "USER",
                                    AuditAction.ENVIRONMENT_ACCESS_GRANTED,
                                    "ENVIRONMENT_ACCESS",
                                    envAccess.getId(),
                                    null,
                                    null,
                                    "SUCCESS"
                            );
                        } else if (existingEnvAccessOpt.isPresent()) {
                            EnvironmentAccess toDelete = existingEnvAccessOpt.get();
                            environmentAccessRepository.delete(toDelete);

                            auditService.recordAudit(
                                    workspace.getOrganizationId(),
                                    workspaceId,
                                    actorUserId,
                                    "USER",
                                    AuditAction.ENVIRONMENT_ACCESS_REVOKED,
                                    "ENVIRONMENT_ACCESS",
                                    toDelete.getId(),
                                    null,
                                    null,
                                    "SUCCESS"
                            );
                        }
                    }
                }
            }
        }

        log.info("Successfully updated project and environment access for user [{}] in workspace [{}] by actor [{}]",
                targetUserId, workspaceId, actorUserId);

        return getMemberAccess(workspaceId, targetUserId, actorUserId);
    }
}
