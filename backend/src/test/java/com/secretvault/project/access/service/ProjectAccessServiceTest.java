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
class ProjectAccessServiceTest {

    @Mock
    private ProjectAccessRepository projectAccessRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private WorkspaceMembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProjectAccessService projectAccessService;

    private UUID workspaceId;
    private UUID projectId;
    private UUID actorUserId;
    private UUID targetUserId;
    private Project testProject;
    private WorkspaceMembership actorMembership;
    private WorkspaceMembership targetMembership;
    private User targetUser;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        actorUserId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();

        testProject = new Project(workspaceId, "Payments API", "payments-api", "Core payments service", actorUserId);
        testProject.setId(projectId);

        actorMembership = new WorkspaceMembership(workspaceId, actorUserId, WorkspaceRole.OWNER);
        targetMembership = new WorkspaceMembership(workspaceId, targetUserId, WorkspaceRole.DEVELOPER);

        targetUser = new User("dev@example.com", "hash", "Alice Developer");
        targetUser.setId(targetUserId);
    }

    @Test
    @DisplayName("Should grant scoped project access to a workspace member")
    void testGrantProjectAccessSuccess() {
        GrantProjectAccessRequest request = new GrantProjectAccessRequest(targetUserId, WorkspaceRole.DEVELOPER);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId)).thenReturn(Optional.of(actorMembership));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)).thenReturn(Optional.of(targetMembership));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(projectAccessRepository.findByProjectIdAndUserId(projectId, targetUserId)).thenReturn(Optional.empty());
        when(projectAccessRepository.save(any(ProjectAccess.class))).thenAnswer(inv -> {
            ProjectAccess pa = inv.getArgument(0);
            pa.setId(UUID.randomUUID());
            return pa;
        });

        ProjectMemberResponse response = projectAccessService.grantProjectAccess(workspaceId, projectId, request, actorUserId);

        assertNotNull(response);
        assertEquals(targetUserId, response.userId());
        assertEquals(WorkspaceRole.DEVELOPER, response.projectRole());
        assertEquals(WorkspaceRole.DEVELOPER, response.effectiveRole());
    }

    @Test
    @DisplayName("Effective role: child scope cannot elevate beyond workspace role (VIEWER + DEVELOPER = VIEWER)")
    void testEffectiveRoleCannotExceedWorkspaceRole() {
        WorkspaceRole effRole = ProjectAccessService.computeEffectiveRole(WorkspaceRole.VIEWER, WorkspaceRole.DEVELOPER);
        assertEquals(WorkspaceRole.VIEWER, effRole);

        WorkspaceRole effRole2 = ProjectAccessService.computeEffectiveRole(WorkspaceRole.ADMIN, WorkspaceRole.DEVELOPER);
        assertEquals(WorkspaceRole.DEVELOPER, effRole2);

        WorkspaceRole effRole3 = ProjectAccessService.computeEffectiveRole(WorkspaceRole.OWNER, WorkspaceRole.VIEWER);
        assertEquals(WorkspaceRole.VIEWER, effRole3);
    }

    @Test
    @DisplayName("Should forbid project access grant if actor is only a DEVELOPER without admin rights")
    void testGrantProjectAccessForbiddenForDeveloper() {
        WorkspaceMembership devActor = new WorkspaceMembership(workspaceId, actorUserId, WorkspaceRole.DEVELOPER);
        GrantProjectAccessRequest request = new GrantProjectAccessRequest(targetUserId, WorkspaceRole.DEVELOPER);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId)).thenReturn(Optional.of(devActor));

        ApiException ex = assertThrows(ApiException.class, () ->
                projectAccessService.grantProjectAccess(workspaceId, projectId, request, actorUserId));
        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    @DisplayName("Should update scoped project access")
    void testUpdateProjectAccessSuccess() {
        UpdateProjectAccessRequest request = new UpdateProjectAccessRequest(WorkspaceRole.VIEWER);
        ProjectAccess existingAccess = new ProjectAccess(projectId, targetUserId, WorkspaceRole.DEVELOPER, actorUserId);
        existingAccess.setId(UUID.randomUUID());

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId)).thenReturn(Optional.of(actorMembership));
        when(projectAccessRepository.findByProjectIdAndUserId(projectId, targetUserId)).thenReturn(Optional.of(existingAccess));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)).thenReturn(Optional.of(targetMembership));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(projectAccessRepository.save(any(ProjectAccess.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectMemberResponse response = projectAccessService.updateProjectAccess(workspaceId, projectId, targetUserId, request, actorUserId);

        assertNotNull(response);
        assertEquals(WorkspaceRole.VIEWER, response.projectRole());
        assertEquals(WorkspaceRole.VIEWER, response.effectiveRole());
    }

    @Test
    @DisplayName("Should remove scoped project access")
    void testRemoveProjectAccessSuccess() {
        ProjectAccess existingAccess = new ProjectAccess(projectId, targetUserId, WorkspaceRole.DEVELOPER, actorUserId);
        existingAccess.setId(UUID.randomUUID());

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId)).thenReturn(Optional.of(actorMembership));
        when(projectAccessRepository.findByProjectIdAndUserId(projectId, targetUserId)).thenReturn(Optional.of(existingAccess));

        projectAccessService.removeProjectAccess(workspaceId, projectId, targetUserId, actorUserId);

        verify(projectAccessRepository).delete(existingAccess);
    }
}
