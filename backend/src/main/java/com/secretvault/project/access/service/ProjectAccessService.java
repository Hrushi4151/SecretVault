package com.secretvault.project.access.service;

import com.secretvault.auth.entity.User;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.common.exception.ApiException;
import com.secretvault.project.access.dto.GrantProjectAccessRequest;
import com.secretvault.project.access.dto.ProjectMemberResponse;
import com.secretvault.project.access.dto.UpdateProjectAccessRequest;
import com.secretvault.project.access.entity.ProjectAccess;
import com.secretvault.project.access.repository.ProjectAccessRepository;
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
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service managing fine-grained scoped project access grants.
 */
@Service
public class ProjectAccessService {

    private static final Logger log = LoggerFactory.getLogger(ProjectAccessService.class);

    private final ProjectAccessRepository projectAccessRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public ProjectAccessService(
            ProjectAccessRepository projectAccessRepository,
            ProjectRepository projectRepository,
            WorkspaceMembershipRepository membershipRepository,
            UserRepository userRepository) {
        this.projectAccessRepository = projectAccessRepository;
        this.projectRepository = projectRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    /**
     * Retrieves all explicit scoped access grants for a project.
     */
    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> getProjectMembers(UUID workspaceId, UUID projectId, UUID actorUserId) {
        verifyWorkspaceAndProject(workspaceId, projectId);

        if (!membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, actorUserId)) {
            throw ApiException.forbidden("You are not a member of this workspace");
        }

        List<ProjectAccess> accesses = projectAccessRepository.findByProjectId(projectId);
        List<UUID> userIds = accesses.stream().map(ProjectAccess::getUserId).toList();
        Map<UUID, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<UUID, WorkspaceMembership> membershipMap = membershipRepository.findAll().stream()
                .filter(m -> m.getWorkspaceId().equals(workspaceId) && userIds.contains(m.getUserId()))
                .collect(Collectors.toMap(WorkspaceMembership::getUserId, m -> m));

        List<ProjectMemberResponse> responses = new ArrayList<>();
        for (ProjectAccess access : accesses) {
            User user = userMap.get(access.getUserId());
            WorkspaceMembership wsMember = membershipMap.get(access.getUserId());
            WorkspaceRole wsRole = wsMember != null ? wsMember.getRole() : WorkspaceRole.VIEWER;
            WorkspaceRole effectiveRole = computeEffectiveRole(wsRole, access.getRole());

            responses.add(new ProjectMemberResponse(
                    access.getId(),
                    projectId,
                    access.getUserId(),
                    user != null ? user.getEmail() : "unknown",
                    user != null ? user.getFullName() : "Unknown User",
                    access.getRole(),
                    wsRole,
                    effectiveRole,
                    access.getCreatedAt()
            ));
        }

        return responses;
    }

    /**
     * Grants scoped access on a project to an existing workspace member.
     */
    @Transactional
    public ProjectMemberResponse grantProjectAccess(UUID workspaceId, UUID projectId, GrantProjectAccessRequest request, UUID actorUserId) {
        verifyManagePermission(workspaceId, projectId, actorUserId);

        // Verify target user is in the workspace
        WorkspaceMembership targetWsMember = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, request.userId())
                .orElseThrow(() -> ApiException.badRequest("User must be a member of the workspace before granting project access"));

        User targetUser = userRepository.findById(request.userId())
                .orElseThrow(() -> ApiException.notFound("User not found"));

        // Upsert or create project access
        ProjectAccess access = projectAccessRepository.findByProjectIdAndUserId(projectId, request.userId())
                .orElseGet(() -> new ProjectAccess(projectId, request.userId(), request.role(), actorUserId));

        access.setRole(request.role());
        access = projectAccessRepository.save(access);

        WorkspaceRole effectiveRole = computeEffectiveRole(targetWsMember.getRole(), access.getRole());

        log.info("Granted project [{}] access to user [{}] with role [{}] by actor [{}]",
                projectId, request.userId(), request.role(), actorUserId);

        return new ProjectMemberResponse(
                access.getId(),
                projectId,
                access.getUserId(),
                targetUser.getEmail(),
                targetUser.getFullName(),
                access.getRole(),
                targetWsMember.getRole(),
                effectiveRole,
                access.getCreatedAt()
        );
    }

    /**
     * Updates an existing scoped project access grant.
     */
    @Transactional
    public ProjectMemberResponse updateProjectAccess(UUID workspaceId, UUID projectId, UUID userId, UpdateProjectAccessRequest request, UUID actorUserId) {
        verifyManagePermission(workspaceId, projectId, actorUserId);

        ProjectAccess access = projectAccessRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> ApiException.notFound("Project access grant not found for user"));

        WorkspaceMembership targetWsMember = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> ApiException.badRequest("User is no longer a member of the workspace"));

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        access.setRole(request.role());
        access = projectAccessRepository.save(access);

        WorkspaceRole effectiveRole = computeEffectiveRole(targetWsMember.getRole(), access.getRole());

        log.info("Updated project [{}] access for user [{}] to role [{}] by actor [{}]",
                projectId, userId, request.role(), actorUserId);

        return new ProjectMemberResponse(
                access.getId(),
                projectId,
                access.getUserId(),
                targetUser.getEmail(),
                targetUser.getFullName(),
                access.getRole(),
                targetWsMember.getRole(),
                effectiveRole,
                access.getCreatedAt()
        );
    }

    /**
     * Revokes scoped project access for a user.
     */
    @Transactional
    public void removeProjectAccess(UUID workspaceId, UUID projectId, UUID userId, UUID actorUserId) {
        verifyManagePermission(workspaceId, projectId, actorUserId);

        ProjectAccess access = projectAccessRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> ApiException.notFound("Project access grant not found for user"));

        projectAccessRepository.delete(access);

        log.info("Removed project [{}] access for user [{}] by actor [{}]",
                projectId, userId, actorUserId);
    }

    /**
     * Computes the effective role: effective = min(workspaceRole, projectRole).
     * A child project role cannot grant more privilege than the workspace role.
     */
    public static WorkspaceRole computeEffectiveRole(WorkspaceRole workspaceRole, WorkspaceRole projectRole) {
        if (workspaceRole == null) return WorkspaceRole.VIEWER;
        if (projectRole == null) return workspaceRole;

        // Compare ordinal rankings (lower ordinal = higher privilege: OWNER=0, ADMIN=1, DEVELOPER=2, VIEWER=3)
        // Taking the max ordinal gives the most restricted / safe permission level
        int restrictedOrdinal = Math.max(workspaceRole.ordinal(), projectRole.ordinal());
        return WorkspaceRole.values()[restrictedOrdinal];
    }

    private void verifyWorkspaceAndProject(UUID workspaceId, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("Project not found"));

        if (!project.getWorkspaceId().equals(workspaceId)) {
            throw ApiException.notFound("Project does not belong to the specified workspace");
        }
    }

    private void verifyManagePermission(UUID workspaceId, UUID projectId, UUID actorUserId) {
        verifyWorkspaceAndProject(workspaceId, projectId);

        WorkspaceMembership actorMembership = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this workspace"));

        if (!actorMembership.getRole().canManageProjects()) {
            throw ApiException.forbidden("Only OWNER or ADMIN can manage project access");
        }
    }
}
