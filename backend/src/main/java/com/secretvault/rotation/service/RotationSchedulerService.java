package com.secretvault.rotation.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.rotation.dto.RotationDtos.TriggerRotationRequest;
import com.secretvault.rotation.entity.RotationJob;
import com.secretvault.rotation.entity.RotationPolicy;
import com.secretvault.rotation.model.RotationStatus;
import com.secretvault.rotation.model.RotationStrategy;
import com.secretvault.rotation.provider.SecretRotator;
import com.secretvault.rotation.provider.SecretRotatorRegistry;
import com.secretvault.rotation.repository.RotationJobRepository;
import com.secretvault.rotation.repository.RotationPolicyRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.repository.SecretRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Distributed Background Scheduler orchestrating automated rotation triggers and grace-period decommissioning.
 */
@Service
public class RotationSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(RotationSchedulerService.class);

    private final RotationPolicyRepository policyRepository;
    private final RotationJobRepository jobRepository;
    private final SecretRepository secretRepository;
    private final RotationService rotationService;
    private final SecretRotatorRegistry rotatorRegistry;
    private final AuditService auditService;

    public RotationSchedulerService(
            RotationPolicyRepository policyRepository,
            RotationJobRepository jobRepository,
            SecretRepository secretRepository,
            RotationService rotationService,
            SecretRotatorRegistry rotatorRegistry,
            AuditService auditService) {
        this.policyRepository = policyRepository;
        this.jobRepository = jobRepository;
        this.secretRepository = secretRepository;
        this.rotationService = rotationService;
        this.rotatorRegistry = rotatorRegistry;
        this.auditService = auditService;
    }

    /**
     * Periodic runner checking due rotation policies across all workspaces.
     */
    @Scheduled(fixedDelay = 60000) // Every 1 minute
    @Transactional
    public void processDueRotations() {
        Instant now = Instant.now();
        List<RotationPolicy> duePolicies = policyRepository.findByEnabledTrueAndNextRotationDueAtLessThanEqual(now);
        if (!duePolicies.isEmpty()) {
            log.info("Found {} rotation policies due for execution", duePolicies.size());
        }

        for (RotationPolicy policy : duePolicies) {
            try {
                Secret secret = secretRepository.findById(policy.getSecretId()).orElse(null);
                if (secret == null) {
                    log.warn("Secret {} for policy {} no longer exists, disabling policy", policy.getSecretId(), policy.getId());
                    policy.setEnabled(false);
                    policyRepository.save(policy);
                    continue;
                }

                log.info("Triggering scheduled rotation for secret {} (policy {})", secret.getId(), policy.getId());
                rotationService.triggerRotation(
                        policy.getWorkspaceId(),
                        policy.getSecretId(),
                        new TriggerRotationRequest(RotationStrategy.SCHEDULED, "Scheduled automated rotation", false, false),
                        null,
                        null
                );
            } catch (Exception e) {
                log.error("Failed to execute scheduled rotation for policy {}: {}", policy.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Periodic runner decommissioning previous versions once their grace period has elapsed.
     */
    @Scheduled(fixedDelay = 30000) // Every 30 seconds
    @Transactional
    public void processGracePeriodExpirations() {
        Instant now = Instant.now();
        List<RotationJob> activeGraceJobs = jobRepository.findByStatusAndGracePeriodEndsAtLessThanEqual(RotationStatus.GRACE_PERIOD, now);

        for (RotationJob job : activeGraceJobs) {
            try {
                log.info("Grace period elapsed for rotation job {}. Revoking previous credential.", job.getId());
                RotationPolicy policy = job.getPolicyId() != null
                        ? policyRepository.findById(job.getPolicyId()).orElse(null)
                        : null;

                if (policy != null && policy.isAutoRevokePrevious()) {
                    job.setStatus(RotationStatus.REVOKING);
                    SecretRotator rotator = rotatorRegistry.getRotator(policy.getSecretType(), policy);
                    rotator.revokePrevious(null, policy, job);
                    auditService.recordSecretAudit(null, job.getWorkspaceId(), null, AuditAction.OLD_VERSION_REVOKED, job.getSecretId(), null, null, "SUCCESS");
                }

                job.setStatus(RotationStatus.COMPLETED);
                job.setCompletedAt(Instant.now());
                jobRepository.save(job);
                log.info("Rotation job {} successfully completed post-grace-period", job.getId());
            } catch (Exception e) {
                log.error("Failed to revoke previous credential for job {}: {}", job.getId(), e.getMessage(), e);
            }
        }
    }
}
