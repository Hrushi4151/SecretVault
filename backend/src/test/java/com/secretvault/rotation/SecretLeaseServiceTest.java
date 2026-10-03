package com.secretvault.rotation;

import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.model.AccessSourceType;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.rotation.dto.RotationDtos.CreateSecretLeaseRequest;
import com.secretvault.rotation.dto.RotationDtos.RenewSecretLeaseRequest;
import com.secretvault.rotation.dto.RotationDtos.SecretLeaseResponse;
import com.secretvault.rotation.entity.SecretLease;
import com.secretvault.rotation.model.LeaseStatus;
import com.secretvault.rotation.repository.SecretConsumerRepository;
import com.secretvault.rotation.repository.SecretLeaseRepository;
import com.secretvault.rotation.service.SecretLeaseService;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecretLeaseServiceTest {

    @Mock
    private SecretLeaseRepository leaseRepository;

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private EffectiveAccessService effectiveAccessService;

    @Mock
    private AuditService auditService;

    @Mock
    private SecretConsumerRepository consumerRepository;

    @InjectMocks
    private SecretLeaseService leaseService;

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
        secret.setEnvironmentId(environmentId);
        secret.setStatus(SecretStatus.ACTIVE);
        secret.setCurrentVersionNumber(2);
    }

    private void grantAccess(AccessPermission permission) {
        when(effectiveAccessService.evaluateAccess(eq(workspaceId), any(), any(), any(), eq(permission), eq(actorId)))
                .thenReturn(AccessDecision.allow(permission, AccessScope.WORKSPACE, AccessSourceType.WORKSPACE_ROLE, "ROLE_ADMIN", "Granted for test"));
    }

    @Test
    @DisplayName("Should issue runtime secret lease with dynamic TTL")
    void testCreateLease() {
        grantAccess(AccessPermission.SECRET_READ);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));
        when(leaseRepository.save(any(SecretLease.class))).thenAnswer(i -> {
            SecretLease l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });

        CreateSecretLeaseRequest req = new CreateSecretLeaseRequest(
                secretId,
                null,
                null,
                3600L,
                86400L,
                "127.0.0.1",
                "curl/8.0"
        );

        SecretLeaseResponse response = leaseService.createLease(workspaceId, req, actorId);

        assertNotNull(response);
        assertEquals(LeaseStatus.ACTIVE, response.status());
        assertEquals(3600L, response.ttlSeconds());
        assertEquals(2, response.secretVersionNumber());
        verify(leaseRepository, times(1)).save(any(SecretLease.class));
    }

    @Test
    @DisplayName("Should renew active secret lease and extend expiration time")
    void testRenewLease() {
        grantAccess(AccessPermission.SECRET_READ);

        SecretLease lease = new SecretLease(workspaceId, projectId, environmentId, secretId, 2, null, actorId, null, 3600L, 86400L, "127.0.0.1", "curl/8.0");
        lease.setId(UUID.randomUUID());
        lease.setStatus(LeaseStatus.ACTIVE);
        lease.setIssuedAt(Instant.now().minusSeconds(1800));
        lease.setExpiresAt(Instant.now().plusSeconds(1800));

        when(leaseRepository.findById(lease.getId())).thenReturn(Optional.of(lease));
        when(leaseRepository.save(any(SecretLease.class))).thenReturn(lease);

        RenewSecretLeaseRequest req = new RenewSecretLeaseRequest(3600L);
        SecretLeaseResponse renewed = leaseService.renewLease(workspaceId, lease.getId(), req, actorId);

        assertNotNull(renewed);
        assertNotNull(renewed.lastRenewedAt());
        verify(leaseRepository, times(1)).save(any(SecretLease.class));
    }

    @Test
    @DisplayName("Should revoke secret lease immediately")
    void testRevokeLease() {
        grantAccess(AccessPermission.SECRET_LEASE_MANAGE);

        SecretLease lease = new SecretLease(workspaceId, projectId, environmentId, secretId, 2, null, actorId, null, 3600L, 86400L, "127.0.0.1", "curl/8.0");
        lease.setId(UUID.randomUUID());
        lease.setStatus(LeaseStatus.ACTIVE);

        when(leaseRepository.findById(lease.getId())).thenReturn(Optional.of(lease));
        when(leaseRepository.save(any(SecretLease.class))).thenReturn(lease);

        assertDoesNotThrow(() -> leaseService.revokeLease(workspaceId, lease.getId(), actorId));
        assertEquals(LeaseStatus.REVOKED, lease.getStatus());
        assertNotNull(lease.getRevokedAt());
    }

    @Test
    @DisplayName("Should automatically expire past-due leases in background worker")
    void testExpireWorker() {
        SecretLease lease = new SecretLease(workspaceId, projectId, environmentId, secretId, 2, null, actorId, null, 3600L, 86400L, "127.0.0.1", "curl/8.0");
        lease.setId(UUID.randomUUID());
        lease.setStatus(LeaseStatus.ACTIVE);
        lease.setExpiresAt(Instant.now().minusSeconds(10));

        when(leaseRepository.findByStatusAndExpiresAtLessThanEqual(eq(LeaseStatus.ACTIVE), any(Instant.class))).thenReturn(List.of(lease));
        when(leaseRepository.save(any(SecretLease.class))).thenReturn(lease);

        leaseService.expireOverdueLeases();
        assertEquals(LeaseStatus.EXPIRED, lease.getStatus());
        verify(leaseRepository, times(1)).save(lease);
    }
}
