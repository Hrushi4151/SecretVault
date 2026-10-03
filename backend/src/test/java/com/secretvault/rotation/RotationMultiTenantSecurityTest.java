package com.secretvault.rotation;

import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.model.AccessSourceType;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.rotation.dto.RotationDtos.*;
import com.secretvault.rotation.engine.RotationValidationEngine;
import com.secretvault.rotation.entity.RotationJob;
import com.secretvault.rotation.entity.RotationPolicy;
import com.secretvault.rotation.model.RotationStatus;
import com.secretvault.rotation.model.RotationStrategy;
import com.secretvault.rotation.model.SecretType;
import com.secretvault.rotation.provider.DefaultCryptoRotator;
import com.secretvault.rotation.provider.SecretRotatorRegistry;
import com.secretvault.rotation.repository.RotationAttemptRepository;
import com.secretvault.rotation.repository.RotationJobRepository;
import com.secretvault.rotation.repository.RotationPolicyRepository;
import com.secretvault.rotation.repository.SecretLeaseRepository;
import com.secretvault.rotation.service.RotationDistributedLock;
import com.secretvault.rotation.service.RotationService;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Phase 12.1 Multi-Tenant Isolation, IDOR, Project/Environment Boundary Hardening Test.
 */
@ExtendWith(MockitoExtension.class)
class RotationMultiTenantSecurityTest {

    @Mock private RotationPolicyRepository policyRepository;
    @Mock private RotationJobRepository jobRepository;
    @Mock private RotationAttemptRepository attemptRepository;
    @Mock private SecretRepository secretRepository;
    @Mock private SecretVersionRepository versionRepository;
    @Mock private EnvironmentRepository environmentRepository;
    @Mock private SecretLeaseRepository leaseRepository;
    @Mock private EncryptionService encryptionService;
    @Mock private RotationValidationEngine validationEngine;
    @Mock private EffectiveAccessService effectiveAccessService;
    @Mock private AuditService auditService;

    private RotationService rotationService;

    private UUID workspaceA;
    private UUID workspaceB;
    private UUID projectA;
    private UUID projectB;
    private UUID envProd;
    private UUID envDev;
    private UUID secretInWorkspaceA;
    private UUID secretInWorkspaceB;
    private UUID actorTenantA;
    private UUID actorTenantB;

    @BeforeEach
    void setUp() {
        workspaceA = UUID.randomUUID();
        workspaceB = UUID.randomUUID();
        projectA = UUID.randomUUID();
        projectB = UUID.randomUUID();
        envProd = UUID.randomUUID();
        envDev = UUID.randomUUID();
        secretInWorkspaceA = UUID.randomUUID();
        secretInWorkspaceB = UUID.randomUUID();
        actorTenantA = UUID.randomUUID();
        actorTenantB = UUID.randomUUID();

        RotationDistributedLock lock = new RotationDistributedLock(null);
        SecretRotatorRegistry registry = new SecretRotatorRegistry(List.of(new DefaultCryptoRotator(new com.secretvault.rotation.engine.SecretGenerationEngine(new com.fasterxml.jackson.databind.ObjectMapper()))));

        rotationService = new RotationService(
                policyRepository, jobRepository, attemptRepository, secretRepository,
                versionRepository, environmentRepository, leaseRepository, encryptionService,
                registry, validationEngine, lock, effectiveAccessService, auditService
        );
    }

    @Test
    @DisplayName("Cross-Tenant Trigger: Tenant B actor cannot trigger rotation on Tenant A secret")
    void testCrossTenantRotationTriggerForbidden() {
        Secret secretA = new Secret();
        secretA.setId(secretInWorkspaceA);
        secretA.setEnvironmentId(envProd);
        secretA.setCurrentVersionNumber(1);

        Environment env = new Environment();
        env.setId(envProd);
        env.setProjectId(projectA);

        when(secretRepository.findById(secretInWorkspaceA)).thenReturn(Optional.of(secretA));
        when(environmentRepository.findById(envProd)).thenReturn(Optional.of(env));
        when(effectiveAccessService.evaluateAccess(eq(workspaceB), eq(projectA), eq(envProd), eq(secretInWorkspaceA), eq(AccessPermission.SECRET_ROTATION_CREATE), eq(actorTenantB)))
                .thenReturn(AccessDecision.deny(AccessPermission.SECRET_ROTATION_CREATE, "Unauthorized cross-tenant attempt"));

        TriggerRotationRequest req = new TriggerRotationRequest(RotationStrategy.MANUAL, "Cross tenant test", false, false);

        assertThatThrownBy(() -> rotationService.triggerRotation(workspaceB, secretInWorkspaceA, req, actorTenantB, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    @DisplayName("Project Isolation: Project A user cannot view or mutate Project B rotation policy")
    void testProjectIsolationRotationPolicy() {
        Secret secretB = new Secret();
        secretB.setId(secretInWorkspaceB);
        secretB.setEnvironmentId(envDev);

        Environment envB = new Environment();
        envB.setId(envDev);
        envB.setProjectId(projectB);

        RotationPolicy policyB = new RotationPolicy();
        policyB.setId(UUID.randomUUID());
        policyB.setSecretId(secretInWorkspaceB);
        policyB.setWorkspaceId(workspaceA);

        when(policyRepository.findBySecretId(secretInWorkspaceB)).thenReturn(Optional.of(policyB));
        when(secretRepository.findById(secretInWorkspaceB)).thenReturn(Optional.of(secretB));
        when(environmentRepository.findById(envDev)).thenReturn(Optional.of(envB));
        when(effectiveAccessService.evaluateAccess(eq(workspaceA), eq(projectB), eq(envDev), eq(secretInWorkspaceB), eq(AccessPermission.SECRET_ROTATION_READ), eq(actorTenantA)))
                .thenReturn(AccessDecision.deny(AccessPermission.SECRET_ROTATION_READ, "Not member of Project B"));

        assertThatThrownBy(() -> rotationService.getPolicy(workspaceA, secretInWorkspaceB, actorTenantA))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    @DisplayName("Environment Boundary: Dev developer cannot execute emergency rotation on Prod secret")
    void testDevRoleCannotRotateProdSecret() {
        Secret prodSecret = new Secret();
        prodSecret.setId(secretInWorkspaceA);
        prodSecret.setEnvironmentId(envProd);

        Environment prodEnv = new Environment();
        prodEnv.setId(envProd);
        prodEnv.setProjectId(projectA);

        when(secretRepository.findById(secretInWorkspaceA)).thenReturn(Optional.of(prodSecret));
        when(environmentRepository.findById(envProd)).thenReturn(Optional.of(prodEnv));
        when(effectiveAccessService.evaluateAccess(eq(workspaceA), eq(projectA), eq(envProd), eq(secretInWorkspaceA), eq(AccessPermission.SECRET_ROTATION_EMERGENCY), eq(actorTenantA)))
                .thenReturn(AccessDecision.deny(AccessPermission.SECRET_ROTATION_EMERGENCY, "Role DEVELOPER lacks SECRET_ROTATION_EMERGENCY in PRODUCTION"));

        TriggerRotationRequest req = new TriggerRotationRequest(RotationStrategy.EMERGENCY, "Emergency request", true, true);

        assertThatThrownBy(() -> rotationService.triggerRotation(workspaceA, secretInWorkspaceA, req, actorTenantA, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    @DisplayName("IDOR Testing: Guessing arbitrary Job UUID across tenants returns forbidden or not found")
    void testIdorJobAccessForbidden() {
        UUID guessedJobId = UUID.randomUUID();
        RotationJob foreignJob = new RotationJob();
        foreignJob.setId(guessedJobId);
        foreignJob.setWorkspaceId(workspaceA);
        foreignJob.setSecretId(secretInWorkspaceA);

        Secret secretA = new Secret();
        secretA.setId(secretInWorkspaceA);
        secretA.setEnvironmentId(envProd);

        Environment env = new Environment();
        env.setId(envProd);
        env.setProjectId(projectA);

        when(jobRepository.findById(guessedJobId)).thenReturn(Optional.of(foreignJob));
        when(secretRepository.findById(secretInWorkspaceA)).thenReturn(Optional.of(secretA));
        when(environmentRepository.findById(envProd)).thenReturn(Optional.of(env));
        when(effectiveAccessService.evaluateAccess(eq(workspaceB), eq(projectA), eq(envProd), eq(secretInWorkspaceA), eq(AccessPermission.SECRET_ROTATION_READ), eq(actorTenantB)))
                .thenReturn(AccessDecision.deny(AccessPermission.SECRET_ROTATION_READ, "Tenant B forbidden"));

        assertThatThrownBy(() -> rotationService.getJob(workspaceB, guessedJobId, actorTenantB))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Access denied");
    }
}
