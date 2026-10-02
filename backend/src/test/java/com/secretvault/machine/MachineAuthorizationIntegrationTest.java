package com.secretvault.machine;

import com.secretvault.access.grant.repository.AccessGrantRepository;
import com.secretvault.access.jit.repository.JitAccessRequestRepository;
import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.environment.access.repository.EnvironmentAccessRepository;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.machine.entity.MachineAccessGrant;
import com.secretvault.machine.entity.MachineIdentity;
import com.secretvault.machine.model.MachineStatus;
import com.secretvault.machine.model.MachineType;
import com.secretvault.machine.repository.MachineAccessGrantRepository;
import com.secretvault.machine.repository.MachineIdentityRepository;
import com.secretvault.project.access.repository.ProjectAccessRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class MachineAuthorizationIntegrationTest {

    private WorkspaceRepository workspaceRepository;
    private WorkspaceMembershipRepository membershipRepository;
    private ProjectRepository projectRepository;
    private EnvironmentRepository environmentRepository;
    private SecretRepository secretRepository;
    private ProjectAccessRepository projectAccessRepository;
    private EnvironmentAccessRepository environmentAccessRepository;
    private AccessGrantRepository accessGrantRepository;
    private JitAccessRequestRepository jitRepository;
    private MachineIdentityRepository machineRepository;
    private MachineAccessGrantRepository machineGrantRepository;

    private EffectiveAccessService effectiveAccessService;

    private UUID workspaceA;
    private UUID workspaceB;
    private UUID projectId;
    private UUID envDevId;
    private UUID envProdId;
    private UUID secretDbPasswordId;
    private UUID secretMasterKeyId;

    private Project project;
    private Environment envDev;
    private Environment envProd;
    private MachineIdentity machineA;

    @BeforeEach
    void setUp() {
        workspaceRepository = Mockito.mock(WorkspaceRepository.class);
        membershipRepository = Mockito.mock(WorkspaceMembershipRepository.class);
        projectRepository = Mockito.mock(ProjectRepository.class);
        environmentRepository = Mockito.mock(EnvironmentRepository.class);
        secretRepository = Mockito.mock(SecretRepository.class);
        projectAccessRepository = Mockito.mock(ProjectAccessRepository.class);
        environmentAccessRepository = Mockito.mock(EnvironmentAccessRepository.class);
        accessGrantRepository = Mockito.mock(AccessGrantRepository.class);
        jitRepository = Mockito.mock(JitAccessRequestRepository.class);
        machineRepository = Mockito.mock(MachineIdentityRepository.class);
        machineGrantRepository = Mockito.mock(MachineAccessGrantRepository.class);

        effectiveAccessService = new EffectiveAccessService(
                workspaceRepository,
                membershipRepository,
                projectRepository,
                environmentRepository,
                secretRepository,
                projectAccessRepository,
                environmentAccessRepository,
                accessGrantRepository,
                jitRepository,
                Clock.systemUTC(),
                machineRepository,
                machineGrantRepository
        );

        workspaceA = UUID.randomUUID();
        workspaceB = UUID.randomUUID();
        projectId = UUID.randomUUID();
        envDevId = UUID.randomUUID();
        envProdId = UUID.randomUUID();
        secretDbPasswordId = UUID.randomUUID();
        secretMasterKeyId = UUID.randomUUID();

        project = new Project();
        project.setId(projectId);
        project.setWorkspaceId(workspaceA);
        project.setName("Rally");

        envDev = new Environment();
        envDev.setId(envDevId);
        envDev.setProjectId(projectId);
        envDev.setName("Development");

        envProd = new Environment();
        envProd.setId(envProdId);
        envProd.setProjectId(projectId);
        envProd.setName("Production");

        machineA = new MachineIdentity(workspaceA, "github-rally-ci", "CI runner", MachineType.CI_CD, null, null);
        machineA.setId(UUID.randomUUID());
        machineA.setStatus(MachineStatus.ACTIVE);

        Secret dbSecret = new Secret();
        dbSecret.setId(secretDbPasswordId);
        dbSecret.setEnvironmentId(envDevId);
        dbSecret.setName("DB_PASSWORD");
        dbSecret.setStatus(SecretStatus.ACTIVE);

        when(workspaceRepository.existsById(eq(workspaceA))).thenReturn(true);
        when(workspaceRepository.existsById(eq(workspaceB))).thenReturn(true);
        when(projectRepository.findByIdAndWorkspaceId(eq(projectId), eq(workspaceA))).thenReturn(Optional.of(project));
        when(environmentRepository.findByIdAndProjectId(eq(envDevId), eq(projectId))).thenReturn(Optional.of(envDev));
        when(environmentRepository.findByIdAndProjectId(eq(envProdId), eq(projectId))).thenReturn(Optional.of(envProd));
        when(secretRepository.findById(eq(secretDbPasswordId))).thenReturn(Optional.of(dbSecret));

        when(machineRepository.findByIdAndDeletedAtIsNull(eq(machineA.getId()))).thenReturn(Optional.of(machineA));
        when(machineRepository.findById(eq(machineA.getId()))).thenReturn(Optional.of(machineA));
    }

    @Test
    @DisplayName("Invariant: Machine identity has NO standing access by default")
    void testDefaultNoAccess() {
        when(machineGrantRepository.findByWorkspaceIdAndMachineIdentityId(eq(workspaceA), eq(machineA.getId())))
                .thenReturn(List.of());

        AccessDecision decision = effectiveAccessService.evaluateAccess(
                workspaceA, projectId, envDevId, secretDbPasswordId, AccessPermission.SECRET_READ, machineA.getId());

        assertFalse(decision.allowed());
        assertTrue(decision.deniedReason().contains("No standing grant"));
    }

    @Test
    @DisplayName("Machine with explicit project/environment grant is ALLOWED for secret.read")
    void testScopedGrantReadAllowed() {
        MachineAccessGrant grant = new MachineAccessGrant(
                workspaceA, machineA.getId(), AccessScope.ENVIRONMENT, projectId, envDevId, null, null, "secret.read", "ALLOW", null
        );

        when(machineGrantRepository.findByWorkspaceIdAndMachineIdentityId(eq(workspaceA), eq(machineA.getId())))
                .thenReturn(List.of(grant));

        AccessDecision decision = effectiveAccessService.evaluateAccess(
                workspaceA, projectId, envDevId, secretDbPasswordId, AccessPermission.SECRET_READ, machineA.getId());

        assertTrue(decision.allowed());
    }

    @Test
    @DisplayName("secret.read does NOT implicitly grant secret.reveal")
    void testReadDoesNotGrantReveal() {
        MachineAccessGrant readOnlyGrant = new MachineAccessGrant(
                workspaceA, machineA.getId(), AccessScope.ENVIRONMENT, projectId, envDevId, null, null, "secret.read", "ALLOW", null
        );

        when(machineGrantRepository.findByWorkspaceIdAndMachineIdentityId(eq(workspaceA), eq(machineA.getId())))
                .thenReturn(List.of(readOnlyGrant));

        AccessDecision decision = effectiveAccessService.evaluateAccess(
                workspaceA, projectId, envDevId, secretDbPasswordId, AccessPermission.SECRET_REVEAL, machineA.getId());

        assertFalse(decision.allowed());
    }

    @Test
    @DisplayName("Grant for Development does NOT grant access to Production")
    void testEnvironmentIsolation() {
        MachineAccessGrant devGrant = new MachineAccessGrant(
                workspaceA, machineA.getId(), AccessScope.ENVIRONMENT, projectId, envDevId, null, null, "secret.reveal", "ALLOW", null
        );

        when(machineGrantRepository.findByWorkspaceIdAndMachineIdentityId(eq(workspaceA), eq(machineA.getId())))
                .thenReturn(List.of(devGrant));

        AccessDecision decision = effectiveAccessService.evaluateAccess(
                workspaceA, projectId, envProdId, secretDbPasswordId, AccessPermission.SECRET_REVEAL, machineA.getId());

        assertFalse(decision.allowed());
    }

    @Test
    @DisplayName("Secret restrictions allowlist permits listed key and denies unlisted key")
    void testSecretAllowlist() {
        Secret dbSecret = new Secret();
        dbSecret.setId(secretDbPasswordId);
        dbSecret.setEnvironmentId(envDevId);
        dbSecret.setName("DB_PASSWORD");
        dbSecret.setStatus(SecretStatus.ACTIVE);

        Secret masterSecret = new Secret();
        masterSecret.setId(secretMasterKeyId);
        masterSecret.setEnvironmentId(envDevId);
        masterSecret.setName("MASTER_KEY");
        masterSecret.setStatus(SecretStatus.ACTIVE);

        when(secretRepository.findById(eq(secretDbPasswordId))).thenReturn(Optional.of(dbSecret));
        when(secretRepository.findById(eq(secretMasterKeyId))).thenReturn(Optional.of(masterSecret));

        MachineAccessGrant restrictedGrant = new MachineAccessGrant(
                workspaceA, machineA.getId(), AccessScope.ENVIRONMENT, projectId, envDevId, null, "DB_PASSWORD, API_KEY", "secret.reveal", "ALLOW", null
        );

        when(machineGrantRepository.findByWorkspaceIdAndMachineIdentityId(eq(workspaceA), eq(machineA.getId())))
                .thenReturn(List.of(restrictedGrant));

        // Attempt DB_PASSWORD -> ALLOW
        AccessDecision dbDecision = effectiveAccessService.evaluateAccess(
                workspaceA, projectId, envDevId, secretDbPasswordId, AccessPermission.SECRET_REVEAL, machineA.getId());
        assertTrue(dbDecision.allowed());

        // Attempt MASTER_KEY -> DENY
        AccessDecision masterDecision = effectiveAccessService.evaluateAccess(
                workspaceA, projectId, envDevId, secretMasterKeyId, AccessPermission.SECRET_REVEAL, machineA.getId());
        assertFalse(masterDecision.allowed());
    }

    @Test
    @DisplayName("Tenant Isolation: Workspace A machine CANNOT access Workspace B")
    void testTenantIsolation() {
        AccessDecision decision = effectiveAccessService.evaluateAccess(
                workspaceB, projectId, envDevId, secretDbPasswordId, AccessPermission.SECRET_READ, machineA.getId());

        assertFalse(decision.allowed());
        assertTrue(decision.deniedReason().contains("Cross-tenant access denied"));
    }

    @Test
    @DisplayName("Disabled machine is immediately DENIED for all operations")
    void testDisabledMachineDenied() {
        machineA.setStatus(MachineStatus.DISABLED);

        MachineAccessGrant grant = new MachineAccessGrant(
                workspaceA, machineA.getId(), AccessScope.WORKSPACE, null, null, null, null, "secret.read", "ALLOW", null
        );

        when(machineGrantRepository.findByWorkspaceIdAndMachineIdentityId(eq(workspaceA), eq(machineA.getId())))
                .thenReturn(List.of(grant));

        AccessDecision decision = effectiveAccessService.evaluateAccess(
                workspaceA, projectId, envDevId, secretDbPasswordId, AccessPermission.SECRET_READ, machineA.getId());

        assertFalse(decision.allowed());
        assertTrue(decision.deniedReason().contains("DISABLED"));
    }
}
