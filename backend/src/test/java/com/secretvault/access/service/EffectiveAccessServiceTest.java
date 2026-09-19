package com.secretvault.access.service;

import com.secretvault.access.dto.EffectiveAccessExplanation;
import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.model.AccessSourceType;
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
import com.secretvault.secret.entity.Secret;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EffectiveAccessServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMembershipRepository membershipRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private ProjectAccessRepository projectAccessRepository;

    @Mock
    private EnvironmentAccessRepository environmentAccessRepository;

    @Mock
    private com.secretvault.access.grant.repository.AccessGrantRepository accessGrantRepository;

    @Mock
    private com.secretvault.access.jit.repository.JitAccessRequestRepository jitRepository;

    @InjectMocks
    private EffectiveAccessService accessService;

    private UUID workspaceId;
    private UUID projectId;
    private UUID devEnvId;
    private UUID stagingEnvId;
    private UUID prodEnvId;
    private UUID secretId;
    private UUID userId;

    private Workspace workspace;
    private Project project;
    private Environment devEnvironment;
    private Environment stagingEnvironment;
    private Environment prodEnvironment;
    private Secret secret;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        devEnvId = UUID.randomUUID();
        stagingEnvId = UUID.randomUUID();
        prodEnvId = UUID.randomUUID();
        secretId = UUID.randomUUID();
        userId = UUID.randomUUID();

        workspace = new Workspace(UUID.randomUUID(), "Test Workspace", "test-ws", true);
        workspace.setId(workspaceId);

        project = new Project(workspaceId, "Payment API", "payment-api", "Core billing", userId);
        project.setId(projectId);

        devEnvironment = new Environment(projectId, "Development", "dev", EnvType.DEVELOPMENT, "Dev tier", false, userId);
        devEnvironment.setId(devEnvId);

        stagingEnvironment = new Environment(projectId, "Staging", "staging", EnvType.STAGING, "Staging tier", false, userId);
        stagingEnvironment.setId(stagingEnvId);

        prodEnvironment = new Environment(projectId, "Production", "prod", EnvType.PRODUCTION, "Prod tier", true, userId);
        prodEnvironment.setId(prodEnvId);

        secret = new Secret(devEnvId, "STRIPE_KEY", "Payment gateway key", userId);
        try {
            var idField = Secret.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(secret, secretId);
        } catch (Exception ignored) {
        }
    }

    private void mockValidHierarchy(WorkspaceRole role) {
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(new WorkspaceMembership(workspaceId, userId, role)));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId))
                .thenReturn(Optional.of(project));
        when(environmentRepository.findByIdAndProjectId(devEnvId, projectId))
                .thenReturn(Optional.of(devEnvironment));
    }

    @Test
    @DisplayName("Tenant Isolation: Non-member is denied access fail-closed")
    void testNonMemberDenied() {
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.empty());

        AccessDecision decision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, null, AccessPermission.SECRET_READ, userId);

        assertFalse(decision.allowed());
        assertEquals(AccessScope.WORKSPACE, decision.scope());
        assertTrue(decision.deniedReason().contains("not an active member"));
    }

    @Test
    @DisplayName("Project Isolation: Mismatched project in another workspace is rejected")
    void testCrossWorkspaceProjectRejected() {
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER)));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.empty());

        AccessDecision decision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, null, AccessPermission.SECRET_READ, userId);

        assertFalse(decision.allowed());
        assertEquals(AccessScope.PROJECT, decision.scope());
        assertTrue(decision.deniedReason().contains("Project not found in this workspace"));
    }

    @Test
    @DisplayName("Environment Isolation: Mismatched environment in another project is rejected")
    void testCrossProjectEnvironmentRejected() {
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER)));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId))
                .thenReturn(Optional.of(project));
        when(environmentRepository.findByIdAndProjectId(devEnvId, projectId)).thenReturn(Optional.empty());

        AccessDecision decision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, null, AccessPermission.SECRET_READ, userId);

        assertFalse(decision.allowed());
        assertEquals(AccessScope.ENVIRONMENT, decision.scope());
        assertTrue(decision.deniedReason().contains("Environment not found in this project"));
    }

    @Test
    @DisplayName("Developer Role in Dev Environment: Full CRUD and Branching allowed, governance denied")
    void testDeveloperRoleInDevEnvironment() {
        mockValidHierarchy(WorkspaceRole.DEVELOPER);

        AccessDecision readDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, null, AccessPermission.SECRET_READ, userId);
        assertTrue(readDecision.allowed());

        AccessDecision revealDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, null, AccessPermission.SECRET_REVEAL, userId);
        assertTrue(revealDecision.allowed());

        AccessDecision updateDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, null, AccessPermission.SECRET_UPDATE, userId);
        assertTrue(updateDecision.allowed());

        AccessDecision branchDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, null, AccessPermission.SECRET_BRANCH, userId);
        assertTrue(branchDecision.allowed());

        AccessDecision govDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, null, AccessPermission.ACCESS_MANAGE, userId);
        assertFalse(govDecision.allowed());
        assertTrue(govDecision.deniedReason().contains("require OWNER or ADMIN"));
    }

    @Test
    @DisplayName("Viewer Role: Strictly denied secret.reveal and mutations, allowed secret.read")
    void testViewerRoleStrictlyRestricted() {
        mockValidHierarchy(WorkspaceRole.VIEWER);

        AccessDecision readDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, null, AccessPermission.SECRET_READ, userId);
        assertTrue(readDecision.allowed());

        AccessDecision revealDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, null, AccessPermission.SECRET_REVEAL, userId);
        assertFalse(revealDecision.allowed());
        assertTrue(revealDecision.deniedReason().contains("VIEWER role is strictly forbidden from revealing"));

        AccessDecision createDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, null, AccessPermission.SECRET_CREATE, userId);
        assertFalse(createDecision.allowed());

        AccessDecision branchDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, null, AccessPermission.SECRET_BRANCH, userId);
        assertFalse(branchDecision.allowed());
    }

    @Test
    @DisplayName("Hard Invariant: SECRET_BRANCH is unconditionally rejected in STAGING and PRODUCTION")
    void testBranchPolicyHardeningInStagingAndProduction() {
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(new WorkspaceMembership(workspaceId, userId, WorkspaceRole.ADMIN)));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId))
                .thenReturn(Optional.of(project));
        when(environmentRepository.findByIdAndProjectId(prodEnvId, projectId))
                .thenReturn(Optional.of(prodEnvironment));

        AccessDecision prodBranch = accessService.evaluateAccess(workspaceId, projectId, prodEnvId, null, AccessPermission.SECRET_BRANCH, userId);
        assertFalse(prodBranch.allowed());
        assertTrue(prodBranch.deniedReason().contains("Feature branches are only permitted in DEVELOPMENT"));

        when(environmentRepository.findByIdAndProjectId(stagingEnvId, projectId))
                .thenReturn(Optional.of(stagingEnvironment));

        AccessDecision stagingBranch = accessService.evaluateAccess(workspaceId, projectId, stagingEnvId, null, AccessPermission.SECRET_BRANCH, userId);
        assertFalse(stagingBranch.allowed());
        assertTrue(stagingBranch.deniedReason().contains("Feature branches are only permitted in DEVELOPMENT"));
    }

    @Test
    @DisplayName("Scoped Access Override: ProjectAccess role downgrades workspace role")
    void testScopedProjectAccessDowngrade() {
        mockValidHierarchy(WorkspaceRole.ADMIN);

        // Project access explicitly scopes user to VIEWER on this project
        when(projectAccessRepository.findByProjectIdAndUserId(projectId, userId))
                .thenReturn(Optional.of(new ProjectAccess(projectId, userId, WorkspaceRole.VIEWER, userId)));

        AccessDecision revealDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, null, AccessPermission.SECRET_REVEAL, userId);
        assertFalse(revealDecision.allowed());
        assertTrue(revealDecision.deniedReason().contains("VIEWER role is strictly forbidden"));
    }

    @Test
    @DisplayName("Scoped Environment Access: READ-only permission level prevents mutations and reveals")
    void testScopedEnvironmentAccessReadOnly() {
        mockValidHierarchy(WorkspaceRole.DEVELOPER);

        when(environmentAccessRepository.findByEnvironmentIdAndUserId(devEnvId, userId))
                .thenReturn(Optional.of(new EnvironmentAccess(devEnvId, userId, PermissionLevel.READ, userId)));

        AccessDecision revealDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, null, AccessPermission.SECRET_REVEAL, userId);
        assertFalse(revealDecision.allowed());
        assertTrue(revealDecision.deniedReason().contains("READ only"));

        AccessDecision updateDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, null, AccessPermission.SECRET_UPDATE, userId);
        assertFalse(updateDecision.allowed());
    }

    @Test
    @DisplayName("Why Access Lineage: explainAccess returns detailed rationale for all permissions")
    void testExplainAccessLineage() {
        mockValidHierarchy(WorkspaceRole.DEVELOPER);

        List<EffectiveAccessExplanation> explanations = accessService.explainAccess(workspaceId, projectId, devEnvId, null, userId);

        assertNotNull(explanations);
        assertEquals(AccessPermission.values().length, explanations.size());

        EffectiveAccessExplanation readExp = explanations.stream()
                .filter(e -> e.permission() == AccessPermission.SECRET_READ)
                .findFirst().orElseThrow();
        assertTrue(readExp.granted());
        assertEquals(AccessSourceType.ENVIRONMENT_ACCESS, readExp.sourceType());
        assertEquals(AccessScope.ENVIRONMENT, readExp.scope());

        // Governance permission denied for DEVELOPER
        EffectiveAccessExplanation govExp = explanations.stream()
                .filter(e -> e.permission() == AccessPermission.ACCESS_MANAGE)
                .findFirst().orElseThrow();
        assertFalse(govExp.granted());
        assertNotNull(govExp.deniedReason());

        // Test workspace-level explanation (no project/environment context)
        List<EffectiveAccessExplanation> wsExplanations = accessService.explainAccess(workspaceId, null, null, null, userId);
        EffectiveAccessExplanation wsReadExp = wsExplanations.stream()
                .filter(e -> e.permission() == AccessPermission.SECRET_READ)
                .findFirst().orElseThrow();
        assertTrue(wsReadExp.granted());
        assertEquals(AccessSourceType.WORKSPACE_ROLE, wsReadExp.sourceType());
        assertEquals(AccessScope.WORKSPACE, wsReadExp.scope());
    }

    @Test
    @DisplayName("getEffectivePermissions returns exact set of authorized permissions")
    void testGetEffectivePermissions() {
        mockValidHierarchy(WorkspaceRole.DEVELOPER);

        Set<AccessPermission> perms = accessService.getEffectivePermissions(workspaceId, projectId, devEnvId, null, userId);

        assertNotNull(perms);
        assertTrue(perms.contains(AccessPermission.SECRET_READ));
        assertTrue(perms.contains(AccessPermission.SECRET_REVEAL));
        assertTrue(perms.contains(AccessPermission.SECRET_CREATE));
        assertTrue(perms.contains(AccessPermission.SECRET_UPDATE));
        assertTrue(perms.contains(AccessPermission.SECRET_DELETE));
        assertTrue(perms.contains(AccessPermission.SECRET_ROLLBACK));
        assertTrue(perms.contains(AccessPermission.SECRET_BRANCH));
        assertTrue(perms.contains(AccessPermission.ENVIRONMENT_PROMOTE));
        assertTrue(perms.contains(AccessPermission.JIT_REQUEST));

        assertFalse(perms.contains(AccessPermission.ACCESS_MANAGE));
        assertFalse(perms.contains(AccessPermission.JIT_APPROVE));
        assertFalse(perms.contains(AccessPermission.ACCESS_REVIEW_MANAGE));
    }

    @Test
    @DisplayName("checkPermission throws ApiException.forbidden when access is denied")
    void testCheckPermissionThrowsExceptionOnDenial() {
        mockValidHierarchy(WorkspaceRole.VIEWER);

        ApiException ex = assertThrows(ApiException.class, () ->
                accessService.checkPermission(workspaceId, projectId, devEnvId, null, AccessPermission.SECRET_REVEAL, userId));

        assertEquals("FORBIDDEN", ex.getCode());
        assertTrue(ex.getMessage().contains("VIEWER role is strictly forbidden"));
    }

    @Test
    @DisplayName("Edge Case: Auto-resolves environment and project when only secretId is supplied")
    void testAutoResolveHierarchyFromSecretId() {
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER)));
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(environmentRepository.findById(devEnvId)).thenReturn(Optional.of(devEnvironment));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));

        AccessDecision decision = accessService.evaluateAccess(workspaceId, null, null, secretId, AccessPermission.SECRET_READ, userId);

        assertTrue(decision.allowed());
        assertEquals(AccessScope.ENVIRONMENT, decision.scope());
    }

    @Test
    @DisplayName("Edge Case: Deleted secret rejects update, delete, rollback, and branch mutations")
    void testDeletedSecretRejectsMutations() {
        secret.setStatus(com.secretvault.secret.entity.SecretStatus.DELETED);

        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(new WorkspaceMembership(workspaceId, userId, WorkspaceRole.ADMIN)));
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(environmentRepository.findByIdAndProjectId(devEnvId, projectId)).thenReturn(Optional.of(devEnvironment));

        AccessDecision updateDec = accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_UPDATE, userId);
        assertFalse(updateDec.allowed());
        assertTrue(updateDec.deniedReason().contains("deleted secret"));

        AccessDecision rollbackDec = accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_ROLLBACK, userId);
        assertFalse(rollbackDec.allowed());
        assertTrue(rollbackDec.deniedReason().contains("deleted secret"));
    }

    @Test
    @DisplayName("Edge Case: Cross-hierarchy secret in mismatched environment is denied")
    void testCrossHierarchySecretMismatchedEnvironment() {
        UUID wrongEnvId = UUID.randomUUID();

        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(new WorkspaceMembership(workspaceId, userId, WorkspaceRole.ADMIN)));
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));

        AccessDecision decision = accessService.evaluateAccess(workspaceId, projectId, wrongEnvId, secretId, AccessPermission.SECRET_READ, userId);
        assertFalse(decision.allowed());
        assertTrue(decision.deniedReason().contains("does not belong to the specified environment"));
    }

    @Test
    @DisplayName("Edge Case: Null permission or unauthenticated actor fails closed")
    void testNullInputsFailClosed() {
        AccessDecision nullPerm = accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, null, userId);
        assertFalse(nullPerm.allowed());

        AccessDecision nullActor = accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_READ, null);
        assertFalse(nullActor.allowed());

        AccessDecision nullWs = accessService.evaluateAccess(null, projectId, devEnvId, secretId, AccessPermission.SECRET_READ, userId);
        assertFalse(nullWs.allowed());
    }
}
