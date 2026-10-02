package com.secretvault.access.service;

import com.secretvault.access.dto.MemberAccessOverviewResponse;
import com.secretvault.access.dto.UpdateMemberAccessRequest;
import com.secretvault.access.grant.repository.AccessGrantRepository;
import com.secretvault.access.jit.repository.JitAccessRequestRepository;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.access.entity.EnvironmentAccess;
import com.secretvault.environment.access.entity.PermissionLevel;
import com.secretvault.environment.access.repository.EnvironmentAccessRepository;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.access.entity.ProjectAccess;
import com.secretvault.project.access.repository.ProjectAccessRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.secret.repository.SecretRepository;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberAccessServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private ProjectAccessRepository projectAccessRepository;

    @Mock
    private EnvironmentAccessRepository environmentAccessRepository;

    @Mock
    private AccessGrantRepository accessGrantRepository;

    @Mock
    private JitAccessRequestRepository jitRepository;

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private MemberAccessService memberAccessService;

    private UUID workspaceId;
    private UUID orgId;
    private UUID ownerId;
    private UUID rahulId;
    private UUID projectAId;
    private UUID projectBId;
    private UUID envA1Id;
    private UUID envA2Id;

    private Workspace workspace;
    private User rahulUser;
    private Project projectA;
    private Project projectB;
    private Environment envA1;
    private Environment envA2;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        rahulId = UUID.randomUUID();
        projectAId = UUID.randomUUID();
        projectBId = UUID.randomUUID();
        envA1Id = UUID.randomUUID();
        envA2Id = UUID.randomUUID();

        workspace = new Workspace(orgId, "Acme", "acme", true);
        workspace.setId(workspaceId);

        rahulUser = new User("rahul@example.com", "hash", "Rahul Sharma");
        rahulUser.setId(rahulId);

        projectA = new Project(workspaceId, "E-Commerce", "e-comm", "E-Commerce platform", ownerId);
        projectA.setId(projectAId);

        projectB = new Project(workspaceId, "Payment API", "payment-api", "Payment processing", ownerId);
        projectB.setId(projectBId);

        envA1 = new Environment(projectAId, "Development", "dev", EnvType.DEVELOPMENT, "Dev env", false, ownerId);
        envA1.setId(envA1Id);

        envA2 = new Environment(projectAId, "Production", "prod", EnvType.PRODUCTION, "Prod env", true, ownerId);
        envA2.setId(envA2Id);
    }

    @Test
    @DisplayName("getMemberAccess should retrieve complete project and environment access matrix")
    void testGetMemberAccess_Success() {
        WorkspaceMembership ownerMem = new WorkspaceMembership(workspaceId, ownerId, WorkspaceRole.OWNER);
        WorkspaceMembership rahulMem = new WorkspaceMembership(workspaceId, rahulId, WorkspaceRole.DEVELOPER);

        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, ownerId)).thenReturn(Optional.of(ownerMem));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, rahulId)).thenReturn(Optional.of(rahulMem));
        when(userRepository.findById(rahulId)).thenReturn(Optional.of(rahulUser));

        when(projectRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(projectA, projectB));

        ProjectAccess projAccessA = new ProjectAccess(projectAId, rahulId, WorkspaceRole.VIEWER, ownerId);
        when(projectAccessRepository.findByUserId(rahulId)).thenReturn(List.of(projAccessA));

        EnvironmentAccess envAccessA1 = new EnvironmentAccess(envA1Id, rahulId, PermissionLevel.READ, ownerId);
        when(environmentAccessRepository.findByUserId(rahulId)).thenReturn(List.of(envAccessA1));

        when(environmentRepository.findByProjectId(projectAId)).thenReturn(List.of(envA1, envA2));
        when(environmentRepository.findByProjectId(projectBId)).thenReturn(Collections.emptyList());

        MemberAccessOverviewResponse result = memberAccessService.getMemberAccess(workspaceId, rahulId, ownerId);

        assertNotNull(result);
        assertEquals("Rahul Sharma", result.member().fullName());
        assertEquals("rahul@example.com", result.member().email());
        assertEquals(WorkspaceRole.DEVELOPER, result.member().workspaceRole());

        assertEquals(2, result.projects().size());
        MemberAccessOverviewResponse.ProjectAccessSummary pA = result.projects().stream()
                .filter(p -> p.projectId().equals(projectAId)).findFirst().orElseThrow();
        assertEquals(WorkspaceRole.VIEWER, pA.projectRole());
        assertEquals(WorkspaceRole.VIEWER, pA.effectiveProjectRole()); // min(DEVELOPER, VIEWER) = VIEWER

        assertEquals(2, pA.environments().size());
        MemberAccessOverviewResponse.EnvironmentAccessSummary eA1 = pA.environments().stream()
                .filter(e -> e.environmentId().equals(envA1Id)).findFirst().orElseThrow();
        assertEquals(PermissionLevel.READ, eA1.environmentPermission());
        assertEquals(PermissionLevel.READ, eA1.effectivePermission());
    }

    @Test
    @DisplayName("updateMemberAccess should transactionally configure multi-project access matrix")
    void testUpdateMemberAccess_Success() {
        WorkspaceMembership ownerMem = new WorkspaceMembership(workspaceId, ownerId, WorkspaceRole.OWNER);
        WorkspaceMembership rahulMem = new WorkspaceMembership(workspaceId, rahulId, WorkspaceRole.DEVELOPER);

        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, ownerId)).thenReturn(Optional.of(ownerMem));
        when(membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, rahulId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, rahulId)).thenReturn(Optional.of(rahulMem));
        when(userRepository.findById(rahulId)).thenReturn(Optional.of(rahulUser));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        when(projectRepository.findById(projectAId)).thenReturn(Optional.of(projectA));
        when(environmentRepository.findById(envA1Id)).thenReturn(Optional.of(envA1));
        when(environmentRepository.findById(envA2Id)).thenReturn(Optional.of(envA2));

        when(projectAccessRepository.findByProjectIdAndUserId(projectAId, rahulId)).thenReturn(Optional.empty());
        when(environmentAccessRepository.findByEnvironmentIdAndUserId(envA1Id, rahulId)).thenReturn(Optional.empty());
        when(environmentAccessRepository.findByEnvironmentIdAndUserId(envA2Id, rahulId)).thenReturn(Optional.empty());

        when(projectRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(projectA));
        when(environmentRepository.findByProjectId(projectAId)).thenReturn(List.of(envA1, envA2));

        UpdateMemberAccessRequest request = new UpdateMemberAccessRequest(List.of(
                new UpdateMemberAccessRequest.ProjectAccessConfig(
                        projectAId,
                        WorkspaceRole.DEVELOPER,
                        List.of(
                                new UpdateMemberAccessRequest.EnvironmentAccessConfig(envA1Id, PermissionLevel.WRITE),
                                new UpdateMemberAccessRequest.EnvironmentAccessConfig(envA2Id, PermissionLevel.READ)
                        )
                )
        ));

        MemberAccessOverviewResponse response = memberAccessService.updateMemberAccess(workspaceId, rahulId, request, ownerId);

        assertNotNull(response);
        verify(projectAccessRepository, times(1)).save(any(ProjectAccess.class));
        verify(environmentAccessRepository, times(2)).save(any(EnvironmentAccess.class));
    }

    @Test
    @DisplayName("updateMemberAccess should reject cross-workspace project hierarchy tampering")
    void testUpdateMemberAccess_CrossWorkspaceProject_Rejected() {
        WorkspaceMembership ownerMem = new WorkspaceMembership(workspaceId, ownerId, WorkspaceRole.OWNER);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, ownerId)).thenReturn(Optional.of(ownerMem));
        when(membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, rahulId)).thenReturn(true);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        Project foreignProject = new Project(UUID.randomUUID(), "Foreign Project", "foreign-proj", null, ownerId);
        foreignProject.setId(UUID.randomUUID());
        when(projectRepository.findById(foreignProject.getId())).thenReturn(Optional.of(foreignProject));

        UpdateMemberAccessRequest request = new UpdateMemberAccessRequest(List.of(
                new UpdateMemberAccessRequest.ProjectAccessConfig(foreignProject.getId(), WorkspaceRole.DEVELOPER, Collections.emptyList())
        ));

        ApiException ex = assertThrows(ApiException.class, () ->
                memberAccessService.updateMemberAccess(workspaceId, rahulId, request, ownerId)
        );
        assertTrue(ex.getMessage().contains("does not belong to this workspace"));
    }

    @Test
    @DisplayName("updateMemberAccess should reject unauthorized DEVELOPER caller")
    void testUpdateMemberAccess_UnauthorizedCaller_Forbidden() {
        WorkspaceMembership devMem = new WorkspaceMembership(workspaceId, rahulId, WorkspaceRole.DEVELOPER);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, rahulId)).thenReturn(Optional.of(devMem));

        UpdateMemberAccessRequest request = new UpdateMemberAccessRequest(Collections.emptyList());

        ApiException ex = assertThrows(ApiException.class, () ->
                memberAccessService.updateMemberAccess(workspaceId, rahulId, request, rahulId)
        );
        assertEquals("Only OWNER or ADMIN can configure member project and environment access", ex.getMessage());
    }

    @Test
    @DisplayName("CASE 1 & 2: Target member with zero explicit project access still retrieves ALL workspace projects with INHERIT")
    void testGetMemberAccess_ZeroExplicitAccess_ShowsAllProjectsWithInherit() {
        WorkspaceMembership ownerMem = new WorkspaceMembership(workspaceId, ownerId, WorkspaceRole.OWNER);
        WorkspaceMembership rahulMem = new WorkspaceMembership(workspaceId, rahulId, WorkspaceRole.DEVELOPER);

        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, ownerId)).thenReturn(Optional.of(ownerMem));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, rahulId)).thenReturn(Optional.of(rahulMem));
        when(userRepository.findById(rahulId)).thenReturn(Optional.of(rahulUser));

        when(projectRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(projectA, projectB));
        when(projectAccessRepository.findByUserId(rahulId)).thenReturn(Collections.emptyList());
        when(environmentAccessRepository.findByUserId(rahulId)).thenReturn(Collections.emptyList());

        when(environmentRepository.findByProjectId(projectAId)).thenReturn(List.of(envA1, envA2));
        when(environmentRepository.findByProjectId(projectBId)).thenReturn(Collections.emptyList());

        MemberAccessOverviewResponse result = memberAccessService.getMemberAccess(workspaceId, rahulId, ownerId);

        assertNotNull(result);
        assertEquals(2, result.projects().size());

        for (MemberAccessOverviewResponse.ProjectAccessSummary p : result.projects()) {
            assertNull(p.projectRole(), "Project role override should be null (INHERIT)");
            assertEquals(WorkspaceRole.DEVELOPER, p.effectiveProjectRole(), "Effective role should inherit workspace role");
        }

        MemberAccessOverviewResponse.ProjectAccessSummary pA = result.projects().stream()
                .filter(p -> p.projectId().equals(projectAId)).findFirst().orElseThrow();
        assertEquals(2, pA.environments().size());
        for (MemberAccessOverviewResponse.EnvironmentAccessSummary envSummary : pA.environments()) {
            assertNull(envSummary.environmentPermission(), "Environment permission override should be null (INHERIT)");
            assertEquals(PermissionLevel.WRITE, envSummary.effectivePermission(), "Effective env permission should inherit DEVELOPER WRITE");
        }
    }

    @Test
    @DisplayName("CASE 4: Workspace has no projects returns empty project list without error")
    void testGetMemberAccess_ZeroProjectsInWorkspace() {
        WorkspaceMembership ownerMem = new WorkspaceMembership(workspaceId, ownerId, WorkspaceRole.OWNER);
        WorkspaceMembership rahulMem = new WorkspaceMembership(workspaceId, rahulId, WorkspaceRole.DEVELOPER);

        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, ownerId)).thenReturn(Optional.of(ownerMem));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, rahulId)).thenReturn(Optional.of(rahulMem));
        when(userRepository.findById(rahulId)).thenReturn(Optional.of(rahulUser));

        when(projectRepository.findByWorkspaceId(workspaceId)).thenReturn(Collections.emptyList());
        when(projectAccessRepository.findByUserId(rahulId)).thenReturn(Collections.emptyList());
        when(environmentAccessRepository.findByUserId(rahulId)).thenReturn(Collections.emptyList());

        MemberAccessOverviewResponse result = memberAccessService.getMemberAccess(workspaceId, rahulId, ownerId);

        assertNotNull(result);
        assertTrue(result.projects().isEmpty());
    }

    @Test
    @DisplayName("CASE 5: Non-existent target user or non-member throws not found")
    void testGetMemberAccess_TargetMemberNotFound_ThrowsNotFound() {
        WorkspaceMembership ownerMem = new WorkspaceMembership(workspaceId, ownerId, WorkspaceRole.OWNER);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, ownerId)).thenReturn(Optional.of(ownerMem));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, rahulId)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () ->
                memberAccessService.getMemberAccess(workspaceId, rahulId, ownerId)
        );
        assertEquals("Target member not found in this workspace", ex.getMessage());
    }
}
