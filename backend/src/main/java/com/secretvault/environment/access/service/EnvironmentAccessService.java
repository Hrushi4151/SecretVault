package com.secretvault.environment.access.service;

import com.secretvault.auth.entity.User;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.access.dto.EnvironmentAccessResponse;
import com.secretvault.environment.access.dto.GrantEnvironmentAccessRequest;
import com.secretvault.environment.access.dto.UpdateEnvironmentAccessRequest;
import com.secretvault.environment.access.entity.EnvironmentAccess;
import com.secretvault.environment.access.entity.PermissionLevel;
import com.secretvault.environment.access.repository.EnvironmentAccessRepository;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.access.entity.ProjectAccess;
import com.secretvault.project.access.repository.ProjectAccessRepository;
import com.secretvault.project.access.service.ProjectAccessService;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service managing scoped environment access grants and multi-tier permission inheritance.
 */
@Service
public class EnvironmentAccessService {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentAccessService.class);

    private final EnvironmentAccessRepository environmentAccessRepository;
    private final EnvironmentRepository environmentRepository;
    private final ProjectRepository projectRepository;
    private final ProjectAccessRepository projectAccessRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public EnvironmentAccessService(
            EnvironmentAccessRepository environmentAccessRepository,
            EnvironmentRepository environmentRepository,
            ProjectRepository projectRepository,
            ProjectAccessRepository projectAccessRepository,
            WorkspaceMembershipRepository membershipRepository,
            UserRepository userRepository) {
        this.environmentAccessRepository = environmentAccessRepository;
        this.environmentRepository = environmentRepository;
        this.projectRepository = projectRepository;
        this.projectAccessRepository = projectAccessRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    /**
     * Retrieves all explicit environment access grants.
     */
    @Transactional(readOnly = true)
    public List<EnvironmentAccessResponse> getEnvironmentAccesses(UUID workspaceId, UUID projectId, UUID envId, UUID actorUserId) {
        verifyHierarchy(workspaceId, projectId, envId);

        if (!membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, actorUserId)) {
            throw ApiException.forbidden("You are not a member of this workspace");
        }

        List<EnvironmentAccess> accesses = environmentAccessRepository.findByEnvironmentId(envId);
        List<UUID> userIds = accesses.stream().map(EnvironmentAccess::getUserId).toList();
        Map<UUID, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<UUID, WorkspaceMembership> membershipMap = membershipRepository.findAll().stream()
                .filter(m -> m.getWorkspaceId().equals(workspaceId) && userIds.contains(m.getUserId()))
                .collect(Collectors.toMap(WorkspaceMembership::getUserId, m -> m));
        Map<UUID, ProjectAccess> projectAccessMap = projectAccessRepository.findByProjectId(projectId).stream()
                .collect(Collectors.toMap(ProjectAccess::getUserId, pa -> pa));

        List<EnvironmentAccessResponse> responses = new ArrayList<>();
        for (EnvironmentAccess access : accesses) {
            User user = userMap.get(access.getUserId());
            WorkspaceMembership wsMember = membershipMap.get(access.getUserId());
            WorkspaceRole wsRole = wsMember != null ? wsMember.getRole() : WorkspaceRole.VIEWER;

            ProjectAccess pa = projectAccessMap.get(access.getUserId());
            WorkspaceRole projRole = pa != null ? pa.getRole() : wsRole;
            WorkspaceRole effProjectRole = ProjectAccessService.computeEffectiveRole(wsRole, projRole);

            PermissionLevel effective = computeEffectivePermission(effProjectRole, access.getPermissionLevel());

            responses.add(new EnvironmentAccessResponse(
                    access.getId(),
                    envId,
                    access.getUserId(),
                    user != null ? user.getEmail() : "unknown",
                    user != null ? user.getFullName() : "Unknown User",
                    access.getPermissionLevel(),
                    wsRole,
                    effective,
                    access.getCreatedAt()
            ));
        }

        return responses;
    }

    /**
     * Grants scoped access on an environment to an existing workspace member.
     */
    @Transactional
    public EnvironmentAccessResponse grantEnvironmentAccess(
            UUID workspaceId, UUID projectId, UUID envId, GrantEnvironmentAccessRequest request, UUID actorUserId) {
        verifyManagePermission(workspaceId, projectId, envId, actorUserId);

        WorkspaceMembership targetWsMember = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, request.userId())
                .orElseThrow(() -> ApiException.badRequest("User must be a member of the workspace before granting environment access"));

        User targetUser = userRepository.findById(request.userId())
                .orElseThrow(() -> ApiException.notFound("User not found"));

        EnvironmentAccess access = environmentAccessRepository.findByEnvironmentIdAndUserId(envId, request.userId())
                .orElseGet(() -> new EnvironmentAccess(envId, request.userId(), request.permissionLevel(), actorUserId));

        access.setPermissionLevel(request.permissionLevel());
        access = environmentAccessRepository.save(access);

        Optional<ProjectAccess> projAccess = projectAccessRepository.findByProjectIdAndUserId(projectId, request.userId());
        WorkspaceRole effProjectRole = ProjectAccessService.computeEffectiveRole(
                targetWsMember.getRole(),
                projAccess.map(ProjectAccess::getRole).orElse(targetWsMember.getRole())
        );

        PermissionLevel effective = computeEffectivePermission(effProjectRole, access.getPermissionLevel());

        log.info("Granted environment [{}] access to user [{}] with permission [{}] by actor [{}]",
                envId, request.userId(), request.permissionLevel(), actorUserId);

        return new EnvironmentAccessResponse(
                access.getId(),
                envId,
                access.getUserId(),
                targetUser.getEmail(),
                targetUser.getFullName(),
                access.getPermissionLevel(),
                targetWsMember.getRole(),
                effective,
                access.getCreatedAt()
        );
    }

    /**
     * Updates an existing scoped environment access grant.
     */
    @Transactional
    public EnvironmentAccessResponse updateEnvironmentAccess(
            UUID workspaceId, UUID projectId, UUID envId, UUID userId, UpdateEnvironmentAccessRequest request, UUID actorUserId) {
        verifyManagePermission(workspaceId, projectId, envId, actorUserId);

        EnvironmentAccess access = environmentAccessRepository.findByEnvironmentIdAndUserId(envId, userId)
                .orElseThrow(() -> ApiException.notFound("Environment access grant not found for user"));

        WorkspaceMembership targetWsMember = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> ApiException.badRequest("User is no longer a member of the workspace"));

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        access.setPermissionLevel(request.permissionLevel());
        access = environmentAccessRepository.save(access);

        Optional<ProjectAccess> projAccess = projectAccessRepository.findByProjectIdAndUserId(projectId, userId);
        WorkspaceRole effProjectRole = ProjectAccessService.computeEffectiveRole(
                targetWsMember.getRole(),
                projAccess.map(ProjectAccess::getRole).orElse(targetWsMember.getRole())
        );

        PermissionLevel effective = computeEffectivePermission(effProjectRole, access.getPermissionLevel());

        log.info("Updated environment [{}] access for user [{}] to permission [{}] by actor [{}]",
                envId, userId, request.permissionLevel(), actorUserId);

        return new EnvironmentAccessResponse(
                access.getId(),
                envId,
                access.getUserId(),
                targetUser.getEmail(),
                targetUser.getFullName(),
                access.getPermissionLevel(),
                targetWsMember.getRole(),
                effective,
                access.getCreatedAt()
        );
    }

    /**
     * Revokes scoped environment access for a user.
     */
    @Transactional
    public void removeEnvironmentAccess(UUID workspaceId, UUID projectId, UUID envId, UUID userId, UUID actorUserId) {
        verifyManagePermission(workspaceId, projectId, envId, actorUserId);

        EnvironmentAccess access = environmentAccessRepository.findByEnvironmentIdAndUserId(envId, userId)
                .orElseThrow(() -> ApiException.notFound("Environment access grant not found for user"));

        environmentAccessRepository.delete(access);

        log.info("Removed environment [{}] access for user [{}] by actor [{}]",
                envId, userId, actorUserId);
    }

    /**
     * Computes the effective permission: Effective = Workspace/Project Role ∩ Env Scope.
     * VIEWER can never receive WRITE or MANAGE.
     * DEVELOPER can never receive MANAGE.
     */
    public static PermissionLevel computeEffectivePermission(WorkspaceRole parentEffectiveRole, PermissionLevel envGrant) {
        if (parentEffectiveRole == null || parentEffectiveRole == WorkspaceRole.VIEWER) {
            return PermissionLevel.READ;
        }

        if (parentEffectiveRole == WorkspaceRole.DEVELOPER) {
            if (envGrant == PermissionLevel.MANAGE) {
                return PermissionLevel.WRITE;
            }
            return envGrant != null ? envGrant : PermissionLevel.WRITE;
        }

        // OWNER or ADMIN
        return envGrant != null ? envGrant : PermissionLevel.MANAGE;
    }

    private void verifyHierarchy(UUID workspaceId, UUID projectId, UUID envId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("Project not found"));

        if (!project.getWorkspaceId().equals(workspaceId)) {
            throw ApiException.notFound("Project does not belong to the specified workspace");
        }

        Environment environment = environmentRepository.findById(envId)
                .orElseThrow(() -> ApiException.notFound("Environment not found"));

        if (!environment.getProjectId().equals(projectId)) {
            throw ApiException.notFound("Environment does not belong to the specified project");
        }
    }

    private void verifyManagePermission(UUID workspaceId, UUID projectId, UUID envId, UUID actorUserId) {
        verifyHierarchy(workspaceId, projectId, envId);

        WorkspaceMembership actorMembership = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this workspace"));

        if (!actorMembership.getRole().canManageEnvironments()) {
            throw ApiException.forbidden("Only OWNER or ADMIN can manage environment access");
        }
    }
}
