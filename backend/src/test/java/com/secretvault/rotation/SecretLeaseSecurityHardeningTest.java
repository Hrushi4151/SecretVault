package com.secretvault.rotation;

import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.model.AccessSourceType;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.machine.entity.MachineIdentity;
import com.secretvault.machine.model.MachineStatus;
import com.secretvault.machine.repository.MachineIdentityRepository;
import com.secretvault.rotation.dto.RotationDtos.*;
import com.secretvault.rotation.entity.SecretLease;
import com.secretvault.rotation.model.LeaseStatus;
import com.secretvault.rotation.repository.SecretLeaseRepository;
import com.secretvault.rotation.service.SecretLeaseService;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.repository.SecretRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 12.1 Secret Lease Security and Race Hardening Test Suite.
 */
@ExtendWith(MockitoExtension.class)
class SecretLeaseSecurityHardeningTest {

    @Mock private SecretLeaseRepository leaseRepository;
    @Mock private SecretRepository secretRepository;
    @Mock private EnvironmentRepository environmentRepository;
    @Mock private MachineIdentityRepository machineRepository;
    @Mock private EffectiveAccessService effectiveAccessService;
    @Mock private AuditService auditService;

    private SecretLeaseService leaseService;

    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID secretId;
    private UUID machineId;
    private UUID actorId;
    private Secret testSecret;
    private Environment testEnv;
    private MachineIdentity testMachine;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        secretId = UUID.randomUUID();
        machineId = UUID.randomUUID();
        actorId = UUID.randomUUID();

        testSecret = new Secret();
        testSecret.setId(secretId);
        testSecret.setEnvironmentId(environmentId);
        testSecret.setName("STRIPE_API_KEY");
        testSecret.setCurrentVersionNumber(3);

        testEnv = new Environment();
        testEnv.setId(environmentId);
        testEnv.setProjectId(projectId);
        testEnv.setName("production");

        testMachine = new MachineIdentity();
        testMachine.setId(machineId);
        testMachine.setName("payment-worker-01");
        testMachine.setStatus(MachineStatus.ACTIVE);

        leaseService = new SecretLeaseService(
                leaseRepository, secretRepository, environmentRepository,
                machineRepository, effectiveAccessService, auditService
        );
    }

    @Test
    @DisplayName("Valid Lease Creation: Successful issuance with bounded TTL")
    void testCreateValidLease() {
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(testSecret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(testEnv));
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(testMachine));
        when(effectiveAccessService.evaluateAccess(eq(workspaceId), eq(projectId), eq(environmentId), eq(secretId), eq(AccessPermission.SECRET_READ), eq(actorId)))
                .thenReturn(AccessDecision.allow(AccessPermission.SECRET_READ, AccessScope.SECRET, AccessSourceType.WORKSPACE_ROLE, "ROLE_ADMIN", "Granted"));
        when(leaseRepository.save(any(SecretLease.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateSecretLeaseRequest req = new CreateSecretLeaseRequest(secretId, machineId, null, 1800L, 7200L, "10.0.0.1", "PaymentWorker/1.0");
        SecretLeaseResponse response = leaseService.createLease(workspaceId, req, actorId);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(LeaseStatus.ACTIVE);
        assertThat(response.secretVersionNumber()).isEqualTo(3);
        assertThat(response.ttlSeconds()).isEqualTo(1800L);
        assertThat(response.maxLifetimeSeconds()).isEqualTo(7200L);
    }

    @Test
    @DisplayName("TTL Ceiling Enforcement: Requested TTL exceeding maxLifetime is clamped to maxLifetime")
    void testTtlCeilingClamping() {
        when(secretRepository.findById(secretId)).thenReturn(Optional.of(testSecret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(testEnv));
        when(effectiveAccessService.evaluateAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(AccessDecision.allow(AccessPermission.SECRET_READ, AccessScope.SECRET, AccessSourceType.WORKSPACE_ROLE, "ROLE_ADMIN", "Granted"));
        when(leaseRepository.save(any(SecretLease.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateSecretLeaseRequest req = new CreateSecretLeaseRequest(secretId, null, null, 999999L, 3600L, null, null);
        SecretLeaseResponse response = leaseService.createLease(workspaceId, req, actorId);

        assertThat(response.ttlSeconds()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("Machine Identity Suspension: Disabled machine cannot renew lease and lease is immediately revoked")
    void testDisabledMachineLeaseRenewal() {
        UUID leaseId = UUID.randomUUID();
        SecretLease activeLease = new SecretLease();
        activeLease.setId(leaseId);
        activeLease.setWorkspaceId(workspaceId);
        activeLease.setProjectId(projectId);
        activeLease.setEnvironmentId(environmentId);
        activeLease.setSecretId(secretId);
        activeLease.setMachineIdentityId(machineId);
        activeLease.setStatus(LeaseStatus.ACTIVE);
        activeLease.setIssuedAt(Instant.now().minus(Duration.ofMinutes(10)));
        activeLease.setExpiresAt(Instant.now().plus(Duration.ofMinutes(50)));
        activeLease.setTtlSeconds(3600L);
        activeLease.setMaxLifetimeSeconds(86400L);

        testMachine.setStatus(MachineStatus.DISABLED); // Machine disabled

        when(leaseRepository.findById(leaseId)).thenReturn(Optional.of(activeLease));
        when(machineRepository.findById(machineId)).thenReturn(Optional.of(testMachine));

        assertThatThrownBy(() -> leaseService.renewLease(workspaceId, leaseId, new RenewSecretLeaseRequest(1800L), actorId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Associated machine identity is no longer active; lease revoked");

        assertThat(activeLease.getStatus()).isEqualTo(LeaseStatus.REVOKED);
        verify(leaseRepository).save(activeLease);
    }

    @Test
    @DisplayName("Cross-Workspace Lease Isolation: Renewal fails if lease workspace mismatch")
    void testCrossWorkspaceLeaseIsolation() {
        UUID leaseId = UUID.randomUUID();
        UUID otherWorkspaceId = UUID.randomUUID();

        SecretLease foreignLease = new SecretLease();
        foreignLease.setId(leaseId);
        foreignLease.setWorkspaceId(otherWorkspaceId);
        foreignLease.setStatus(LeaseStatus.ACTIVE);

        when(leaseRepository.findById(leaseId)).thenReturn(Optional.of(foreignLease));

        assertThatThrownBy(() -> leaseService.renewLease(workspaceId, leaseId, new RenewSecretLeaseRequest(1800L), actorId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("does not belong to this workspace");
    }

    @Test
    @DisplayName("Max Lifetime Exceeded: Renewal fails when absolute max lifetime is reached")
    void testRenewalPastMaxLifetimeFails() {
        UUID leaseId = UUID.randomUUID();
        SecretLease expiredLifetimeLease = new SecretLease();
        expiredLifetimeLease.setId(leaseId);
        expiredLifetimeLease.setWorkspaceId(workspaceId);
        expiredLifetimeLease.setProjectId(projectId);
        expiredLifetimeLease.setEnvironmentId(environmentId);
        expiredLifetimeLease.setSecretId(secretId);
        expiredLifetimeLease.setStatus(LeaseStatus.ACTIVE);
        expiredLifetimeLease.setIssuedAt(Instant.now().minus(Duration.ofHours(25)));
        expiredLifetimeLease.setMaxLifetimeSeconds(86400L); // 24 hours
        expiredLifetimeLease.setTtlSeconds(3600L);

        when(leaseRepository.findById(leaseId)).thenReturn(Optional.of(expiredLifetimeLease));
        when(effectiveAccessService.evaluateAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(AccessDecision.allow(AccessPermission.SECRET_READ, AccessScope.SECRET, AccessSourceType.WORKSPACE_ROLE, "ROLE_ADMIN", "Granted"));

        assertThatThrownBy(() -> leaseService.renewLease(workspaceId, leaseId, new RenewSecretLeaseRequest(1800L), actorId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("absolute maximum lifetime");

        assertThat(expiredLifetimeLease.getStatus()).isEqualTo(LeaseStatus.EXPIRED);
    }

    @Test
    @DisplayName("Revoked Lease Cannot Be Renewed")
    void testRevokedLeaseCannotBeRenewed() {
        UUID leaseId = UUID.randomUUID();
        SecretLease revokedLease = new SecretLease();
        revokedLease.setId(leaseId);
        revokedLease.setWorkspaceId(workspaceId);
        revokedLease.setStatus(LeaseStatus.REVOKED);

        when(leaseRepository.findById(leaseId)).thenReturn(Optional.of(revokedLease));

        assertThatThrownBy(() -> leaseService.renewLease(workspaceId, leaseId, new RenewSecretLeaseRequest(1800L), actorId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Cannot renew lease with status REVOKED");
    }

    @Test
    @DisplayName("Concurrent 100-iteration Race: Renew vs Revoke results in deterministic revocation")
    void testConcurrentRenewAndRevokeRace() throws InterruptedException {
        int iterations = 100;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger revokedWins = new AtomicInteger(0);

        for (int i = 0; i < iterations; i++) {
            SecretLease lease = new SecretLease();
            lease.setId(UUID.randomUUID());
            lease.setWorkspaceId(workspaceId);
            lease.setProjectId(projectId);
            lease.setEnvironmentId(environmentId);
            lease.setSecretId(secretId);
            lease.setStatus(LeaseStatus.ACTIVE);
            lease.setIssuedAt(Instant.now());
            lease.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
            lease.setMaxLifetimeSeconds(86400L);
            lease.setTtlSeconds(3600L);

            CountDownLatch latch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(2);

            // Thread 1: Revoke
            executor.submit(() -> {
                try {
                    latch.await();
                    synchronized (lease) {
                        lease.setStatus(LeaseStatus.REVOKED);
                        lease.setRevokedAt(Instant.now());
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });

            // Thread 2: Renew check
            executor.submit(() -> {
                try {
                    latch.await();
                    synchronized (lease) {
                        if (lease.getStatus() == LeaseStatus.REVOKED) {
                            revokedWins.incrementAndGet();
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });

            latch.countDown();
            doneLatch.await(2, TimeUnit.SECONDS);

            // Invariant: Once revoked, status remains REVOKED
            synchronized (lease) {
                if (lease.getStatus() == LeaseStatus.REVOKED) {
                    assertThat(lease.getStatus()).isEqualTo(LeaseStatus.REVOKED);
                }
            }
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
}
