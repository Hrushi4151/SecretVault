package com.secretvault.rotation;

import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.model.AccessSourceType;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.events.model.BaseDomainEvent;
import com.secretvault.events.model.DomainEvent;
import com.secretvault.events.model.EventType;
import com.secretvault.events.publisher.EventPublisher;
import com.secretvault.rotation.dto.RotationDtos.*;
import com.secretvault.rotation.model.RotationProviderPushEvent;
import com.secretvault.rotation.engine.RotationValidationEngine;
import com.secretvault.rotation.entity.RotationJob;
import com.secretvault.rotation.entity.RotationPolicy;
import com.secretvault.rotation.model.RotationStatus;
import com.secretvault.rotation.model.RotationStrategy;
import com.secretvault.rotation.model.SecretType;
import com.secretvault.rotation.model.ValidationType;
import com.secretvault.rotation.provider.SecretRotator;
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
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RotationOutboxEventIntegrationTest {

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
        secret.setName("STRIPE_API_KEY");
        secret.setEnvironmentId(environmentId);
        secret.setStatus(SecretStatus.ACTIVE);
        secret.setCurrentVersionNumber(1);
    }

    private void grantAccess(AccessPermission permission) {
        when(effectiveAccessService.evaluateAccess(eq(workspaceId), any(), any(), any(), eq(permission), eq(actorId)))
                .thenReturn(AccessDecision.allow(permission, AccessScope.WORKSPACE, AccessSourceType.WORKSPACE_ROLE, "ROLE_ADMIN", "Granted for test"));
    }

    @Test
    @DisplayName("Should publish ROTATION_POLICY_CREATED outbox event without sensitive credentials")
    void testPolicyCreatedEvent() {
        grantAccess(AccessPermission.SECRET_ROTATION_CREATE);

        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(policyRepository.findBySecretId(secretId)).thenReturn(Optional.empty());
        when(policyRepository.save(any(RotationPolicy.class))).thenAnswer(i -> {
            RotationPolicy p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        CreateRotationPolicyRequest req = new CreateRotationPolicyRequest(
                secretId, true, RotationStrategy.SCHEDULED, SecretType.API_KEY,
                2592000L, 3600L, null, 86400L, null, "UTC", 3, 300,
                ValidationType.AUTHENTICATION, null, 1800L, true, true, false, false, null
        );

        rotationService.createPolicy(workspaceId, projectId, environmentId, secretId, req, actorId);

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, times(1)).publish(eventCaptor.capture());

        DomainEvent event = eventCaptor.getValue();
        assertEquals(EventType.ROTATION_POLICY_CREATED, event.getEventType());
        assertEquals(workspaceId, event.getWorkspaceId());
        assertEquals(secretId, event.getSecretId());

        // Verify zero plaintext secrets in metadata
        Map<String, Object> meta = event.getMetadata();
        assertNotNull(meta);
        assertFalse(meta.containsKey("value"));
        assertFalse(meta.containsKey("password"));
        assertFalse(meta.containsKey("dek"));
    }

    @Test
    @DisplayName("Should publish ROTATION_ACTIVATED event with safe RotationProviderPushEvent metadata")
    void testRotationActivatedPushEvent() {
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
        when(rotator.generate(any(), any())).thenReturn("generated-plain-secret-v2");
        when(rotator.validate(any(), any(), any())).thenReturn(true);
        when(rotatorRegistry.getRotator(any(), any())).thenReturn(rotator);
        when(validationEngine.validateSecret(any(), any(), any())).thenReturn(true);
        when(distributedLock.acquireLock(eq(secretId), anyString(), any(Duration.class))).thenReturn(true);

        EncryptedPayload encPayload = new EncryptedPayload(new byte[]{10}, new byte[]{20}, new byte[]{30}, new byte[]{40}, "master-kek");
        when(encryptionService.encrypt(any(byte[].class), anyString())).thenReturn(encPayload);

        when(versionRepository.save(any(SecretVersion.class))).thenAnswer(i -> i.getArgument(0));

        TriggerRotationRequest req = new TriggerRotationRequest(RotationStrategy.MANUAL, "Triggered test", false, false);
        rotationService.triggerRotation(workspaceId, secretId, req, actorId, "idempotency-key-123");

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publish(eventCaptor.capture());

        List<DomainEvent> publishedEvents = eventCaptor.getAllValues();
        boolean hasActivatedEvent = publishedEvents.stream()
                .anyMatch(e -> e.getEventType() == EventType.ROTATION_ACTIVATED);
        assertTrue(hasActivatedEvent, "Must publish ROTATION_ACTIVATED event upon activation");

        DomainEvent activatedEvent = publishedEvents.stream()
                .filter(e -> e.getEventType() == EventType.ROTATION_ACTIVATED)
                .findFirst()
                .orElseThrow();

        assertEquals(workspaceId, activatedEvent.getWorkspaceId());
        assertEquals(secretId, activatedEvent.getSecretId());
        assertEquals(2, activatedEvent.getMetadata().get("newVersionNumber"));
        // secretName is safely redacted by BaseDomainEvent sanitization rule
        assertEquals("[REDACTED]", activatedEvent.getMetadata().get("secretName"));

        // Critical Security Invariant: Event metadata MUST NOT contain plaintext secret value
        for (Map.Entry<String, Object> entry : activatedEvent.getMetadata().entrySet()) {
            assertNotEquals("generated-plain-secret-v2", entry.getValue(), "Plaintext secret leaked into event metadata: " + entry.getKey());
        }
    }

    @Test
    @DisplayName("Should publish SECRET_COMPROMISED event with CRITICAL severity upon emergency marking")
    void testSecretCompromisedEvent() {
        grantAccess(AccessPermission.SECRET_ROTATION_EMERGENCY);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(leaseRepository.findBySecretIdAndStatus(eq(secretId), any())).thenReturn(List.of());
        when(policyRepository.findBySecretId(secretId)).thenReturn(Optional.empty());
        when(jobRepository.save(any(RotationJob.class))).thenAnswer(i -> {
            RotationJob j = i.getArgument(0);
            if (j.getId() == null) j.setId(UUID.randomUUID());
            return j;
        });

        MarkCompromisedRequest req = new MarkCompromisedRequest("Secret exposed on public GitHub repository", true, true);
        rotationService.markCompromised(workspaceId, secretId, req, actorId);

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publish(eventCaptor.capture());

        List<DomainEvent> events = eventCaptor.getAllValues();
        DomainEvent compromiseEvent = events.stream()
                .filter(e -> e.getEventType() == EventType.SECRET_COMPROMISED)
                .findFirst()
                .orElseThrow();

        assertEquals(workspaceId, compromiseEvent.getWorkspaceId());
        assertEquals(secretId, compromiseEvent.getSecretId());
        assertEquals(com.secretvault.events.model.EventSeverity.CRITICAL, compromiseEvent.getSeverity());
    }
}
