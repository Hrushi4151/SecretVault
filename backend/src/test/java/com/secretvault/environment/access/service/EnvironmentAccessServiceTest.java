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
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
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

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnvironmentAccessServiceTest {

    @Mock
    private EnvironmentAccessRepository environmentAccessRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectAccessRepository projectAccessRepository;

    @Mock
    private WorkspaceMembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private EnvironmentAccessService environmentAccessService;

    private UUID workspaceId;
    private UUID projectId;
    private UUID envId;
    private UUID actorUserId;
    private UUID targetUserId;
    private Project testProject;
    private Environment testEnv;
    private WorkspaceMembership actorMembership;
    private WorkspaceMembership targetMembership;
    private User targetUser;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        envId = UUID.randomUUID();
        actorUserId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();

        testProject = new Project(workspaceId, "Analytics API", "analytics-api", "Analytics service", actorUserId);
        testProject.setId(projectId);

        testEnv = new Environment(projectId, "Production", "production", EnvType.PRODUCTION, "Production cluster", true, actorUserId);
        testEnv.setId(envId);

        actorMembership = new WorkspaceMembership(workspaceId, actorUserId, WorkspaceRole.OWNER);
        targetMembership = new WorkspaceMembership(workspaceId, targetUserId, WorkspaceRole.DEVELOPER);

        targetUser = new User("developer@example.com", "hash", "Bob Developer");
        targetUser.setId(targetUserId);
    }

    @Test
    @DisplayName("Should grant scoped environment access to a member")
    void testGrantEnvironmentAccessSuccess() {
        GrantEnvironmentAccessRequest request = new GrantEnvironmentAccessRequest(targetUserId, PermissionLevel.READ);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
        when(environmentRepository.findById(envId)).thenReturn(Optional.of(testEnv));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId)).thenReturn(Optional.of(actorMembership));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)).thenReturn(Optional.of(targetMembership));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(environmentAccessRepository.findByEnvironmentIdAndUserId(envId, targetUserId)).thenReturn(Optional.empty());
        when(projectAccessRepository.findByProjectIdAndUserId(projectId, targetUserId)).thenReturn(Optional.empty());
        when(environmentAccessRepository.save(any(EnvironmentAccess.class))).thenAnswer(inv -> {
            EnvironmentAccess ea = inv.getArgument(0);
            ea.setId(UUID.randomUUID());
            return ea;
        });

        EnvironmentAccessResponse response = environmentAccessService.grantEnvironmentAccess(
                workspaceId, projectId, envId, request, actorUserId);

        assertNotNull(response);
        assertEquals(targetUserId, response.userId());
        assertEquals(PermissionLevel.READ, response.permissionLevel());
        assertEquals(PermissionLevel.READ, response.effectivePermission());
    }

    @Test
    @DisplayName("Effective permission: VIEWER at workspace level cannot receive WRITE even if grant says WRITE")
    void testEffectivePermissionCannotExceedWorkspaceRole() {
        PermissionLevel eff1 = EnvironmentAccessService.computeEffectivePermission(WorkspaceRole.VIEWER, PermissionLevel.WRITE);
        assertEquals(PermissionLevel.READ, eff1);

        PermissionLevel eff2 = EnvironmentAccessService.computeEffectivePermission(WorkspaceRole.VIEWER, PermissionLevel.MANAGE);
        assertEquals(PermissionLevel.READ, eff2);

        PermissionLevel eff3 = EnvironmentAccessService.computeEffectivePermission(WorkspaceRole.DEVELOPER, PermissionLevel.MANAGE);
        assertEquals(PermissionLevel.WRITE, eff3);

        PermissionLevel eff4 = EnvironmentAccessService.computeEffectivePermission(WorkspaceRole.DEVELOPER, PermissionLevel.READ);
        assertEquals(PermissionLevel.READ, eff4);

        PermissionLevel eff5 = EnvironmentAccessService.computeEffectivePermission(WorkspaceRole.OWNER, PermissionLevel.WRITE);
        assertEquals(PermissionLevel.WRITE, eff5);
    }

    @Test
    @DisplayName("Should reject hierarchy mismatch (e.g. project does not belong to workspace)")
    void testHierarchyMismatchRejected() {
        UUID otherWsId = UUID.randomUUID();
        GrantEnvironmentAccessRequest request = new GrantEnvironmentAccessRequest(targetUserId, PermissionLevel.READ);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));

        ApiException ex = assertThrows(ApiException.class, () ->
                environmentAccessService.grantEnvironmentAccess(otherWsId, projectId, envId, request, actorUserId));
        assertEquals("RESOURCE_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("Should update scoped environment access")
    void testUpdateEnvironmentAccessSuccess() {
        UpdateEnvironmentAccessRequest request = new UpdateEnvironmentAccessRequest(PermissionLevel.READ);
        EnvironmentAccess existingAccess = new EnvironmentAccess(envId, targetUserId, PermissionLevel.WRITE, actorUserId);
        existingAccess.setId(UUID.randomUUID());

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
        when(environmentRepository.findById(envId)).thenReturn(Optional.of(testEnv));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId)).thenReturn(Optional.of(actorMembership));
        when(environmentAccessRepository.findByEnvironmentIdAndUserId(envId, targetUserId)).thenReturn(Optional.of(existingAccess));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)).thenReturn(Optional.of(targetMembership));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(projectAccessRepository.findByProjectIdAndUserId(projectId, targetUserId)).thenReturn(Optional.empty());
        when(environmentAccessRepository.save(any(EnvironmentAccess.class))).thenAnswer(inv -> inv.getArgument(0));

        EnvironmentAccessResponse response = environmentAccessService.updateEnvironmentAccess(
                workspaceId, projectId, envId, targetUserId, request, actorUserId);

        assertNotNull(response);
        assertEquals(PermissionLevel.READ, response.permissionLevel());
        assertEquals(PermissionLevel.READ, response.effectivePermission());
    }

    @Test
    @DisplayName("Should remove scoped environment access")
    void testRemoveEnvironmentAccessSuccess() {
        EnvironmentAccess existingAccess = new EnvironmentAccess(envId, targetUserId, PermissionLevel.WRITE, actorUserId);
        existingAccess.setId(UUID.randomUUID());

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(testProject));
        when(environmentRepository.findById(envId)).thenReturn(Optional.of(testEnv));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, actorUserId)).thenReturn(Optional.of(actorMembership));
        when(environmentAccessRepository.findByEnvironmentIdAndUserId(envId, targetUserId)).thenReturn(Optional.of(existingAccess));

        environmentAccessService.removeEnvironmentAccess(workspaceId, projectId, envId, targetUserId, actorUserId);

        verify(environmentAccessRepository).delete(existingAccess);
    }
}
