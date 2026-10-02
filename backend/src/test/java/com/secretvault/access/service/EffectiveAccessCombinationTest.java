package com.secretvault.access.service;

import com.secretvault.access.grant.entity.AccessGrant;
import com.secretvault.access.grant.repository.AccessGrantRepository;
import com.secretvault.access.jit.entity.JitAccessRequest;
import com.secretvault.access.jit.entity.JitStatus;
import com.secretvault.access.jit.repository.JitAccessRequestRepository;
import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.model.AccessSourceType;
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
import com.secretvault.secret.entity.SecretStatus;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Parameterized 64-Combination Audit & Hardening Test Suite for Phase 1-5 Access Control.
 * Tests every permutation of:
 * 4 Workspace Roles (OWNER, ADMIN, DEVELOPER, VIEWER)
 * x 4 Project Access Levels (INHERIT, READ, WRITE, MANAGE)
 * x 4 Environment Access Levels (INHERIT, READ, WRITE, MANAGE)
 * = 64 Total Matrix Combinations.
 */
@ExtendWith(MockitoExtension.class)
public class EffectiveAccessCombinationTest {

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
    private AccessGrantRepository accessGrantRepository;

    @Mock
    private JitAccessRequestRepository jitRepository;

    private Clock fixedClock;
    private EffectiveAccessService accessService;

    private UUID workspaceId;
    private UUID projectId;
    private UUID devEnvId;
    private UUID prodEnvId;
    private UUID secretId;
    private UUID userId;

    private Workspace workspace;
    private Project project;
    private Environment devEnvironment;
    private Environment prodEnvironment;
    private Secret secret;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        devEnvId = UUID.randomUUID();
        prodEnvId = UUID.randomUUID();
        secretId = UUID.randomUUID();
        userId = UUID.randomUUID();

        Instant fixedInstant = Instant.parse("2026-10-02T12:00:00Z");
        fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        workspace = new Workspace(UUID.randomUUID(), "Security Corp", "security-corp", true);
        project = new Project(workspaceId, "Core API", "core-api", "API enclave", userId);
        project.setId(projectId);

        devEnvironment = new Environment(projectId, "Development", "development", EnvType.DEVELOPMENT, "Dev tier", false, userId);
        devEnvironment.setId(devEnvId);

        prodEnvironment = new Environment(projectId, "Production", "production", EnvType.PRODUCTION, "Prod tier", true, userId);
        prodEnvironment.setId(prodEnvId);

        secret = new Secret(devEnvId, "DATABASE_URL", "Database connection string", userId);
        secret.setId(secretId);

        accessService = new EffectiveAccessService(
                workspaceRepository,
                membershipRepository,
                projectRepository,
                environmentRepository,
                secretRepository,
                projectAccessRepository,
                environmentAccessRepository,
                accessGrantRepository,
                jitRepository,
                fixedClock
        );
    }

    private void mockResourceHierarchy(Environment targetEnv) {
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(environmentRepository.findByIdAndProjectId(targetEnv.getId(), projectId)).thenReturn(Optional.of(targetEnv));
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
    }

    /**
     * Generates all 64 combinations of (WorkspaceRole, ProjectAccessRole, EnvironmentPermissionLevel).
     */
    static Stream<Arguments> all64Combinations() {
        List<Arguments> combinations = new ArrayList<>();
        WorkspaceRole[] wsRoles = {WorkspaceRole.OWNER, WorkspaceRole.ADMIN, WorkspaceRole.DEVELOPER, WorkspaceRole.VIEWER};
        WorkspaceRole[] projRoles = {null, WorkspaceRole.VIEWER, WorkspaceRole.DEVELOPER, WorkspaceRole.ADMIN}; // null = INHERIT
        PermissionLevel[] envPerms = {null, PermissionLevel.READ, PermissionLevel.WRITE, PermissionLevel.MANAGE}; // null = INHERIT

        for (WorkspaceRole ws : wsRoles) {
            for (WorkspaceRole pr : projRoles) {
                for (PermissionLevel env : envPerms) {
                    combinations.add(Arguments.of(ws, pr, env));
                }
            }
        }
        return combinations.stream();
    }

    @ParameterizedTest(name = "[{index}] WS:{0} + PROJ:{1} + ENV:{2}")
    @MethodSource("all64Combinations")
    @DisplayName("Evaluate full matrix of 64 Role x Project x Environment combinations")
    void testAll64BaseCombinations(WorkspaceRole wsRole, WorkspaceRole projRole, PermissionLevel envPerm) {
        mockResourceHierarchy(devEnvironment);

        // Mock User's standing assignments
        WorkspaceMembership membership = new WorkspaceMembership(workspaceId, userId, wsRole);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(membership));

        if (projRole != null) {
            ProjectAccess pa = new ProjectAccess(projectId, userId, projRole, UUID.randomUUID());
            when(projectAccessRepository.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.of(pa));
        } else {
            when(projectAccessRepository.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.empty());
        }

        if (envPerm != null) {
            EnvironmentAccess ea = new EnvironmentAccess(devEnvId, userId, envPerm, UUID.randomUUID());
            when(environmentAccessRepository.findByEnvironmentIdAndUserId(devEnvId, userId)).thenReturn(Optional.of(ea));
        } else {
            when(environmentAccessRepository.findByEnvironmentIdAndUserId(devEnvId, userId)).thenReturn(Optional.empty());
        }

        // Mathematical Expectations:
        WorkspaceRole effectiveProjRole = ProjectAccessService.computeEffectiveRole(wsRole, projRole != null ? projRole : wsRole);
        PermissionLevel effectiveEnvPerm = EnvironmentAccessService.computeEffectivePermission(effectiveProjRole, envPerm);

        // 1. SECRET_READ
        AccessDecision readDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_READ, userId);
        assertTrue(readDecision.allowed(), "All active workspace members must be allowed to read secret metadata");

        // 2. SECRET_REVEAL
        AccessDecision revealDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_REVEAL, userId);
        boolean expectReveal = (wsRole != WorkspaceRole.VIEWER) &&
                (effectiveProjRole != WorkspaceRole.VIEWER) &&
                (effectiveEnvPerm != PermissionLevel.READ);
        assertEquals(expectReveal, revealDecision.allowed(),
                String.format("SECRET_REVEAL mismatch for WS:%s PROJ:%s ENV:%s (effProj=%s, effEnv=%s)",
                        wsRole, projRole, envPerm, effectiveProjRole, effectiveEnvPerm));

        // 3. SECRET_UPDATE
        AccessDecision updateDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_UPDATE, userId);
        boolean expectUpdate = (wsRole != WorkspaceRole.VIEWER) &&
                (effectiveProjRole != WorkspaceRole.VIEWER) &&
                (effectiveEnvPerm != PermissionLevel.READ);
        assertEquals(expectUpdate, updateDecision.allowed(),
                String.format("SECRET_UPDATE mismatch for WS:%s PROJ:%s ENV:%s (effProj=%s, effEnv=%s)",
                        wsRole, projRole, envPerm, effectiveProjRole, effectiveEnvPerm));

        // 4. SECRET_BRANCH (in DEVELOPMENT env)
        AccessDecision branchDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_BRANCH, userId);
        boolean expectBranch = (wsRole != WorkspaceRole.VIEWER) &&
                (effectiveProjRole != WorkspaceRole.VIEWER) &&
                (effectiveEnvPerm != PermissionLevel.READ);
        assertEquals(expectBranch, branchDecision.allowed(),
                String.format("SECRET_BRANCH mismatch for WS:%s PROJ:%s ENV:%s in Dev Env",
                        wsRole, projRole, envPerm));

        // 5. ENVIRONMENT_PROMOTE
        AccessDecision promoteDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.ENVIRONMENT_PROMOTE, userId);
        boolean expectPromote = (wsRole != WorkspaceRole.VIEWER) &&
                (effectiveProjRole != WorkspaceRole.VIEWER) &&
                (effectiveEnvPerm != PermissionLevel.READ);
        assertEquals(expectPromote, promoteDecision.allowed(),
                String.format("ENVIRONMENT_PROMOTE mismatch for WS:%s PROJ:%s ENV:%s",
                        wsRole, projRole, envPerm));

        // 6. ENVIRONMENT_MANAGE
        AccessDecision envManageDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, null, AccessPermission.ENVIRONMENT_MANAGE, userId);
        boolean expectEnvManage = wsRole.canManageEnvironments() || (effectiveEnvPerm == PermissionLevel.MANAGE);
        assertEquals(expectEnvManage, envManageDecision.allowed(),
                String.format("ENVIRONMENT_MANAGE mismatch for WS:%s PROJ:%s ENV:%s",
                        wsRole, projRole, envPerm));

        // 7. ACCESS_MANAGE
        AccessDecision accessManageDecision = accessService.evaluateAccess(workspaceId, projectId, null, null, AccessPermission.ACCESS_MANAGE, userId);
        boolean expectAccessManage = (wsRole == WorkspaceRole.OWNER || wsRole == WorkspaceRole.ADMIN || effectiveProjRole == WorkspaceRole.ADMIN);
        assertEquals(expectAccessManage, accessManageDecision.allowed(),
                String.format("ACCESS_MANAGE mismatch for WS:%s PROJ:%s ENV:%s",
                        wsRole, projRole, envPerm));
    }

    @Test
    @DisplayName("Critical Scenario: DEVELOPER + Project READ (VIEWER) + Environment INHERIT -> strictly READ only")
    void testScenarioDeveloperProjectViewerEnvironmentInherit() {
        mockResourceHierarchy(devEnvironment);

        WorkspaceMembership membership = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(membership));

        // Project role is explicitly set to VIEWER (READ)
        ProjectAccess pa = new ProjectAccess(projectId, userId, WorkspaceRole.VIEWER, UUID.randomUUID());
        when(projectAccessRepository.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.of(pa));
        when(environmentAccessRepository.findByEnvironmentIdAndUserId(devEnvId, userId)).thenReturn(Optional.empty());

        // 1. Metadata Read -> ALLOW
        AccessDecision readDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_READ, userId);
        assertTrue(readDecision.allowed());
        assertEquals(AccessSourceType.ENVIRONMENT_ACCESS, readDecision.sourceType());

        // 2. Reveal Secret Value -> DENIED
        AccessDecision revealDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_REVEAL, userId);
        assertFalse(revealDecision.allowed());
        assertTrue(revealDecision.deniedReason().contains("VIEWER"));

        // 3. Create Secret -> DENIED
        AccessDecision createDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, null, AccessPermission.SECRET_CREATE, userId);
        assertFalse(createDecision.allowed());

        // 4. Update Secret -> DENIED
        AccessDecision updateDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_UPDATE, userId);
        assertFalse(updateDecision.allowed());

        // 5. Delete Secret -> DENIED
        AccessDecision deleteDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_DELETE, userId);
        assertFalse(deleteDecision.allowed());

        // 6. Rollback Secret -> DENIED
        AccessDecision rollbackDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_ROLLBACK, userId);
        assertFalse(rollbackDecision.allowed());

        // 7. Branch Secret -> DENIED
        AccessDecision branchDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_BRANCH, userId);
        assertFalse(branchDecision.allowed());

        // 8. Promote Secret -> DENIED
        AccessDecision promoteDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.ENVIRONMENT_PROMOTE, userId);
        assertFalse(promoteDecision.allowed());

        // 9. Environment Manage -> DENIED
        AccessDecision envManageDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, null, AccessPermission.ENVIRONMENT_MANAGE, userId);
        assertFalse(envManageDecision.allowed());

        // 10. Access Manage -> DENIED
        AccessDecision accessManageDecision = accessService.evaluateAccess(workspaceId, projectId, null, null, AccessPermission.ACCESS_MANAGE, userId);
        assertFalse(accessManageDecision.allowed());
    }

    @Test
    @DisplayName("Critical Scenario: DEVELOPER + Project WRITE (DEVELOPER) + Environment INHERIT -> normal dev operations allowed")
    void testScenarioDeveloperProjectWriteEnvironmentInherit() {
        mockResourceHierarchy(devEnvironment);

        WorkspaceMembership membership = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(membership));

        ProjectAccess pa = new ProjectAccess(projectId, userId, WorkspaceRole.DEVELOPER, UUID.randomUUID());
        when(projectAccessRepository.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.of(pa));
        when(environmentAccessRepository.findByEnvironmentIdAndUserId(devEnvId, userId)).thenReturn(Optional.empty());

        assertTrue(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_READ, userId).allowed());
        assertTrue(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_REVEAL, userId).allowed());
        assertTrue(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_CREATE, userId).allowed());
        assertTrue(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_UPDATE, userId).allowed());
        assertTrue(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_DELETE, userId).allowed());
        assertTrue(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_ROLLBACK, userId).allowed());
        assertTrue(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_BRANCH, userId).allowed());
        assertTrue(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.ENVIRONMENT_PROMOTE, userId).allowed());

        // Environment & Access management must remain DENIED
        assertFalse(accessService.evaluateAccess(workspaceId, projectId, devEnvId, null, AccessPermission.ENVIRONMENT_MANAGE, userId).allowed());
        assertFalse(accessService.evaluateAccess(workspaceId, projectId, null, null, AccessPermission.ACCESS_MANAGE, userId).allowed());
    }

    @Test
    @DisplayName("Critical Scenario: DEVELOPER + Project MANAGE (ADMIN) + Environment READ -> environment level READ restricts mutations")
    void testScenarioDeveloperProjectManageEnvironmentRead() {
        mockResourceHierarchy(devEnvironment);

        WorkspaceMembership membership = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(membership));

        ProjectAccess pa = new ProjectAccess(projectId, userId, WorkspaceRole.ADMIN, UUID.randomUUID());
        when(projectAccessRepository.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.of(pa));

        EnvironmentAccess ea = new EnvironmentAccess(devEnvId, userId, PermissionLevel.READ, UUID.randomUUID());
        when(environmentAccessRepository.findByEnvironmentIdAndUserId(devEnvId, userId)).thenReturn(Optional.of(ea));

        // Read metadata allowed
        assertTrue(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_READ, userId).allowed());

        // Mutations in this READ environment DENIED
        assertFalse(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_REVEAL, userId).allowed());
        assertFalse(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_UPDATE, userId).allowed());
        assertFalse(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_DELETE, userId).allowed());
        assertFalse(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_ROLLBACK, userId).allowed());
    }

    @Test
    @DisplayName("Critical Scenario: VIEWER + Project MANAGE -> cannot escalate to workspace management")
    void testScenarioViewerProjectManageCannotManageWorkspace() {
        mockResourceHierarchy(devEnvironment);

        WorkspaceMembership membership = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.VIEWER);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(membership));

        // Project role is ADMIN, but workspace role is VIEWER. Effective project role cannot exceed VIEWER.
        ProjectAccess pa = new ProjectAccess(projectId, userId, WorkspaceRole.ADMIN, UUID.randomUUID());
        when(projectAccessRepository.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.of(pa));
        when(environmentAccessRepository.findByEnvironmentIdAndUserId(devEnvId, userId)).thenReturn(Optional.empty());

        // Effective project role is VIEWER
        WorkspaceRole eff = ProjectAccessService.computeEffectiveRole(WorkspaceRole.VIEWER, WorkspaceRole.ADMIN);
        assertEquals(WorkspaceRole.VIEWER, eff);

        assertFalse(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_UPDATE, userId).allowed());
        assertFalse(accessService.evaluateAccess(workspaceId, null, null, null, AccessPermission.ACCESS_MANAGE, userId).allowed());
    }

    @Test
    @DisplayName("Critical Scenario: ADMIN/OWNER + Project READ -> scoped project restriction is authoritative")
    void testScenarioAdminAndOwnerProjectViewerRestricted() {
        mockResourceHierarchy(devEnvironment);

        // Test ADMIN
        WorkspaceMembership adminMem = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.ADMIN);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(adminMem));

        ProjectAccess pa = new ProjectAccess(projectId, userId, WorkspaceRole.VIEWER, UUID.randomUUID());
        when(projectAccessRepository.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.of(pa));
        when(environmentAccessRepository.findByEnvironmentIdAndUserId(devEnvId, userId)).thenReturn(Optional.empty());

        assertFalse(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_UPDATE, userId).allowed(),
                "ADMIN with explicit Project VIEWER must not be allowed to mutate secrets in that project");

        // Test OWNER
        WorkspaceMembership ownerMem = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.OWNER);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(ownerMem));

        assertFalse(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_UPDATE, userId).allowed(),
                "OWNER with explicit Project VIEWER must not be allowed to mutate secrets in that project");
    }

    @Test
    @DisplayName("Granular Grant Override: narrowly scoped grant on Secret A allows update on Secret A only")
    void testGranularGrantNarrowScope() {
        mockResourceHierarchy(devEnvironment);

        WorkspaceMembership membership = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(membership));

        // Project and Environment are READ only
        ProjectAccess pa = new ProjectAccess(projectId, userId, WorkspaceRole.VIEWER, UUID.randomUUID());
        when(projectAccessRepository.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.of(pa));

        EnvironmentAccess ea = new EnvironmentAccess(devEnvId, userId, PermissionLevel.READ, UUID.randomUUID());
        when(environmentAccessRepository.findByEnvironmentIdAndUserId(devEnvId, userId)).thenReturn(Optional.of(ea));

        // Standing access check without grant -> DENIED
        when(accessGrantRepository.findByWorkspaceIdAndUserIdAndPermission(workspaceId, userId, AccessPermission.SECRET_UPDATE))
                .thenReturn(List.of());
        assertFalse(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_UPDATE, userId).allowed());

        // Grant explicit SECRET_UPDATE on secretId
        AccessGrant secretGrant = new AccessGrant(
                workspaceId,
                userId,
                AccessScope.SECRET,
                projectId,
                devEnvId,
                secretId,
                AccessPermission.SECRET_UPDATE,
                UUID.randomUUID()
        );
        when(accessGrantRepository.findByWorkspaceIdAndUserIdAndPermission(workspaceId, userId, AccessPermission.SECRET_UPDATE))
                .thenReturn(List.of(secretGrant));

        // Access on secretId -> ALLOWED via GRANULAR_GRANT
        AccessDecision decision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_UPDATE, userId);
        assertTrue(decision.allowed());
        assertEquals(AccessSourceType.GRANULAR_GRANT, decision.sourceType());

        // Access on a different secret -> DENIED
        UUID differentSecretId = UUID.randomUUID();
        Secret diffSecret = new Secret(devEnvId, "API_KEY", "Different secret", userId);
        diffSecret.setId(differentSecretId);
        when(secretRepository.findById(differentSecretId)).thenReturn(Optional.of(diffSecret));

        AccessDecision otherSecretDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, differentSecretId, AccessPermission.SECRET_UPDATE, userId);
        assertFalse(otherSecretDecision.allowed(), "Granular grant on Secret A must not grant access to Secret B");
    }

    @Test
    @DisplayName("JIT Temporary Elevation: Before, Active, Expired, Revoked lifecycle")
    void testJitAccessLifecycle() {
        mockResourceHierarchy(devEnvironment);

        WorkspaceMembership membership = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(membership));

        // Standing access is READ only
        EnvironmentAccess ea = new EnvironmentAccess(devEnvId, userId, PermissionLevel.READ, UUID.randomUUID());
        when(environmentAccessRepository.findByEnvironmentIdAndUserId(devEnvId, userId)).thenReturn(Optional.of(ea));
        when(projectAccessRepository.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.empty());

        Instant now = fixedClock.instant();

        // 1. Before JIT: DENIED
        when(jitRepository.findActiveGrantsForEnvAndPerm(workspaceId, userId, devEnvId, AccessPermission.SECRET_UPDATE, now))
                .thenReturn(List.of());
        assertFalse(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_UPDATE, userId).allowed());

        // 2. Active JIT: ALLOWED
        JitAccessRequest activeJit = new JitAccessRequest(
                workspaceId,
                userId,
                projectId,
                devEnvId,
                secretId,
                AccessPermission.SECRET_UPDATE,
                60,
                "Emergency prod hotfix"
        );
        activeJit.setStatus(JitStatus.APPROVED);
        activeJit.setApproverId(UUID.randomUUID());
        activeJit.setApprovedAt(now);
        activeJit.setExpiresAt(now.plusSeconds(3600));

        when(jitRepository.findActiveGrantsForEnvAndPerm(workspaceId, userId, devEnvId, AccessPermission.SECRET_UPDATE, now))
                .thenReturn(List.of(activeJit));

        AccessDecision jitDecision = accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_UPDATE, userId);
        assertTrue(jitDecision.allowed());
        assertEquals(AccessSourceType.JIT_GRANT, jitDecision.sourceType());

        // 3. Expired / Revoked JIT: DENIED
        when(jitRepository.findActiveGrantsForEnvAndPerm(workspaceId, userId, devEnvId, AccessPermission.SECRET_UPDATE, now))
                .thenReturn(List.of());
        assertFalse(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_UPDATE, userId).allowed());
    }

    @Test
    @DisplayName("Production Invariant: SECRET_BRANCH is strictly rejected in non-DEVELOPMENT environments")
    void testBranchingRejectedInProduction() {
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.of(project));
        when(environmentRepository.findByIdAndProjectId(prodEnvId, projectId)).thenReturn(Optional.of(prodEnvironment));

        // Even OWNER cannot branch in Production
        WorkspaceMembership membership = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.OWNER);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(membership));

        AccessDecision branchDecision = accessService.evaluateAccess(workspaceId, projectId, prodEnvId, null, AccessPermission.SECRET_BRANCH, userId);
        assertFalse(branchDecision.allowed());
        assertTrue(branchDecision.deniedReason().contains("DEVELOPMENT environments"));
    }

    @Test
    @DisplayName("Security Invariant: Mutations on DELETED secrets are rejected")
    void testDeletedSecretMutationsBlocked() {
        mockResourceHierarchy(devEnvironment);

        secret.setStatus(SecretStatus.DELETED);

        WorkspaceMembership membership = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.OWNER);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(membership));

        assertFalse(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_UPDATE, userId).allowed());
        assertFalse(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_DELETE, userId).allowed());
        assertFalse(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_ROLLBACK, userId).allowed());
        assertFalse(accessService.evaluateAccess(workspaceId, projectId, devEnvId, secretId, AccessPermission.SECRET_BRANCH, userId).allowed());
    }

    @Test
    @DisplayName("IDOR & Tenant Isolation: Cross-workspace or mismatched project/environment rejected")
    void testIdorRejection() {
        // Workspace not found
        when(workspaceRepository.existsById(workspaceId)).thenReturn(false);
        AccessDecision d1 = accessService.evaluateAccess(workspaceId, projectId, devEnvId, null, AccessPermission.SECRET_READ, userId);
        assertFalse(d1.allowed());
        assertEquals("Workspace not found", d1.deniedReason());

        // Project does not belong to workspace
        when(workspaceRepository.existsById(workspaceId)).thenReturn(true);
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(new WorkspaceMembership(workspaceId, userId, WorkspaceRole.OWNER)));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)).thenReturn(Optional.empty());

        AccessDecision d2 = accessService.evaluateAccess(workspaceId, projectId, devEnvId, null, AccessPermission.SECRET_READ, userId);
        assertFalse(d2.allowed());
        assertTrue(d2.deniedReason().contains("Project not found in this workspace"));
    }
}
