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
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.rotation.dto.RotationDtos.*;
import com.secretvault.rotation.engine.RotationValidationEngine;
import com.secretvault.rotation.engine.SecretGenerationEngine;
import com.secretvault.rotation.entity.RotationJob;
import com.secretvault.rotation.entity.RotationPolicy;
import com.secretvault.rotation.model.LeaseStatus;
import com.secretvault.rotation.model.RolloutStrategy;
import com.secretvault.rotation.model.RotationStatus;
import com.secretvault.rotation.model.RotationStrategy;
import com.secretvault.rotation.model.SecretType;
import com.secretvault.rotation.model.ValidationType;
import com.secretvault.rotation.provider.SecretRotator;
import com.secretvault.rotation.provider.SecretRotatorRegistry;
import com.secretvault.rotation.repository.*;
import com.secretvault.rotation.service.RotationDistributedLock;
import com.secretvault.rotation.service.RotationService;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RotationServiceTest {

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
    private ProjectRepository projectRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private EffectiveAccessService effectiveAccessService;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private SecretGenerationEngine generationEngine;

    @Mock
    private RotationValidationEngine validationEngine;

    @Mock
    private SecretRotatorRegistry rotatorRegistry;

    @Mock
    private RotationDistributedLock distributedLock;

    @Mock
    private SecretLeaseRepository leaseRepository;

    @Mock
    private SecretConsumerRepository consumerRepository;

    @Mock
    private SecretDependencyRepository dependencyRepository;

    @InjectMocks
    private RotationService rotationService;

    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID secretId;
    private UUID actorId;
    private Secret secret;
    private Environment environment;
    private Project project;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        secretId = UUID.randomUUID();
        actorId = UUID.randomUUID();

        workspace = new Workspace();
        workspace.setId(workspaceId);

        project = new Project();
        project.setId(projectId);
        project.setWorkspaceId(workspaceId);

        environment = new Environment();
        environment.setId(environmentId);
        environment.setProjectId(projectId);

        secret = new Secret();
        secret.setId(secretId);
        secret.setName("DATABASE_PASSWORD");
        secret.setEnvironmentId(environmentId);
        secret.setStatus(SecretStatus.ACTIVE);
        secret.setCurrentVersionNumber(1);
    }

    private void grantAccess(AccessPermission permission) {
        when(effectiveAccessService.evaluateAccess(eq(workspaceId), any(), any(), any(), eq(permission), eq(actorId)))
                .thenReturn(AccessDecision.allow(permission, AccessScope.WORKSPACE, AccessSourceType.WORKSPACE_ROLE, "ROLE_ADMIN", "Granted for test"));
    }

    @Test
    @DisplayName("Should create rotation policy successfully")
    void testCreatePolicy() {
        grantAccess(AccessPermission.SECRET_ROTATION_CREATE);

        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(policyRepository.findBySecretId(secretId)).thenReturn(Optional.empty());
        when(policyRepository.save(any(RotationPolicy.class))).thenAnswer(i -> {
            RotationPolicy p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        CreateRotationPolicyRequest req = new CreateRotationPolicyRequest(
                secretId,
                true,
                RotationStrategy.SCHEDULED,
                SecretType.PASSWORD,
                2592000L,
                3600L,
                7776000L,
                86400L,
                "0 0 1 * *",
                "UTC",
                3,
                300,
                ValidationType.AUTHENTICATION,
                RolloutStrategy.STAGED,
                1800L,
                true,
                true,
                false,
                false,
                null
        );

        RotationPolicyResponse response = rotationService.createPolicy(workspaceId, projectId, environmentId, secretId, req, actorId);

        assertNotNull(response);
        assertTrue(response.enabled());
        assertEquals(2592000L, response.intervalSeconds());
        assertEquals(1800L, response.gracePeriodSeconds());
        verify(policyRepository, times(1)).save(any(RotationPolicy.class));
    }

    @Test
    @DisplayName("Should trigger rotation and execute lifecycle states")
    void testTriggerRotation() {
        grantAccess(AccessPermission.SECRET_ROTATION_CREATE);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(policyRepository.findBySecretId(secretId)).thenReturn(Optional.empty());
        when(jobRepository.save(any(RotationJob.class))).thenAnswer(i -> {
            RotationJob j = i.getArgument(0);
            if (j.getId() == null) j.setId(UUID.randomUUID());
            return j;
        });

        SecretRotator rotator = mock(SecretRotator.class);
        when(rotator.generate(any(), any())).thenReturn("generated-test-secret");
        when(rotator.validate(any(), any(), any())).thenReturn(true);
        when(rotatorRegistry.getRotator(any(), any())).thenReturn(rotator);
        when(validationEngine.validateSecret(any(), any(), any())).thenReturn(true);
        when(distributedLock.acquireLock(eq(secretId), anyString(), any(Duration.class))).thenReturn(true);

        EncryptedPayload encPayload = new EncryptedPayload(new byte[]{1}, new byte[]{2}, new byte[]{3}, new byte[]{4}, "master-kek");
        when(encryptionService.encrypt(any(byte[].class), anyString())).thenReturn(encPayload);

        when(versionRepository.save(any(SecretVersion.class))).thenAnswer(i -> {
            SecretVersion v = i.getArgument(0);
            return v;
        });

        TriggerRotationRequest req = new TriggerRotationRequest(RotationStrategy.MANUAL, "Test trigger", false, false);
        RotationJobResponse res = rotationService.triggerRotation(workspaceId, secretId, req, actorId, null);

        assertNotNull(res);
        assertTrue(res.status() == RotationStatus.GRACE_PERIOD || res.status() == RotationStatus.COMPLETED);
        assertEquals(RotationStrategy.MANUAL, res.triggerType());
    }

    @Test
    @DisplayName("Should execute emergency rotation with immediate activation and lease revocation")
    void testMarkCompromisedEmergency() {
        grantAccess(AccessPermission.SECRET_ROTATION_EMERGENCY);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(policyRepository.findBySecretId(secretId)).thenReturn(Optional.empty());
        when(leaseRepository.findBySecretIdAndStatus(eq(secretId), eq(LeaseStatus.ACTIVE))).thenReturn(List.of());
        when(jobRepository.save(any(RotationJob.class))).thenAnswer(i -> {
            RotationJob j = i.getArgument(0);
            if (j.getId() == null) j.setId(UUID.randomUUID());
            return j;
        });

        MarkCompromisedRequest req = new MarkCompromisedRequest("Detected in leak", true, true);
        assertDoesNotThrow(() -> rotationService.markCompromised(workspaceId, secretId, req, actorId));

        verify(jobRepository, atLeastOnce()).save(any(RotationJob.class));
    }

    @Test
    @DisplayName("Should cancel an active rotation job")
    void testCancelRotation() {
        grantAccess(AccessPermission.SECRET_ROTATION_CANCEL);

        RotationJob job = new RotationJob(workspaceId, secretId, null, RotationStrategy.MANUAL, actorId);
        job.setId(UUID.randomUUID());
        job.setStatus(RotationStatus.STAGING);

        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(jobRepository.save(any(RotationJob.class))).thenReturn(job);

        assertDoesNotThrow(() -> rotationService.cancelRotation(workspaceId, job.getId(), actorId));
        assertEquals(RotationStatus.CANCELLED, job.getStatus());
        assertNotNull(job.getCancelledAt());
    }

    @Test
    @DisplayName("Should reject unauthorized user from triggering rotation")
    void testUnauthorizedTrigger() {
        when(effectiveAccessService.evaluateAccess(any(), any(), any(), any(), eq(AccessPermission.SECRET_ROTATION_CREATE), any()))
                .thenReturn(AccessDecision.deny(AccessPermission.SECRET_ROTATION_CREATE, "Forbidden"));

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        TriggerRotationRequest req = new TriggerRotationRequest(RotationStrategy.MANUAL, "Unauth", false, false);
        assertThrows(ApiException.class, () -> rotationService.triggerRotation(workspaceId, secretId, req, actorId, null));
    }
}
