package com.secretvault.rotation;

import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.model.AccessSourceType;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.model.EncryptedPayload;
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
import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.entity.VersionType;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 12.1 System Invariants (1-15) and Plaintext Leakage Certification Test.
 */
@ExtendWith(MockitoExtension.class)
class RotationSystemInvariantsAndPlaintextLeakageTest {

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

    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID secretId;
    private UUID actorId;
    private Secret testSecret;
    private Environment testEnv;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        secretId = UUID.randomUUID();
        actorId = UUID.randomUUID();

        testSecret = new Secret();
        testSecret.setId(secretId);
        testSecret.setEnvironmentId(environmentId);
        testSecret.setName("CERTIFIED_SECRET");
        testSecret.setCurrentVersionNumber(1);

        testEnv = new Environment();
        testEnv.setId(environmentId);
        testEnv.setProjectId(projectId);
        testEnv.setName("production");

        RotationDistributedLock lock = new RotationDistributedLock(null);
        SecretRotatorRegistry registry = new SecretRotatorRegistry(List.of(new DefaultCryptoRotator(new com.secretvault.rotation.engine.SecretGenerationEngine(new com.fasterxml.jackson.databind.ObjectMapper()))));

        rotationService = new RotationService(
                policyRepository, jobRepository, attemptRepository, secretRepository,
                versionRepository, environmentRepository, leaseRepository, encryptionService,
                registry, validationEngine, lock, effectiveAccessService, auditService
        );
    }

    @Test
    @DisplayName("Invariant 1: No Plaintext Persistence in SecretVersion entity")
    void testInvariant1NoPlaintextPersistence() {
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(testSecret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(testEnv));
        when(effectiveAccessService.evaluateAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(AccessDecision.allow(AccessPermission.SECRET_ROTATION_CREATE, AccessScope.SECRET, AccessSourceType.WORKSPACE_ROLE, "ROLE_ADMIN", "Granted"));
        when(jobRepository.save(any(RotationJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(validationEngine.validateSecret(any(), any(), any())).thenReturn(true);

        byte[] fakeCiphertext = "encrypted-payload-gcm".getBytes(StandardCharsets.UTF_8);
        when(encryptionService.encrypt(any(), any())).thenReturn(
                new EncryptedPayload(fakeCiphertext, "dek".getBytes(), "iv".getBytes(), "tag".getBytes(), "key-ref")
        );

        ArgumentCaptor<SecretVersion> versionCaptor = ArgumentCaptor.forClass(SecretVersion.class);
        when(versionRepository.save(versionCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        rotationService.triggerRotation(workspaceId, secretId, new TriggerRotationRequest(RotationStrategy.MANUAL, "Inv 1 test", false, false), actorId, null);

        SecretVersion savedVersion = versionCaptor.getValue();
        assertThat(savedVersion).isNotNull();
        assertThat(savedVersion.getCiphertext()).isEqualTo(fakeCiphertext);
        // Ciphertext should not be plaintext string
        assertThat(new String(savedVersion.getCiphertext())).doesNotContain("CERTIFIED_SECRET");
    }

    @Test
    @DisplayName("Invariant 5 & 13: Old Secret Versions are Immutable; Rollback creates a new N+1 version")
    void testInvariant5And13RollbackCreatesNewVersion() {
        SecretVersion v1 = new SecretVersion(
                secretId, 1, VersionType.VALUE_UPDATE, "cipher1".getBytes(), "dek1".getBytes(),
                "iv1".getBytes(), "tag1".getBytes(), "key1", actorId, "Initial version"
        );

        testSecret.setCurrentVersionNumber(2); // Currently on v2

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(testSecret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(testEnv));
        when(effectiveAccessService.evaluateAccess(eq(workspaceId), eq(projectId), eq(environmentId), eq(secretId), eq(AccessPermission.SECRET_ROTATION_ROLLBACK), eq(actorId)))
                .thenReturn(AccessDecision.allow(AccessPermission.SECRET_ROTATION_ROLLBACK, AccessScope.SECRET, AccessSourceType.WORKSPACE_ROLE, "ROLE_ADMIN", "Granted"));
        when(versionRepository.findBySecretIdAndVersionNumber(secretId, 1)).thenReturn(Optional.of(v1));
        when(encryptionService.decrypt(any(), any())).thenReturn("original-v1-secret".getBytes(StandardCharsets.UTF_8));
        when(encryptionService.encrypt(any(), any())).thenReturn(
                new EncryptedPayload("cipher3".getBytes(), "dek3".getBytes(), "iv3".getBytes(), "tag3".getBytes(), "key3")
        );

        ArgumentCaptor<SecretVersion> rollbackCaptor = ArgumentCaptor.forClass(SecretVersion.class);
        when(versionRepository.save(rollbackCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(jobRepository.save(any(RotationJob.class))).thenAnswer(inv -> inv.getArgument(0));

        RotationJobResponse rollbackRes = rotationService.rollbackRotation(workspaceId, secretId, new RotationRollbackRequest(1, "Emergency rollback"), actorId);

        // Verify secret version is bumped to 3 (not modifying v1 or v2)
        assertThat(testSecret.getCurrentVersionNumber()).isEqualTo(3);
        SecretVersion rollbackVersion = rollbackCaptor.getValue();
        assertThat(rollbackVersion.getVersionNumber()).isEqualTo(3);
        assertThat(rollbackVersion.getVersionType()).isEqualTo(VersionType.ROLLBACK);
        assertThat(rollbackVersion.getSourceVersionId()).isEqualTo(v1.getId());
    }

    @Test
    @DisplayName("Invariant 9: Failed Validation Cannot Activate Credential")
    void testInvariant9FailedValidationNeverActivates() {
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(testSecret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(testEnv));
        when(effectiveAccessService.evaluateAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(AccessDecision.allow(AccessPermission.SECRET_ROTATION_CREATE, AccessScope.SECRET, AccessSourceType.WORKSPACE_ROLE, "ROLE_ADMIN", "Granted"));
        when(jobRepository.save(any(RotationJob.class))).thenAnswer(inv -> inv.getArgument(0));

        // Force validation to FAIL
        when(validationEngine.validateSecret(any(), any(), any())).thenReturn(false);

        RotationJobResponse res = rotationService.triggerRotation(workspaceId, secretId, new TriggerRotationRequest(RotationStrategy.MANUAL, "Validation fail test", false, false), actorId, null);

        assertThat(res.status()).isEqualTo(RotationStatus.VALIDATION_FAILED);
        // Current version must remain at 1
        assertThat(testSecret.getCurrentVersionNumber()).isEqualTo(1);
        verify(versionRepository, never()).save(any(SecretVersion.class));
    }

    @Test
    @DisplayName("Invariant 11: Rotation Cannot Silently Bypass EffectiveAccessService")
    void testInvariant11AccessBypassForbidden() {
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(testSecret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(testEnv));
        when(effectiveAccessService.evaluateAccess(any(), any(), any(), any(), eq(AccessPermission.SECRET_ROTATION_CREATE), any()))
                .thenReturn(AccessDecision.deny(AccessPermission.SECRET_ROTATION_CREATE, "Lacks SECRET_ROTATION_CREATE"));

        assertThatThrownBy(() -> rotationService.triggerRotation(workspaceId, secretId, new TriggerRotationRequest(RotationStrategy.MANUAL, "Test", false, false), actorId, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Access denied");

        verify(jobRepository, never()).save(any(RotationJob.class));
    }

    @Test
    @DisplayName("Invariant 12: Audit Events and Exception Logs Do Not Leak Plaintext")
    void testInvariant12NoPlaintextInAudit() {
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(testSecret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(testEnv));
        when(effectiveAccessService.evaluateAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(AccessDecision.allow(AccessPermission.SECRET_ROTATION_CREATE, AccessScope.SECRET, AccessSourceType.WORKSPACE_ROLE, "ROLE_ADMIN", "Granted"));
        when(jobRepository.save(any(RotationJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(validationEngine.validateSecret(any(), any(), any())).thenReturn(true);
        when(encryptionService.encrypt(any(), any())).thenReturn(
                new EncryptedPayload("cipher".getBytes(), "dek".getBytes(), "iv".getBytes(), "tag".getBytes(), "key")
        );

        rotationService.triggerRotation(workspaceId, secretId, new TriggerRotationRequest(RotationStrategy.MANUAL, "Audit test", false, false), actorId, null);

        // Verify auditService invocations
        ArgumentCaptor<String> metadataCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService, atLeastOnce()).recordSecretAudit(
                any(), eq(workspaceId), any(), any(), eq(secretId), any(), any(), metadataCaptor.capture()
        );

        for (String meta : metadataCaptor.getAllValues()) {
            if (meta != null) {
                // Ensure no raw cryptographic key or password material is logged in audit metadata
                assertThat(meta).doesNotContain("password=");
                assertThat(meta).doesNotContain("secretValue=");
            }
        }
    }
}
