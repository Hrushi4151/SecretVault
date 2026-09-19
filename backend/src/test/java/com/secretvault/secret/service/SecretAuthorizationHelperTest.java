package com.secretvault.secret.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
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

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecretAuthorizationHelperTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMembershipRepository membershipRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private EffectiveAccessService effectiveAccessService;

    @InjectMocks
    private SecretAuthorizationHelper authHelper;

    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID userId;

    private Workspace workspace;
    private Project project;
    private Environment environment;
    private WorkspaceMembership membership;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        userId = UUID.randomUUID();

        workspace = new Workspace(UUID.randomUUID(), "WS", "ws", true);
        workspace.setId(workspaceId);

        project = new Project(workspaceId, "P", "p", "desc", userId);
        project.setId(projectId);

        environment = new Environment(projectId, "Dev", "dev", EnvType.DEVELOPMENT, "desc", false, userId);
        environment.setId(environmentId);

        membership = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER);
    }

    private void mockHierarchy() {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(membership));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(environmentRepository.findByIdAndProjectId(environmentId, projectId)).thenReturn(Optional.of(environment));
    }

    @Test
    @DisplayName("verifyHierarchy returns populated WorkspaceContext when hierarchy is valid")
    void testVerifyHierarchySuccess() {
        mockHierarchy();

        SecretAuthorizationHelper.WorkspaceContext context = authHelper.verifyHierarchy(workspaceId, projectId, environmentId, userId);

        assertNotNull(context);
        assertEquals(workspace, context.workspace());
        assertEquals(membership, context.membership());
        assertEquals(project, context.project());
        assertEquals(environment, context.environment());
    }

    @Test
    @DisplayName("verifyHierarchyAndReadAccess delegates to EffectiveAccessService checkPermission")
    void testVerifyHierarchyAndReadAccessDelegates() {
        mockHierarchy();

        SecretAuthorizationHelper.WorkspaceContext context = authHelper.verifyHierarchyAndReadAccess(workspaceId, projectId, environmentId, userId);

        assertNotNull(context);
        verify(effectiveAccessService).checkPermission(workspaceId, projectId, environmentId, null, AccessPermission.SECRET_READ, userId);
    }

    @Test
    @DisplayName("verifyHierarchyAndWriteAccess delegates to EffectiveAccessService with SECRET_UPDATE")
    void testVerifyHierarchyAndWriteAccessDelegates() {
        mockHierarchy();

        SecretAuthorizationHelper.WorkspaceContext context = authHelper.verifyHierarchyAndWriteAccess(workspaceId, projectId, environmentId, userId);

        assertNotNull(context);
        verify(effectiveAccessService).checkPermission(workspaceId, projectId, environmentId, null, AccessPermission.SECRET_UPDATE, userId);
    }

    @Test
    @DisplayName("verifyHierarchyAndRevealAccess delegates to EffectiveAccessService with SECRET_REVEAL")
    void testVerifyHierarchyAndRevealAccessDelegates() {
        mockHierarchy();

        SecretAuthorizationHelper.WorkspaceContext context = authHelper.verifyHierarchyAndRevealAccess(workspaceId, projectId, environmentId, userId);

        assertNotNull(context);
        verify(effectiveAccessService).checkPermission(workspaceId, projectId, environmentId, null, AccessPermission.SECRET_REVEAL, userId);
    }

    @Test
    @DisplayName("verifyHierarchyAndBranchWriteAccess delegates to EffectiveAccessService with SECRET_BRANCH")
    void testVerifyHierarchyAndBranchWriteAccessDelegates() {
        mockHierarchy();

        SecretAuthorizationHelper.WorkspaceContext context = authHelper.verifyHierarchyAndBranchWriteAccess(workspaceId, projectId, environmentId, userId);

        assertNotNull(context);
        verify(effectiveAccessService).checkPermission(workspaceId, projectId, environmentId, null, AccessPermission.SECRET_BRANCH, userId);
    }

    @Test
    @DisplayName("buildAad constructs correct standard AAD format")
    void testBuildAad() {
        UUID secretId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();
        String aad = SecretAuthorizationHelper.buildAad(secretId, envId, 3);

        assertEquals(secretId + ":" + envId + ":3", aad);
    }
}
