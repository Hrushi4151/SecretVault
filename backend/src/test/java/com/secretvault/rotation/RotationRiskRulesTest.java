package com.secretvault.rotation;

import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.machine.repository.MachineIdentityRepository;
import com.secretvault.rotation.entity.RotationJob;
import com.secretvault.rotation.entity.RotationPolicy;
import com.secretvault.rotation.entity.SecretLease;
import com.secretvault.rotation.model.LeaseStatus;
import com.secretvault.rotation.model.RotationStatus;
import com.secretvault.rotation.model.RotationStrategy;
import com.secretvault.rotation.repository.RotationJobRepository;
import com.secretvault.rotation.repository.RotationPolicyRepository;
import com.secretvault.rotation.repository.SecretConsumerRepository;
import com.secretvault.rotation.repository.SecretLeaseRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.security.engine.rules.RotationRiskRule;
import com.secretvault.security.engine.rules.SecretLeaseRiskRule;
import com.secretvault.security.finding.dto.SecurityFindingDraft;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.workspace.entity.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RotationRiskRulesTest {

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private RotationPolicyRepository policyRepository;

    @Mock
    private RotationJobRepository jobRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private SecretLeaseRepository leaseRepository;

    @Mock
    private SecretConsumerRepository consumerRepository;

    @Mock
    private MachineIdentityRepository machineRepository;

    private RotationRiskRule rotationRiskRule;
    private SecretLeaseRiskRule secretLeaseRiskRule;

    private UUID workspaceId;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        rotationRiskRule = new RotationRiskRule(secretRepository, policyRepository, jobRepository, environmentRepository);
        secretLeaseRiskRule = new SecretLeaseRiskRule(leaseRepository, consumerRepository, machineRepository, secretRepository);

        workspaceId = UUID.randomUUID();
        workspace = new Workspace();
        workspace.setId(workspaceId);
    }

    @Test
    @DisplayName("RotationRiskRule should detect overdue secret rotation")
    void testDetectOverdueSecretRotation() {
        UUID envId = UUID.randomUUID();
        Environment prodEnv = new Environment();
        prodEnv.setId(envId);
        prodEnv.setProjectId(UUID.randomUUID());
        prodEnv.setEnvType(EnvType.PRODUCTION);

        Secret secret = new Secret();
        secret.setId(UUID.randomUUID());
        secret.setName("PROD_DATABASE_KEY");
        secret.setEnvironmentId(envId);
        secret.setStatus(SecretStatus.ACTIVE);

        RotationPolicy policy = new RotationPolicy();
        policy.setId(UUID.randomUUID());
        policy.setWorkspaceId(workspaceId);
        policy.setSecretId(secret.getId());
        policy.setEnabled(true);
        policy.setNextRotationDueAt(Instant.now().minusSeconds(3600)); // Overdue 1 hour

        when(policyRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(policy));
        when(secretRepository.findById(secret.getId())).thenReturn(Optional.of(secret));
        when(environmentRepository.findById(envId)).thenReturn(Optional.of(prodEnv));
        when(jobRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of());

        List<SecurityFindingDraft> findings = rotationRiskRule.evaluate(workspace, Instant.now());

        assertNotNull(findings);
        assertFalse(findings.isEmpty());
        assertTrue(findings.stream().anyMatch(f -> f.title().contains("Overdue Secret Rotation")));
        assertEquals(FindingCategory.SECRET_ROTATION_RISK, findings.get(0).category());
    }

    @Test
    @DisplayName("RotationRiskRule should detect failed rotation jobs")
    void testDetectFailedRotationJob() {
        RotationJob failedJob = new RotationJob(workspaceId, UUID.randomUUID(), null, RotationStrategy.SCHEDULED, null);
        failedJob.setId(UUID.randomUUID());
        failedJob.setStatus(RotationStatus.FAILED);
        failedJob.setErrorMessage("Target database connection timed out during validation probe");

        when(policyRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of());
        when(jobRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(failedJob));

        List<SecurityFindingDraft> findings = rotationRiskRule.evaluate(workspace, Instant.now());

        assertNotNull(findings);
        assertEquals(1, findings.size());
        assertTrue(findings.get(0).title().contains("Failed Secret Rotation Job"));
    }

    @Test
    @DisplayName("SecretLeaseRiskRule should detect excessively long lease lifetimes")
    void testDetectExcessiveLeaseLifetime() {
        UUID secretId = UUID.randomUUID();

        SecretLease lease = new SecretLease(workspaceId, UUID.randomUUID(), UUID.randomUUID(), secretId, 1, null, null, null, 3600L, 86400L * 10, "127.0.0.1", "agent");
        lease.setId(UUID.randomUUID());
        lease.setStatus(LeaseStatus.ACTIVE);

        when(leaseRepository.findByWorkspaceIdAndStatus(workspaceId, LeaseStatus.ACTIVE)).thenReturn(List.of(lease));
        when(consumerRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of());

        List<SecurityFindingDraft> findings = secretLeaseRiskRule.evaluate(workspace, Instant.now());

        assertNotNull(findings);
        assertEquals(1, findings.size());
        assertTrue(findings.get(0).title().contains("Excessively Long Secret Lease Lifetime"));
        assertEquals(FindingCategory.SECRET_LEASE_RISK, findings.get(0).category());
    }
}
