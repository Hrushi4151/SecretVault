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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WorkspaceService workspaceService;

    private UUID userId;
    private UUID orgId;
    private UUID workspaceId;
    private Workspace testWorkspace;
    private WorkspaceMembership testMembership;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();

        testWorkspace = new Workspace(orgId, "Production Core", "prod-core", false);
        testWorkspace.setId(workspaceId);

        testMembership = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.OWNER);
    }

    @Test
    @DisplayName("Should list all workspaces where user holds membership")
    void testGetWorkspacesForUser() {
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(testMembership));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));

        List<WorkspaceResponse> workspaces = workspaceService.getWorkspacesForUser(userId);

        assertEquals(1, workspaces.size());
        assertEquals("Production Core", workspaces.getFirst().name());
        assertEquals(WorkspaceRole.OWNER, workspaces.getFirst().role());
    }

    @Test
    @DisplayName("Should fetch workspace by ID when caller is an active member")
    void testGetWorkspaceByIdSuccess() {
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(testMembership));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));

        WorkspaceResponse response = workspaceService.getWorkspaceById(workspaceId, userId);

        assertNotNull(response);
        assertEquals(workspaceId, response.id());
        assertEquals("prod-core", response.slug());
    }

    @Test
    @DisplayName("Should forbid access to workspace when caller is not a member")
    void testGetWorkspaceByIdForbidden() {
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> workspaceService.getWorkspaceById(workspaceId, userId));
        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    @DisplayName("Should create new workspace and assign caller as OWNER")
    void testCreateWorkspaceSuccess() {
        CreateWorkspaceRequest request = new CreateWorkspaceRequest("Staging Edge", "staging-edge");

        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(testMembership));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(testWorkspace));
        when(workspaceRepository.existsByOrganizationIdAndSlug(orgId, "staging-edge")).thenReturn(false);
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(inv -> {
            Workspace w = inv.getArgument(0);
            w.setId(UUID.randomUUID());
            return w;
        });

        WorkspaceResponse response = workspaceService.createWorkspace(request, userId);

        assertNotNull(response);
        assertEquals("Staging Edge", response.name());
        assertEquals(WorkspaceRole.OWNER, response.role());
        verify(membershipRepository).save(any(WorkspaceMembership.class));
    }

    @Test
    @DisplayName("Should allow OWNER to add a new member to the workspace")
    void testAddMemberSuccess() {
        UUID targetUserId = UUID.randomUUID();
        User targetUser = new User("bob@example.com", "hash", "Bob Builder");
        targetUser.setId(targetUserId);

        AddMemberRequest request = new AddMemberRequest("bob@example.com", WorkspaceRole.DEVELOPER);

        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(testMembership));
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(targetUser));
        when(membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, targetUserId)).thenReturn(false);
        when(membershipRepository.save(any(WorkspaceMembership.class))).thenAnswer(inv -> {
            WorkspaceMembership m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        MemberResponse response = workspaceService.addMember(workspaceId, request, userId);

        assertNotNull(response);
        assertEquals("bob@example.com", response.email());
        assertEquals(WorkspaceRole.DEVELOPER, response.role());
    }

    @Test
    @DisplayName("Should forbid non-admin/owner roles from adding members")
    void testAddMemberForbiddenForDeveloperRole() {
        WorkspaceMembership devMembership = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER);
        AddMemberRequest request = new AddMemberRequest("bob@example.com", WorkspaceRole.DEVELOPER);

        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(devMembership));

        ApiException ex = assertThrows(ApiException.class, () -> workspaceService.addMember(workspaceId, request, userId));
        assertEquals("FORBIDDEN", ex.getCode());
    }
}
