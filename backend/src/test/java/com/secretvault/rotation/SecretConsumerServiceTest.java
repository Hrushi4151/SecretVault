package com.secretvault.rotation;

import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.model.AccessSourceType;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.rotation.dto.RotationDtos.ConsumerHeartbeatRequest;
import com.secretvault.rotation.dto.RotationDtos.RegisterSecretConsumerRequest;
import com.secretvault.rotation.dto.RotationDtos.SecretConsumerResponse;
import com.secretvault.rotation.entity.SecretConsumer;
import com.secretvault.rotation.model.ConsumerStatus;
import com.secretvault.rotation.model.ConsumerType;
import com.secretvault.rotation.repository.SecretConsumerRepository;
import com.secretvault.rotation.service.SecretConsumerService;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecretConsumerServiceTest {

    @Mock
    private SecretConsumerRepository consumerRepository;

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private EffectiveAccessService effectiveAccessService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private SecretConsumerService consumerService;

    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID secretId;
    private UUID actorId;
    private Workspace workspace;
    private Project project;
    private Environment environment;
    private Secret secret;

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
        secret.setName("AUTH_SIGNING_KEY");
        secret.setEnvironmentId(environmentId);
        secret.setStatus(SecretStatus.ACTIVE);
        secret.setCurrentVersionNumber(3);
    }

    private void grantAccess(AccessPermission permission) {
        when(effectiveAccessService.evaluateAccess(eq(workspaceId), any(), any(), any(), eq(permission), eq(actorId)))
                .thenReturn(AccessDecision.allow(permission, AccessScope.WORKSPACE, AccessSourceType.WORKSPACE_ROLE, "ROLE_ADMIN", "Granted for test"));
    }

    @Test
    @DisplayName("Should register workload consumer and save consumer details")
    void testRegisterConsumer() {
        grantAccess(AccessPermission.CONSUMER_MANAGE);

        when(consumerRepository.findByWorkspaceIdAndName(workspaceId, "order-processing-service")).thenReturn(Optional.empty());
        when(consumerRepository.save(any(SecretConsumer.class))).thenAnswer(i -> {
            SecretConsumer c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        RegisterSecretConsumerRequest req = new RegisterSecretConsumerRequest(
                "order-processing-service",
                ConsumerType.APPLICATION,
                null,
                "inst-1",
                "prod-worker-node-1",
                "1.0.0",
                "Spring Boot 3.3.0",
                true,
                false
        );

        SecretConsumerResponse response = consumerService.registerConsumer(workspaceId, projectId, environmentId, req, actorId);

        assertNotNull(response);
        assertEquals("order-processing-service", response.name());
        assertTrue(response.supportsDynamicRefresh());
        verify(consumerRepository, times(1)).save(any(SecretConsumer.class));
    }

    @Test
    @DisplayName("Should process SDK heartbeat and acknowledge latest secret version")
    void testHeartbeat() {
        SecretConsumer consumer = new SecretConsumer(workspaceId, projectId, environmentId, "payment-gateway", ConsumerType.SERVICE, null, "inst-pg");
        consumer.setId(UUID.randomUUID());
        consumer.setStatus(ConsumerStatus.ACTIVE);

        when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
        when(consumerRepository.save(any(SecretConsumer.class))).thenReturn(consumer);

        ConsumerHeartbeatRequest req = new ConsumerHeartbeatRequest(4, "1.0.0", "Node.js");
        SecretConsumerResponse res = consumerService.heartbeat(workspaceId, consumer.getId(), req);

        assertNotNull(res);
        assertEquals(4, res.currentAcknowledgedVersion());
        assertNotNull(consumer.getLastHeartbeatAt());
        verify(consumerRepository, times(1)).save(consumer);
    }

    @Test
    @DisplayName("Should mark stale consumers that missed heartbeat threshold")
    void testDetectStaleConsumers() {
        SecretConsumer consumer = new SecretConsumer(workspaceId, projectId, environmentId, "legacy-sync", ConsumerType.CLI_PROCESS, null, "inst-ls");
        consumer.setId(UUID.randomUUID());
        consumer.setStatus(ConsumerStatus.ACTIVE);
        consumer.setLastHeartbeatAt(Instant.now().minusSeconds(86400 * 2)); // 2 days old

        when(consumerRepository.findAll()).thenReturn(List.of(consumer));
        when(consumerRepository.save(any(SecretConsumer.class))).thenReturn(consumer);

        consumerService.detectStaleConsumers();
        assertEquals(ConsumerStatus.STALE, consumer.getStatus());
        verify(consumerRepository, times(1)).save(consumer);
    }
}
