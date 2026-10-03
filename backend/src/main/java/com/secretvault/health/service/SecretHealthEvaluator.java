package com.secretvault.health.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.health.model.RiskFactor;
import com.secretvault.health.model.SecretHealthEvaluation;
import com.secretvault.health.model.SecretHealthStatus;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.rotation.entity.RotationPolicy;
import com.secretvault.rotation.entity.SecretConsumer;
import com.secretvault.rotation.model.ConsumerStatus;
import com.secretvault.rotation.model.LeaseStatus;
import com.secretvault.rotation.repository.RotationPolicyRepository;
import com.secretvault.rotation.repository.SecretConsumerRepository;
import com.secretvault.rotation.repository.SecretLeaseRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.repository.SecretRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SecretHealthEvaluator {

    private final SecretRepository secretRepository;
    private final EnvironmentRepository environmentRepository;
    private final ProjectRepository projectRepository;
    private final RotationPolicyRepository policyRepository;
    private final SecretLeaseRepository leaseRepository;
    private final SecretConsumerRepository consumerRepository;
    private final EffectiveAccessService effectiveAccessService;

    public SecretHealthEvaluator(
            SecretRepository secretRepository,
            EnvironmentRepository environmentRepository,
            ProjectRepository projectRepository,
            RotationPolicyRepository policyRepository,
            SecretLeaseRepository leaseRepository,
            SecretConsumerRepository consumerRepository,
            EffectiveAccessService effectiveAccessService
    ) {
        this.secretRepository = secretRepository;
        this.environmentRepository = environmentRepository;
        this.projectRepository = projectRepository;
        this.policyRepository = policyRepository;
        this.leaseRepository = leaseRepository;
        this.consumerRepository = consumerRepository;
        this.effectiveAccessService = effectiveAccessService;
    }

    @Transactional(readOnly = true)
    public SecretHealthEvaluation evaluateSecret(UUID workspaceId, UUID secretId, UUID actorId) {
        Secret secret = secretRepository.findById(secretId)
                .orElseThrow(() -> ApiException.notFound("Secret not found"));

        Environment env = environmentRepository.findById(secret.getEnvironmentId())
                .orElseThrow(() -> ApiException.notFound("Environment not found"));

        Project project = projectRepository.findById(env.getProjectId())
                .orElseThrow(() -> ApiException.notFound("Project not found"));

        if (!project.getWorkspaceId().equals(workspaceId)) {
            throw ApiException.forbidden("Secret does not belong to this workspace");
        }

        if (actorId != null) {
            var decision = effectiveAccessService.evaluateAccess(workspaceId, env.getProjectId(), env.getId(), secretId, AccessPermission.SECRET_READ, actorId);
            if (!decision.allowed()) {
                throw ApiException.forbidden("Access denied: missing SECRET_READ permission");
            }
        }

        List<RiskFactor> risks = new ArrayList<>();
        int healthScore = 100;
        Instant now = Instant.now();

        // 1. Status check
        if (secret.getStatus() == SecretStatus.COMPROMISED) {
            risks.add(new RiskFactor("SECRET_COMPROMISED", "Secret marked COMPROMISED", "Secret is compromised and requires immediate rotation", "CRITICAL", "Execute emergency rotation immediately"));
            healthScore -= 80;
        }

        // 2. Rotation Policy Check
        Optional<RotationPolicy> policyOpt = policyRepository.findBySecretId(secretId);
        boolean rotationConfigured = policyOpt.isPresent() && policyOpt.get().isEnabled();
        Instant lastRotatedAt = null;
        Instant nextRotationDueAt = null;

        if (policyOpt.isPresent()) {
            RotationPolicy policy = policyOpt.get();
            lastRotatedAt = policy.getLastRotatedAt();
            nextRotationDueAt = policy.getNextRotationDueAt();

            if (policy.isEnabled() && policy.getNextRotationDueAt() != null && policy.getNextRotationDueAt().isBefore(now)) {
                long overdueDays = Duration.between(policy.getNextRotationDueAt(), now).toDays();
                risks.add(new RiskFactor(
                        "ROTATION_OVERDUE",
                        "Secret rotation is overdue by " + overdueDays + " days",
                        "Automated rotation schedule missed deadline",
                        overdueDays > 7 ? "HIGH" : "MEDIUM",
                        "Trigger secret rotation manually or verify rotation worker"
                ));
                healthScore -= overdueDays > 7 ? 40 : 20;
            }
        } else {
            // Check secret age if no rotation policy
            long ageDays = Duration.between(secret.getCreatedAt(), now).toDays();
            if (ageDays > 90) {
                risks.add(new RiskFactor(
                        "NO_ROTATION_POLICY",
                        "Secret is " + ageDays + " days old with no rotation policy configured",
                        "Long-lived secrets without rotation increase blast radius of potential exposure",
                        "MEDIUM",
                        "Configure an automated rotation policy"
                ));
                healthScore -= 25;
            }
        }

        // 3. Stale Consumers Check
        List<SecretConsumer> consumers = consumerRepository.findByEnvironmentId(env.getId());
        long activeConsumers = 0;
        long staleConsumers = 0;
        for (SecretConsumer c : consumers) {
            if (c.getStatus() == ConsumerStatus.ACTIVE) {
                activeConsumers++;
            } else if (c.getStatus() == ConsumerStatus.STALE) {
                staleConsumers++;
            }
        }

        if (staleConsumers > 0) {
            risks.add(new RiskFactor(
                    "STALE_CONSUMERS",
                    staleConsumers + " consumer(s) are stale or missed heartbeats",
                    "Consumers may not be receiving secret version changes",
                    "MEDIUM",
                    "Inspect consumer instances and ensure heartbeat service is running"
            ));
            healthScore -= (int) Math.min(30, staleConsumers * 10);
        }

        // 4. Leases Check
        long activeLeaseCount = leaseRepository.findBySecretIdAndStatus(secretId, LeaseStatus.ACTIVE).size();

        healthScore = Math.max(0, Math.min(100, healthScore));

        SecretHealthStatus status = SecretHealthStatus.HEALTHY;
        if (healthScore < 40) {
            status = SecretHealthStatus.CRITICAL;
        } else if (healthScore < 70) {
            status = SecretHealthStatus.DEGRADED;
        } else if (healthScore < 90) {
            status = SecretHealthStatus.WARNING;
        }

        return new SecretHealthEvaluation(
                secret.getId(),
                secret.getName(),
                workspaceId,
                env.getId(),
                env.getName(),
                status,
                healthScore,
                now,
                risks,
                rotationConfigured,
                lastRotatedAt,
                nextRotationDueAt,
                activeLeaseCount,
                activeConsumers
        );
    }
}
