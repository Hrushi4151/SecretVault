package com.secretvault.health;

import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.health.model.SecretHealthEvaluation;
import com.secretvault.health.model.SecretHealthStatus;
import com.secretvault.health.service.SecretHealthEvaluator;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.rotation.repository.RotationPolicyRepository;
import com.secretvault.rotation.repository.SecretConsumerRepository;
import com.secretvault.rotation.repository.SecretLeaseRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.repository.SecretRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 13: Secret Health Evaluator Tests")
class SecretHealthEvaluatorTest {

    @Mock
    private SecretRepository secretRepository;
    @Mock
    private EnvironmentRepository environmentRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private RotationPolicyRepository policyRepository;
    @Mock
    private SecretLeaseRepository leaseRepository;
    @Mock
    private SecretConsumerRepository consumerRepository;
    @Mock
    private EffectiveAccessService effectiveAccessService;

    private SecretHealthEvaluator evaluator;
    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID secretId;

    @BeforeEach
    void setUp() {
        evaluator = new SecretHealthEvaluator(
                secretRepository,
                environmentRepository,
                projectRepository,
                policyRepository,
                leaseRepository,
                consumerRepository,
                effectiveAccessService
        );

        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        secretId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Evaluate healthy active secret with no risk factors")
    void testEvaluateHealthySecret() {
        Secret secret = new Secret();
        secret.setId(secretId);
        secret.setName("API_KEY");
        secret.setEnvironmentId(environmentId);
        secret.setStatus(SecretStatus.ACTIVE);

        Environment env = new Environment();
        env.setId(environmentId);
        env.setProjectId(projectId);
        env.setName("Production");

        Project project = new Project();
        project.setWorkspaceId(workspaceId);

        when(secretRepository.findById(secretId)).thenReturn(Optional.of(secret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(env));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(effectiveAccessService.evaluateAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(AccessDecision.allow("Allowed"));
        when(policyRepository.findBySecretId(secretId)).thenReturn(Optional.empty());
        when(consumerRepository.findByEnvironmentId(environmentId)).thenReturn(List.of());
        when(leaseRepository.findBySecretIdAndStatus(secretId, com.secretvault.rotation.model.LeaseStatus.ACTIVE)).thenReturn(List.of());

        SecretHealthEvaluation eval = evaluator.evaluateSecret(workspaceId, secretId, UUID.randomUUID());

        assertThat(eval).isNotNull();
        assertThat(eval.secretName()).isEqualTo("API_KEY");
        assertThat(eval.healthScore()).isGreaterThanOrEqualTo(75);
        assertThat(eval.status()).isIn(SecretHealthStatus.HEALTHY, SecretHealthStatus.WARNING);
    }
}
