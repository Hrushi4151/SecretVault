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
import com.secretvault.rotation.entity.SecretLease;
import com.secretvault.rotation.model.LeaseStatus;
import com.secretvault.rotation.model.RotationStatus;
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
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 12.1 Compromised Secret Remediation & Emergency Rotation Hardening Test Suite.
 */
@ExtendWith(MockitoExtension.class)
class RotationEmergencyAndCompromiseTest {

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
        testSecret.setName("DB_PASSWORD");
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
    @DisplayName("Compromised Secret Workflow: Immediate lease invalidation and emergency rotation execution")
    void testMarkCompromisedFullWorkflow() {
        SecretLease activeLease1 = new SecretLease();
        activeLease1.setId(UUID.randomUUID());
        activeLease1.setSecretId(secretId);
        activeLease1.setStatus(LeaseStatus.ACTIVE);

        SecretLease activeLease2 = new SecretLease();
        activeLease2.setId(UUID.randomUUID());
        activeLease2.setSecretId(secretId);
        activeLease2.setStatus(LeaseStatus.ACTIVE);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(testSecret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(testEnv));
        when(effectiveAccessService.evaluateAccess(eq(workspaceId), eq(projectId), eq(environmentId), eq(secretId), eq(AccessPermission.SECRET_ROTATION_EMERGENCY), eq(actorId)))
                .thenReturn(AccessDecision.allow(AccessPermission.SECRET_ROTATION_EMERGENCY, AccessScope.SECRET, AccessSourceType.WORKSPACE_ROLE, "ROLE_SECURITY_ADMIN", "Security Admin Granted"));
        when(leaseRepository.findBySecretIdAndStatus(secretId, LeaseStatus.ACTIVE))
                .thenReturn(List.of(activeLease1, activeLease2));

        when(jobRepository.save(any(RotationJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(validationEngine.validateSecret(any(), any(), any())).thenReturn(true);
        when(encryptionService.encrypt(any(), any())).thenReturn(new EncryptedPayload("cipher".getBytes(), "dek".getBytes(), "iv".getBytes(), "tag".getBytes(), "key-ref"));
        when(versionRepository.save(any(SecretVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        MarkCompromisedRequest req = new MarkCompromisedRequest("Compromised via credential leak in public repo", true, true);
        rotationService.markCompromised(workspaceId, secretId, req, actorId);

        // 1. Verify lease invalidation
        assertThat(activeLease1.getStatus()).isEqualTo(LeaseStatus.REVOKED);
        assertThat(activeLease2.getStatus()).isEqualTo(LeaseStatus.REVOKED);
        verify(leaseRepository, times(2)).save(any(SecretLease.class));

        // 2. Verify audit events recorded
        verify(auditService).recordSecretAudit(isNull(), eq(workspaceId), eq(actorId), eq(AuditAction.SECRET_MARKED_COMPROMISED), eq(secretId), isNull(), isNull(), eq("INCIDENT"));
        verify(auditService).recordSecretAudit(isNull(), eq(workspaceId), eq(actorId), eq(AuditAction.LEASE_REVOKED), eq(secretId), isNull(), isNull(), eq("COMPROMISED_EMERGENCY"));

        // 3. Verify emergency rotation triggered
        assertThat(testSecret.getCurrentVersionNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("Unauthorized Compromise Attempt: Denied without modifying leases or secret state")
    void testUnauthorizedMarkCompromisedDenied() {
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(testSecret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(testEnv));
        when(effectiveAccessService.evaluateAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(AccessDecision.deny(AccessPermission.SECRET_ROTATION_EMERGENCY, "Unauthorized role"));

        MarkCompromisedRequest req = new MarkCompromisedRequest("Leak", true, true);

        assertThatThrownBy(() -> rotationService.markCompromised(workspaceId, secretId, req, actorId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Access denied");

        verify(leaseRepository, never()).findBySecretIdAndStatus(any(), any());
    }

    @Test
    @DisplayName("Compromise failure handling: Validation failure during emergency rotation records failure without claiming success")
    void testCompromiseValidationFailure() {
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(testSecret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(testEnv));
        when(effectiveAccessService.evaluateAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(AccessDecision.allow(AccessPermission.SECRET_ROTATION_EMERGENCY, AccessScope.SECRET, AccessSourceType.WORKSPACE_ROLE, "ROLE_ADMIN", "Admin"));
        when(leaseRepository.findBySecretIdAndStatus(secretId, LeaseStatus.ACTIVE)).thenReturn(List.of());
        when(jobRepository.save(any(RotationJob.class))).thenAnswer(inv -> inv.getArgument(0));

        // Validation fails
        when(validationEngine.validateSecret(any(), any(), any())).thenReturn(false);

        MarkCompromisedRequest req = new MarkCompromisedRequest("Leak", true, true);
        rotationService.markCompromised(workspaceId, secretId, req, actorId);

        // Verify version was not bumped
        assertThat(testSecret.getCurrentVersionNumber()).isEqualTo(1);
    }
}
