package com.secretvault.rotation;

import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.model.AccessSourceType;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.rotation.dto.RotationDtos.RotationImpactResponse;
import com.secretvault.rotation.entity.SecretConsumer;
import com.secretvault.rotation.entity.SecretDependency;
import com.secretvault.rotation.entity.SecretLease;
import com.secretvault.rotation.model.ConsumerStatus;
import com.secretvault.rotation.model.ConsumerType;
import com.secretvault.rotation.model.LeaseStatus;
import com.secretvault.rotation.repository.SecretConsumerRepository;
import com.secretvault.rotation.repository.SecretDependencyRepository;
import com.secretvault.rotation.repository.SecretLeaseRepository;
import com.secretvault.rotation.service.RotationImpactService;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.repository.SecretRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RotationImpactServiceTest {

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private SecretDependencyRepository dependencyRepository;

    @Mock
    private SecretConsumerRepository consumerRepository;

    @Mock
    private SecretLeaseRepository leaseRepository;

    @Mock
    private EffectiveAccessService effectiveAccessService;

    @InjectMocks
    private RotationImpactService impactService;

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
        environment.setName("production");

        secret = new Secret();
        secret.setId(secretId);
        secret.setName("STRIPE_API_KEY");
        secret.setEnvironmentId(environmentId);
        secret.setStatus(SecretStatus.ACTIVE);
        secret.setCurrentVersionNumber(2);
    }

    private void grantAccess(AccessPermission permission) {
        when(effectiveAccessService.evaluateAccess(eq(workspaceId), any(), any(), any(), eq(permission), eq(actorId)))
                .thenReturn(AccessDecision.allow(permission, AccessScope.WORKSPACE, AccessSourceType.WORKSPACE_ROLE, "ROLE_ADMIN", "Granted for test"));
    }

    @Test
    @DisplayName("Should accurately calculate live consumer impact and restart requirements")
    void testCalculateImpact() {
        grantAccess(AccessPermission.SECRET_ROTATION_READ);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        SecretConsumer c1 = new SecretConsumer(workspaceId, projectId, environmentId, "checkout-service", ConsumerType.APPLICATION, null, "inst-1");
        c1.setId(UUID.randomUUID());
        c1.setStatus(ConsumerStatus.ACTIVE);
        c1.setSupportsDynamicRefresh(true);
        c1.setRequiresRestart(false);

        SecretConsumer c2 = new SecretConsumer(workspaceId, projectId, environmentId, "billing-cron", ConsumerType.CLI_PROCESS, null, "inst-2");
        c2.setId(UUID.randomUUID());
        c2.setStatus(ConsumerStatus.STALE);
        c2.setSupportsDynamicRefresh(false);
        c2.setRequiresRestart(true);

        SecretDependency d1 = new SecretDependency(workspaceId, c1.getId(), secretId, "stripe_key", true);
        SecretDependency d2 = new SecretDependency(workspaceId, c2.getId(), secretId, "stripe_key", true);

        SecretLease activeLease = new SecretLease(workspaceId, projectId, environmentId, secretId, 2, null, actorId, c1.getId(), 3600L, 86400L, "127.0.0.1", "agent");

        when(dependencyRepository.findBySecretId(secretId)).thenReturn(List.of(d1, d2));
        when(consumerRepository.findAllById(any())).thenReturn(List.of(c1, c2));
        when(leaseRepository.findBySecretIdAndStatus(secretId, LeaseStatus.ACTIVE)).thenReturn(List.of(activeLease));

        RotationImpactResponse impact = impactService.calculateImpact(workspaceId, secretId, actorId);

        assertNotNull(impact);
        assertEquals("STRIPE_API_KEY", impact.secretName());
        assertEquals(2, impact.totalAffectedConsumers());
        assertEquals(1, impact.dynamicRefreshCount());
        assertEquals(1, impact.restartRequiredCount());
        assertEquals(1, impact.activeLeasesCount());
    }
}
