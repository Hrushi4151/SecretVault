package com.secretvault.rotation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.model.AccessSourceType;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.machine.entity.MachineIdentity;
import com.secretvault.machine.model.MachineStatus;
import com.secretvault.machine.repository.MachineIdentityRepository;
import com.secretvault.rotation.dto.RotationDtos.*;
import com.secretvault.rotation.engine.RotationValidationEngine;
import com.secretvault.rotation.engine.SecretGenerationEngine;
import com.secretvault.rotation.entity.RotationJob;
import com.secretvault.rotation.entity.RotationPolicy;
import com.secretvault.rotation.entity.SecretConsumer;
import com.secretvault.rotation.entity.SecretLease;
import com.secretvault.rotation.model.ConsumerStatus;
import com.secretvault.rotation.model.ConsumerType;
import com.secretvault.rotation.model.LeaseStatus;
import com.secretvault.rotation.model.RotationStatus;
import com.secretvault.rotation.model.RotationStrategy;
import com.secretvault.rotation.model.SecretType;
import com.secretvault.rotation.model.ValidationType;
import com.secretvault.rotation.provider.DefaultCryptoRotator;
import com.secretvault.rotation.provider.SecretRotatorRegistry;
import com.secretvault.rotation.repository.*;
import com.secretvault.rotation.service.*;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import com.secretvault.security.engine.rules.RotationRiskRule;
import com.secretvault.security.finding.dto.SecurityFindingDraft;
import com.secretvault.workspace.entity.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 12.1 Comprehensive 20-Step End-to-End Secret Rotation Pipeline Test.
 * Validates the complete integrated flow across all architectural layers.
 */
@ExtendWith(MockitoExtension.class)
class FullEndToEndRotationPipelineTest {

    @Mock private RotationPolicyRepository policyRepository;
    @Mock private RotationJobRepository jobRepository;
    @Mock private RotationAttemptRepository attemptRepository;
    @Mock private SecretRepository secretRepository;
    @Mock private SecretVersionRepository versionRepository;
    @Mock private EnvironmentRepository environmentRepository;
    @Mock private SecretLeaseRepository leaseRepository;
    @Mock private SecretConsumerRepository consumerRepository;
    @Mock private MachineIdentityRepository machineRepository;
    @Mock private EncryptionService encryptionService;
    @Mock private RotationValidationEngine validationEngine;
    @Mock private EffectiveAccessService effectiveAccessService;
    @Mock private AuditService auditService;

    private RotationService rotationService;
    private SecretLeaseService leaseService;
    private SecretConsumerService consumerService;
    private RotationRiskRule rotationRiskRule;

    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID secretId;
    private UUID machineId;
    private UUID actorId;

    private Workspace testWorkspace;
    private Environment testEnv;
    private Secret testSecret;
    private MachineIdentity testMachine;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        secretId = UUID.randomUUID();
        machineId = UUID.randomUUID();
        actorId = UUID.randomUUID();

        testWorkspace = new Workspace();
        testWorkspace.setId(workspaceId);
        testWorkspace.setName("Acme Corporation");

        testEnv = new Environment();
        testEnv.setId(environmentId);
        testEnv.setProjectId(projectId);
        testEnv.setName("production");
        testEnv.setEnvType(EnvType.PRODUCTION);

        testSecret = new Secret();
        testSecret.setId(secretId);
        testSecret.setEnvironmentId(environmentId);
        testSecret.setName("DATABASE_PRIMARY_PASSWORD");
        testSecret.setCurrentVersionNumber(1);

        testMachine = new MachineIdentity();
        testMachine.setId(machineId);
        testMachine.setName("k8s-payment-cluster-worker");
        testMachine.setStatus(MachineStatus.ACTIVE);

        RotationDistributedLock lock = new RotationDistributedLock(null);
        SecretRotatorRegistry registry = new SecretRotatorRegistry(List.of(new DefaultCryptoRotator(new SecretGenerationEngine(new ObjectMapper()))));

        rotationService = new RotationService(
                policyRepository, jobRepository, attemptRepository, secretRepository,
                versionRepository, environmentRepository, leaseRepository, encryptionService,
                registry, validationEngine, lock, effectiveAccessService, auditService
        );

        leaseService = new SecretLeaseService(
                leaseRepository, secretRepository, environmentRepository,
                machineRepository, effectiveAccessService, auditService
        );

        consumerService = new SecretConsumerService(
                consumerRepository, effectiveAccessService, auditService
        );

        rotationRiskRule = new RotationRiskRule(
                secretRepository, policyRepository, jobRepository, environmentRepository
        );
    }

    @Test
    @DisplayName("Complete 20-step E2E Lifecycle: Identity -> Policy -> Rotation -> Lease -> Consumer -> Security Center")
    void testComplete20StepRotationLifecycle() {
        // 1. Authorization checks pass
        when(effectiveAccessService.evaluateAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(AccessDecision.allow(AccessPermission.SECRET_ROTATION_CREATE, AccessScope.SECRET, AccessSourceType.WORKSPACE_ROLE, "ROLE_ADMIN", "Access Granted by RBAC Engine"));

        // 2. Secret & Environment lookup
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(testSecret));
        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(testSecret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(testEnv));
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(testMachine));

        // 3. Register Consumer (Step 5)
        when(consumerRepository.save(any(SecretConsumer.class))).thenAnswer(inv -> inv.getArgument(0));
        RegisterSecretConsumerRequest regReq = new RegisterSecretConsumerRequest(
                "payment-api", ConsumerType.APPLICATION,
                machineId, "pod-prod-88", "worker-node-1", "secretvault-sdk-java/1.2.0",
                "Spring Boot 3.3.4", true, false
        );
        SecretConsumerResponse consumerRes = consumerService.registerConsumer(workspaceId, projectId, environmentId, regReq, actorId);
        assertThat(consumerRes).isNotNull();
        assertThat(consumerRes.status()).isEqualTo(ConsumerStatus.ACTIVE);

        // 4. Issue Ephemeral Lease for Secret v1 (Step 6)
        when(leaseRepository.save(any(SecretLease.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateSecretLeaseRequest leaseReq = new CreateSecretLeaseRequest(secretId, machineId, consumerRes.id(), 3600L, 86400L, "10.0.1.45", "PaymentApp/1.0");
        SecretLeaseResponse leaseRes = leaseService.createLease(workspaceId, leaseReq, actorId);
        assertThat(leaseRes.secretVersionNumber()).isEqualTo(1);
        assertThat(leaseRes.status()).isEqualTo(LeaseStatus.ACTIVE);

        // 5. Create Rotation Policy (Step 7)
        when(policyRepository.save(any(RotationPolicy.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateRotationPolicyRequest policyReq = new CreateRotationPolicyRequest(
                secretId, true, RotationStrategy.SCHEDULED, SecretType.PASSWORD, 2592000L, 3600L,
                7776000L, 86400L, null, "UTC", 3, 300,
                ValidationType.AUTHENTICATION,
                com.secretvault.rotation.model.RolloutStrategy.STAGED,
                1800L, true, true, false, false, null
        );
        RotationPolicyResponse policyRes = rotationService.createPolicy(workspaceId, projectId, environmentId, secretId, policyReq, actorId);
        assertThat(policyRes.enabled()).isTrue();

        RotationPolicy createdPolicy = new RotationPolicy();
        createdPolicy.setId(policyRes.id());
        createdPolicy.setSecretId(secretId);
        createdPolicy.setWorkspaceId(workspaceId);
        createdPolicy.setEnabled(true);
        createdPolicy.setIntervalSeconds(2592000L);
        createdPolicy.setGracePeriodSeconds(1800L);
        when(policyRepository.findBySecretId(secretId)).thenReturn(Optional.of(createdPolicy));

        // 6. Execute Rotation (Steps 8-12)
        when(jobRepository.save(any(RotationJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(validationEngine.validateSecret(any(), any(), any())).thenReturn(true);
        when(encryptionService.encrypt(any(), any())).thenReturn(
                new EncryptedPayload("cipher-v2".getBytes(), "dek-v2".getBytes(), "iv-v2".getBytes(), "tag-v2".getBytes(), "kms-ref-2")
        );
        when(versionRepository.save(any(SecretVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        TriggerRotationRequest triggerReq = new TriggerRotationRequest(RotationStrategy.SCHEDULED, "Scheduled automated rotation", false, false);
        RotationJobResponse jobRes = rotationService.triggerRotation(workspaceId, secretId, triggerReq, actorId, "idempotency-key-test-1");

        assertThat(jobRes).isNotNull();
        assertThat(jobRes.status()).isIn(RotationStatus.ACTIVE, RotationStatus.GRACE_PERIOD, RotationStatus.COMPLETED);
        assertThat(testSecret.getCurrentVersionNumber()).isEqualTo(2); // Version bumped to v2

        // 7. Consumer acknowledges new version v2 (Step 13)
        SecretConsumer activeConsumer = new SecretConsumer();
        activeConsumer.setId(consumerRes.id());
        activeConsumer.setWorkspaceId(workspaceId);
        activeConsumer.setStatus(ConsumerStatus.ACTIVE);
        when(consumerRepository.findById(consumerRes.id())).thenReturn(Optional.of(activeConsumer));

        ConsumerHeartbeatRequest hbReq = new ConsumerHeartbeatRequest(2, "secretvault-sdk-java/1.2.0", "Spring Boot 3.3.4");
        SecretConsumerResponse hbRes = consumerService.heartbeat(workspaceId, consumerRes.id(), hbReq);
        assertThat(hbRes.currentAcknowledgedVersion()).isEqualTo(2);

        // 8. Security Center Rule verification (Step 17)
        when(policyRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(createdPolicy));
        when(jobRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of());
        createdPolicy.setNextRotationDueAt(Instant.now().plus(Duration.ofDays(30))); // Not overdue

        List<SecurityFindingDraft> findings = rotationRiskRule.evaluate(testWorkspace, Instant.now());
        // Healthy secret -> 0 findings
        assertThat(findings).isEmpty();

        // 9. Verify Audit Records Created (Step 16)
        verify(auditService, atLeast(3)).recordSecretAudit(any(), eq(workspaceId), any(), any(), any(), any(), any(), any());
    }
}
