package com.secretvault.rotation;

import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.model.AccessSourceType;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.events.model.DomainEvent;
import com.secretvault.events.model.EventType;
import com.secretvault.events.publisher.EventPublisher;
import com.secretvault.rotation.dto.RotationDtos.*;
import com.secretvault.rotation.engine.RotationValidationEngine;
import com.secretvault.rotation.entity.RotationJob;
import com.secretvault.rotation.entity.RotationPolicy;
import com.secretvault.rotation.model.RotationStatus;
import com.secretvault.rotation.model.RotationStrategy;
import com.secretvault.rotation.model.SecretType;
import com.secretvault.rotation.provider.SecretRotatorRegistry;
import com.secretvault.rotation.repository.RotationAttemptRepository;
import com.secretvault.rotation.repository.RotationJobRepository;
import com.secretvault.rotation.repository.RotationPolicyRepository;
import com.secretvault.rotation.repository.SecretLeaseRepository;
import com.secretvault.rotation.service.RotationDistributedLock;
import com.secretvault.rotation.service.RotationService;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.entity.VersionType;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RotationChaosAndSecurityHardeningTest {

    @Mock
    private RotationPolicyRepository policyRepository;

    @Mock
    private RotationJobRepository jobRepository;

    @Mock
    private RotationAttemptRepository attemptRepository;

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private SecretVersionRepository versionRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private SecretLeaseRepository leaseRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private SecretRotatorRegistry rotatorRegistry;

    @Mock
    private RotationValidationEngine validationEngine;

    @Mock
    private RotationDistributedLock distributedLock;

    @Mock
    private EffectiveAccessService effectiveAccessService;

    @Mock
    private AuditService auditService;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private RotationService rotationService;

    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID secretId;
    private UUID actorId;
    private Secret secret;
    private Environment environment;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        secretId = UUID.randomUUID();
        actorId = UUID.randomUUID();

        environment = new Environment();
        environment.setId(environmentId);
        environment.setProjectId(projectId);

        secret = new Secret();
        secret.setId(secretId);
        secret.setName("OAUTH_CLIENT_SECRET");
        secret.setEnvironmentId(environmentId);
        secret.setStatus(SecretStatus.ACTIVE);
        secret.setCurrentVersionNumber(3);
    }

    private void grantAccess(AccessPermission permission) {
        when(effectiveAccessService.evaluateAccess(eq(workspaceId), any(), any(), any(), eq(permission), eq(actorId)))
                .thenReturn(AccessDecision.allow(permission, AccessScope.WORKSPACE, AccessSourceType.WORKSPACE_ROLE, "ROLE_ADMIN", "Granted for test"));
    }

    @Test
    @DisplayName("Chaos: Concurrent rotation attempt when lock already held should fail gracefully")
    void testLockContentionHandling() {
        grantAccess(AccessPermission.SECRET_ROTATION_CREATE);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(policyRepository.findBySecretId(secretId)).thenReturn(Optional.empty());
        when(jobRepository.save(any(RotationJob.class))).thenAnswer(i -> {
            RotationJob j = i.getArgument(0);
            if (j.getId() == null) j.setId(UUID.randomUUID());
            return j;
        });

        // Simulate lock contention: lock cannot be acquired
        when(distributedLock.acquireLock(eq(secretId), anyString(), any(Duration.class))).thenReturn(false);

        TriggerRotationRequest req = new TriggerRotationRequest(RotationStrategy.MANUAL, "Concurrent attempt", false, false);
        RotationJobResponse response = rotationService.triggerRotation(workspaceId, secretId, req, actorId, null);

        assertNotNull(response);
        assertEquals(RotationStatus.FAILED, response.status());
        assertTrue(response.errorMessage().contains("Concurrent rotation lock already held"));
    }

    @Test
    @DisplayName("Security: Idempotency key replay returns existing job without duplicate execution")
    void testIdempotentRotationReplay() {
        grantAccess(AccessPermission.SECRET_ROTATION_CREATE);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        String idempotencyKey = "idemp-req-uuid-9999";
        RotationJob existingJob = new RotationJob(workspaceId, secretId, null, RotationStrategy.MANUAL, actorId);
        existingJob.setId(UUID.randomUUID());
        existingJob.setIdempotencyKey(idempotencyKey);
        existingJob.setStatus(RotationStatus.ACTIVE);

        when(jobRepository.findByWorkspaceIdAndIdempotencyKey(workspaceId, idempotencyKey))
                .thenReturn(Optional.of(existingJob));

        TriggerRotationRequest req = new TriggerRotationRequest(RotationStrategy.MANUAL, "Duplicate request", false, false);
        RotationJobResponse response = rotationService.triggerRotation(workspaceId, secretId, req, actorId, idempotencyKey);

        assertNotNull(response);
        assertEquals(existingJob.getId(), response.id());
        assertEquals(RotationStatus.ACTIVE, response.status());

        // Verify lock was NEVER acquired because request was resolved idempotently
        verify(distributedLock, never()).acquireLock(any(), any(), any());
    }

    @Test
    @DisplayName("Security: Rollback decrypts target historical version and re-encrypts into version N+1")
    void testRollbackReEncryption() {
        grantAccess(AccessPermission.SECRET_ROTATION_ROLLBACK);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        SecretVersion targetVersion = new SecretVersion(
                secretId, 2, VersionType.ROTATION,
                new byte[]{1, 2, 3}, new byte[]{4, 5, 6}, new byte[]{7, 8}, new byte[]{9, 10}, "kek-ref",
                actorId, "Target v2", null, null, null, null
        );
        when(versionRepository.findBySecretIdAndVersionNumber(secretId, 2)).thenReturn(Optional.of(targetVersion));

        when(encryptionService.decrypt(any(EncryptedPayload.class), anyString())).thenReturn("restored-plaintext".getBytes());
        EncryptedPayload newEnc = new EncryptedPayload(new byte[]{11}, new byte[]{12}, new byte[]{13}, new byte[]{14}, "kek-ref");
        when(encryptionService.encrypt(any(byte[].class), anyString())).thenReturn(newEnc);
        when(jobRepository.save(any(RotationJob.class))).thenAnswer(i -> {
            RotationJob j = i.getArgument(0);
            if (j.getId() == null) j.setId(UUID.randomUUID());
            return j;
        });

        RotationRollbackRequest req = new RotationRollbackRequest(2, "Rollback to stable v2");
        RotationJobResponse rollbackResponse = rotationService.rollbackRotation(workspaceId, secretId, req, actorId);

        assertNotNull(rollbackResponse);
        assertEquals(RotationStatus.ROLLED_BACK, rollbackResponse.status());
        assertEquals(4, rollbackResponse.targetVersionNumber()); // Target becomes N+1 (3 + 1 = 4)
        verify(versionRepository, times(1)).save(any(SecretVersion.class));
        verify(secretRepository, times(1)).save(secret);
        assertEquals(4, secret.getCurrentVersionNumber());
    }

    @Test
    @DisplayName("Security: Cross-tenant access attempt to get or update rotation policy is denied")
    void testCrossTenantIsolation() {
        UUID otherWorkspaceId = UUID.randomUUID();
        when(effectiveAccessService.evaluateAccess(eq(otherWorkspaceId), any(), any(), any(), eq(AccessPermission.SECRET_ROTATION_READ), eq(actorId)))
                .thenReturn(AccessDecision.deny(AccessPermission.SECRET_ROTATION_READ, "Cross tenant violation"));

        RotationPolicy policy = new RotationPolicy();
        policy.setId(UUID.randomUUID());
        policy.setWorkspaceId(workspaceId);
        policy.setSecretId(secretId);

        when(policyRepository.findBySecretId(secretId)).thenReturn(Optional.of(policy));
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        ApiException ex = assertThrows(ApiException.class, () -> rotationService.getPolicy(otherWorkspaceId, secretId, actorId));
        assertTrue(ex.getMessage().contains("Access denied"));
    }
}
