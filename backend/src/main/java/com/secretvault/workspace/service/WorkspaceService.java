package com.secretvault.workspace.service;

import com.secretvault.auth.entity.User;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.common.exception.ApiException;
import com.secretvault.workspace.dto.AddMemberRequest;
import com.secretvault.workspace.dto.CreateWorkspaceRequest;
import com.secretvault.workspace.dto.MemberResponse;
import com.secretvault.workspace.dto.WorkspaceResponse;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
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
 * Service managing workspace lifecycle, multi-tenant workspace isolation,
 * membership administration, and role-based access control.
 */
@Service
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            UserRepository userRepository) {
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    /**
     * Lists all workspaces where the given user has an active membership.
     */
    @Transactional(readOnly = true)
    public List<WorkspaceResponse> getWorkspacesForUser(UUID userId) {
        List<WorkspaceMembership> memberships = membershipRepository.findByUserId(userId);
        List<WorkspaceResponse> responses = new ArrayList<>();

        for (WorkspaceMembership membership : memberships) {
            workspaceRepository.findById(membership.getWorkspaceId())
                    .ifPresent(w -> responses.add(WorkspaceResponse.fromEntity(w, membership.getRole())));
        }

        return responses;
    }

    /**
     * Retrieves details for a specific workspace, enforcing that the caller is a verified member.
     */
    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspaceById(UUID workspaceId, UUID userId) {
        WorkspaceMembership membership = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this workspace"));

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> ApiException.notFound("Workspace not found"));

        return WorkspaceResponse.fromEntity(workspace, membership.getRole());
    }

    /**
     * Creates a new workspace inside the caller's organization and assigns the creator as OWNER.
     */
    @Transactional
    public WorkspaceResponse createWorkspace(CreateWorkspaceRequest request, UUID userId) {
        // Find caller's organization from their existing memberships
        List<WorkspaceMembership> existingMemberships = membershipRepository.findByUserId(userId);
        if (existingMemberships.isEmpty()) {
            throw ApiException.forbidden("User has no organization context to create workspaces in");
        }

        // Use the organization ID from the caller's first available workspace
        Workspace firstWorkspace = workspaceRepository.findById(existingMemberships.getFirst().getWorkspaceId())
                .orElseThrow(() -> ApiException.notFound("Organization container not found"));

        UUID organizationId = firstWorkspace.getOrganizationId();

        String slug = StringUtils.hasText(request.slug())
                ? request.slug().toLowerCase(Locale.ROOT)
                : generateSlug(request.name());

        if (workspaceRepository.existsByOrganizationIdAndSlug(organizationId, slug)) {
            throw ApiException.conflict("A workspace with slug '" + slug + "' already exists in this organization");
        }

        Workspace workspace = new Workspace(organizationId, request.name().trim(), slug, false);
        workspace = workspaceRepository.save(workspace);

        WorkspaceMembership membership = new WorkspaceMembership(workspace.getId(), userId, WorkspaceRole.OWNER);
        membershipRepository.save(membership);

        log.info("Created workspace [{}] in organization [{}] for user [{}]",
                workspace.getId(), organizationId, userId);

        return WorkspaceResponse.fromEntity(workspace, WorkspaceRole.OWNER);
    }

    /**
     * Adds an existing registered user to a workspace with a specified RBAC role.
     * Requires OWNER or ADMIN role on the workspace.
     */
    @Transactional
    public MemberResponse addMember(UUID workspaceId, AddMemberRequest request, UUID actorUserId) {
        WorkspaceMembership actorMembership = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this workspace"));

        if (!actorMembership.getRole().canManageWorkspace()) {
            throw ApiException.forbidden("Only OWNER or ADMIN can add members to this workspace");
        }

        String targetEmail = request.email().trim().toLowerCase(Locale.ROOT);
        User targetUser = userRepository.findByEmail(targetEmail)
                .orElseThrow(() -> ApiException.notFound("User with email '" + targetEmail + "' not found"));

        if (membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, targetUser.getId())) {
            throw ApiException.conflict("User is already a member of this workspace");
        }

        WorkspaceMembership newMembership = new WorkspaceMembership(workspaceId, targetUser.getId(), request.role());
        newMembership = membershipRepository.save(newMembership);

        log.info("Added user [{}] with role [{}] to workspace [{}] by actor [{}]",
                targetUser.getId(), request.role(), workspaceId, actorUserId);

        return new MemberResponse(
                newMembership.getId(),
                targetUser.getId(),
                targetUser.getEmail(),
                targetUser.getFullName(),
                newMembership.getRole(),
                newMembership.getCreatedAt()
        );
    }

    /**
     * Lists all members of a workspace. Requires active membership.
     */
    @Transactional(readOnly = true)
    public List<MemberResponse> getWorkspaceMembers(UUID workspaceId, UUID actorUserId) {
        if (!membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, actorUserId)) {
            throw ApiException.forbidden("You are not a member of this workspace");
        }

        List<WorkspaceMembership> memberships = membershipRepository.findByWorkspaceId(workspaceId);
        List<MemberResponse> responses = new ArrayList<>();

        for (WorkspaceMembership membership : memberships) {
            userRepository.findById(membership.getUserId())
                    .ifPresent(u -> responses.add(new MemberResponse(
                            membership.getId(),
                            u.getId(),
                            u.getEmail(),
                            u.getFullName(),
                            membership.getRole(),
                            membership.getCreatedAt()
                    )));
        }

        return responses;
    }

    private String generateSlug(String input) {
        String slug = input.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return StringUtils.hasText(slug) ? slug : "ws-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
