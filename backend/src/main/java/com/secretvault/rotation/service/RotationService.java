package com.secretvault.rotation.service;

import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.rotation.dto.RotationDtos.*;
import com.secretvault.rotation.engine.RotationValidationEngine;
import com.secretvault.rotation.entity.*;
import com.secretvault.rotation.model.RolloutStrategy;
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
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.entity.VersionType;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import com.secretvault.secret.service.SecretAuthorizationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * Core Service managing Secret Rotation Lifecycle, Policies, Execution, Validation,
 * Rollout, Rollback, and Compromised Secret Remediation.
 */
@Service
public class RotationService {

    private static final Logger log = LoggerFactory.getLogger(RotationService.class);

    private final RotationPolicyRepository policyRepository;
    private final RotationJobRepository jobRepository;
    private final RotationAttemptRepository attemptRepository;
    private final SecretRepository secretRepository;
    private final SecretVersionRepository versionRepository;
    private final EnvironmentRepository environmentRepository;
    private final SecretLeaseRepository leaseRepository;
    private final EncryptionService encryptionService;
    private final SecretRotatorRegistry rotatorRegistry;
    private final RotationValidationEngine validationEngine;
    private final RotationDistributedLock distributedLock;
    private final EffectiveAccessService effectiveAccessService;
    private final AuditService auditService;

    public RotationService(
            RotationPolicyRepository policyRepository,
            RotationJobRepository jobRepository,
            RotationAttemptRepository attemptRepository,
            SecretRepository secretRepository,
            SecretVersionRepository versionRepository,
            EnvironmentRepository environmentRepository,
            SecretLeaseRepository leaseRepository,
            EncryptionService encryptionService,
            SecretRotatorRegistry rotatorRegistry,
            RotationValidationEngine validationEngine,
            RotationDistributedLock distributedLock,
            EffectiveAccessService effectiveAccessService,
            AuditService auditService) {
        this.policyRepository = policyRepository;
        this.jobRepository = jobRepository;
        this.attemptRepository = attemptRepository;
        this.secretRepository = secretRepository;
        this.versionRepository = versionRepository;
        this.environmentRepository = environmentRepository;
        this.leaseRepository = leaseRepository;
        this.encryptionService = encryptionService;
        this.rotatorRegistry = rotatorRegistry;
        this.validationEngine = validationEngine;
        this.distributedLock = distributedLock;
        this.effectiveAccessService = effectiveAccessService;
        this.auditService = auditService;
    }

    // ==========================================
    // ROTATION POLICY MANAGEMENT
    // ==========================================

    @Transactional
    public RotationPolicyResponse createPolicy(
            UUID workspaceId, UUID projectId, UUID environmentId, UUID secretId,
            CreateRotationPolicyRequest req, UUID actorId) {

        verifyAccess(workspaceId, projectId, environmentId, secretId, actorId, AccessPermission.SECRET_ROTATION_CREATE);

        Secret secret = secretRepository.findByIdAndEnvironmentId(secretId, environmentId)
                .orElseThrow(() -> ApiException.notFound("Secret not found"));

        if (policyRepository.findBySecretId(secretId).isPresent()) {
            throw ApiException.conflict("Rotation policy already exists for this secret");
        }

        RotationPolicy policy = new RotationPolicy();
        policy.setId(UUID.randomUUID());
        policy.setWorkspaceId(workspaceId);
        policy.setSecretId(secretId);
        policy.setCreatedBy(actorId);
        policy.setEnabled(req.enabled());
        policy.setStrategy(req.strategy() != null ? req.strategy() : RotationStrategy.SCHEDULED);
        policy.setSecretType(req.secretType() != null ? req.secretType() : SecretType.PASSWORD);
        policy.setIntervalSeconds(req.intervalSeconds() > 0 ? req.intervalSeconds() : 2592000L);
        policy.setMinIntervalSeconds(req.minIntervalSeconds() > 0 ? req.minIntervalSeconds() : 3600L);
        policy.setMaxSecretAgeSeconds(req.maxSecretAgeSeconds());
        policy.setRotationWindowSeconds(req.rotationWindowSeconds() > 0 ? req.rotationWindowSeconds() : 86400L);
        policy.setCronExpression(req.cronExpression());
        policy.setTimezone(req.timezone() != null ? req.timezone() : "UTC");
        policy.setMaxRetries(req.maxRetries() > 0 ? req.maxRetries() : 3);
        policy.setRetryBackoffSeconds(req.retryBackoffSeconds() > 0 ? req.retryBackoffSeconds() : 300);
        policy.setValidationType(req.validationType() != null ? req.validationType() : ValidationType.AUTHENTICATION);
        policy.setRolloutStrategy(req.rolloutStrategy() != null ? req.rolloutStrategy() : RolloutStrategy.STAGED);
        policy.setGracePeriodSeconds(req.gracePeriodSeconds() >= 0 ? req.gracePeriodSeconds() : 1800L);
        policy.setAutoRevokePrevious(req.autoRevokePrevious());
        policy.setAutoRollbackOnFailure(req.autoRollbackOnFailure());
        policy.setRequireApproval(req.requireApproval());
        policy.setRequireJitApproval(req.requireJitApproval());
        policy.setSecretGeneratorConfig(req.secretGeneratorConfig());

        if (policy.isEnabled()) {
            policy.setNextRotationDueAt(Instant.now().plus(Duration.ofSeconds(policy.getIntervalSeconds())));
        }

        policy = policyRepository.save(policy);

        auditService.recordSecretAudit(null, workspaceId, actorId, AuditAction.ROTATION_POLICY_CREATED, secretId, null, null, "SUCCESS");
        return RotationPolicyResponse.fromEntity(policy);
    }

    @Transactional(readOnly = true)
    public RotationPolicyResponse getPolicy(UUID workspaceId, UUID secretId, UUID actorId) {
        RotationPolicy policy = policyRepository.findBySecretId(secretId)
                .orElseThrow(() -> ApiException.notFound("Rotation policy not found for this secret"));

        Secret secret = secretRepository.findById(secretId)
                .orElseThrow(() -> ApiException.notFound("Secret not found"));
        Environment env = environmentRepository.findById(secret.getEnvironmentId())
                .orElseThrow(() -> ApiException.notFound("Environment not found"));

        verifyAccess(workspaceId, env.getProjectId(), secret.getEnvironmentId(), secretId, actorId, AccessPermission.SECRET_ROTATION_READ);
        return RotationPolicyResponse.fromEntity(policy);
    }

    @Transactional
    public RotationPolicyResponse updatePolicy(UUID workspaceId, UUID secretId, UpdateRotationPolicyRequest req, UUID actorId) {
        RotationPolicy policy = policyRepository.findBySecretId(secretId)
                .orElseThrow(() -> ApiException.notFound("Rotation policy not found"));

        Secret secret = secretRepository.findById(secretId)
                .orElseThrow(() -> ApiException.notFound("Secret not found"));
        Environment env = environmentRepository.findById(secret.getEnvironmentId())
                .orElseThrow(() -> ApiException.notFound("Environment not found"));

        verifyAccess(workspaceId, env.getProjectId(), secret.getEnvironmentId(), secretId, actorId, AccessPermission.SECRET_ROTATION_MANAGE);

        policy.setEnabled(req.enabled());
        if (req.strategy() != null) policy.setStrategy(req.strategy());
        if (req.secretType() != null) policy.setSecretType(req.secretType());
        if (req.intervalSeconds() > 0) policy.setIntervalSeconds(req.intervalSeconds());
        if (req.minIntervalSeconds() > 0) policy.setMinIntervalSeconds(req.minIntervalSeconds());
        if (req.maxSecretAgeSeconds() != null) policy.setMaxSecretAgeSeconds(req.maxSecretAgeSeconds());
        if (req.rotationWindowSeconds() > 0) policy.setRotationWindowSeconds(req.rotationWindowSeconds());
        if (req.cronExpression() != null) policy.setCronExpression(req.cronExpression());
        if (req.timezone() != null) policy.setTimezone(req.timezone());
        if (req.maxRetries() > 0) policy.setMaxRetries(req.maxRetries());
        if (req.retryBackoffSeconds() > 0) policy.setRetryBackoffSeconds(req.retryBackoffSeconds());
        if (req.validationType() != null) policy.setValidationType(req.validationType());
        if (req.rolloutStrategy() != null) policy.setRolloutStrategy(req.rolloutStrategy());
        if (req.gracePeriodSeconds() >= 0) policy.setGracePeriodSeconds(req.gracePeriodSeconds());
        policy.setAutoRevokePrevious(req.autoRevokePrevious());
        policy.setAutoRollbackOnFailure(req.autoRollbackOnFailure());
        policy.setRequireApproval(req.requireApproval());
        policy.setRequireJitApproval(req.requireJitApproval());
        if (req.secretGeneratorConfig() != null) policy.setSecretGeneratorConfig(req.secretGeneratorConfig());

        if (policy.isEnabled() && policy.getNextRotationDueAt() == null) {
            policy.setNextRotationDueAt(Instant.now().plus(Duration.ofSeconds(policy.getIntervalSeconds())));
        }

        policy = policyRepository.save(policy);
        auditService.recordSecretAudit(null, workspaceId, actorId, AuditAction.ROTATION_POLICY_UPDATED, secretId, null, null, "SUCCESS");
        return RotationPolicyResponse.fromEntity(policy);
    }

    @Transactional
    public void disablePolicy(UUID workspaceId, UUID secretId, UUID actorId) {
        RotationPolicy policy = policyRepository.findBySecretId(secretId)
                .orElseThrow(() -> ApiException.notFound("Rotation policy not found"));

        Secret secret = secretRepository.findById(secretId)
                .orElseThrow(() -> ApiException.notFound("Secret not found"));
        Environment env = environmentRepository.findById(secret.getEnvironmentId())
                .orElseThrow(() -> ApiException.notFound("Environment not found"));

        verifyAccess(workspaceId, env.getProjectId(), secret.getEnvironmentId(), secretId, actorId, AccessPermission.SECRET_ROTATION_MANAGE);
        policy.setEnabled(false);
        policy.setNextRotationDueAt(null);
        policyRepository.save(policy);
        auditService.recordSecretAudit(null, workspaceId, actorId, AuditAction.ROTATION_POLICY_DISABLED, secretId, null, null, "SUCCESS");
    }

    // ==========================================
    // ROTATION EXECUTION & STATE MACHINE
    // ==========================================

    @Transactional
    public RotationJobResponse triggerRotation(
            UUID workspaceId, UUID secretId, TriggerRotationRequest req, UUID actorId, String idempotencyKey) {

        Secret secret = secretRepository.findById(secretId)
                .orElseThrow(() -> ApiException.notFound("Secret not found"));

        Environment env = environmentRepository.findById(secret.getEnvironmentId())
                .orElseThrow(() -> ApiException.notFound("Environment not found"));

        AccessPermission requiredPermission = (req != null && req.emergency())
                ? AccessPermission.SECRET_ROTATION_EMERGENCY
                : AccessPermission.SECRET_ROTATION_CREATE;

        verifyAccess(workspaceId, env.getProjectId(), secret.getEnvironmentId(), secretId, actorId, requiredPermission);

        // Verify idempotency key if supplied
        if (StringUtils.hasText(idempotencyKey)) {
            Optional<RotationJob> existingKeyJob = jobRepository.findByWorkspaceIdAndIdempotencyKey(workspaceId, idempotencyKey);
            if (existingKeyJob.isPresent()) {
                RotationJob existing = existingKeyJob.get();
                if (!existing.getSecretId().equals(secretId)) {
                    throw ApiException.conflict("Idempotency key '" + idempotencyKey + "' was previously used for a different secret rotation");
                }
                log.info("Idempotent rotation request matched existing job {} for secret {}", existing.getId(), secretId);
                return RotationJobResponse.fromEntity(existing);
            }
        }

        // Check if an active rotation job is already executing for this secret
        Optional<RotationJob> activeJob = jobRepository.findTopBySecretIdOrderByCreatedAtDesc(secretId);
        if (activeJob.isPresent() && isActiveState(activeJob.get().getStatus())) {
            log.warn("Rotation already in progress for secret {} (job ID: {})", secretId, activeJob.get().getId());
            return RotationJobResponse.fromEntity(activeJob.get());
        }

        RotationPolicy policy = policyRepository.findBySecretId(secretId).orElse(null);

        RotationJob job = new RotationJob();
        job.setId(UUID.randomUUID());
        job.setWorkspaceId(workspaceId);
        job.setSecretId(secretId);
        job.setPolicyId(policy != null ? policy.getId() : null);
        job.setStatus(RotationStatus.QUEUED);
        job.setTriggerType(req != null && req.strategy() != null ? req.strategy() : (req != null && req.emergency() ? RotationStrategy.EMERGENCY : RotationStrategy.MANUAL));
        job.setPreviousVersionNumber(secret.getCurrentVersionNumber());
        job.setTargetVersionNumber(secret.getCurrentVersionNumber() + 1);
        job.setMaxRetries(policy != null ? policy.getMaxRetries() : 3);
        job.setInitiatedBy(actorId);
        job.setEmergencyReason(req != null ? req.reason() : "Manual rotation triggered");
        job.setIdempotencyKey(idempotencyKey);
        job.setStartedAt(Instant.now());

        job = jobRepository.save(job);
        auditService.recordSecretAudit(null, workspaceId, actorId, req != null && req.emergency() ? AuditAction.ROTATION_EMERGENCY : AuditAction.ROTATION_STARTED, secretId, null, null, "SUCCESS");

        // Execute rotation lifecycle
        return executeRotation(job, policy, secret);
    }

    /**
     * Executes the full rotation state machine synchronously or worker-coordinated:
     * QUEUED -> STARTED -> GENERATING -> GENERATED -> VALIDATING -> VALIDATED -> STAGING -> STAGED -> ACTIVATING -> ACTIVE -> GRACE_PERIOD -> COMPLETED
     */
    @Transactional
    public RotationJobResponse executeRotation(RotationJob job, RotationPolicy policy, Secret secret) {
        String lockToken = UUID.randomUUID().toString();
        if (!distributedLock.acquireLock(secret.getId(), lockToken, Duration.ofMinutes(5))) {
            job.setStatus(RotationStatus.FAILED);
            job.setErrorMessage("Concurrent rotation lock already held");
            job = jobRepository.save(job);
            return RotationJobResponse.fromEntity(job);
        }

        long startTime = System.currentTimeMillis();
        String generatedPlaintext = null;
        try {
            // 1. STARTED
            transitionState(job, RotationStatus.STARTED);
            job.setStartedAt(Instant.now());

            // 2. GENERATING
            transitionState(job, RotationStatus.GENERATING);
            SecretRotator rotator = rotatorRegistry.getRotator(
                    policy != null ? policy.getSecretType() : SecretType.PASSWORD,
                    policy
            );

            generatedPlaintext = rotator.generate(policy, job);
            transitionState(job, RotationStatus.GENERATED);

            // Record attempt
            RotationAttempt attempt = new RotationAttempt();
            attempt.setJobId(job.getId());
            attempt.setAttemptNumber(job.getRetryCount() + 1);
            attempt.setStatus("GENERATED");
            attempt.setStage("GENERATION");
            attemptRepository.save(attempt);
            job.setRetryCount(job.getRetryCount() + 1);

            // 3. VALIDATING
            transitionState(job, RotationStatus.VALIDATING);
            boolean isValid = validationEngine.validateSecret(generatedPlaintext, policy, job)
                    && rotator.validate(generatedPlaintext, policy, job);

            if (!isValid) {
                transitionState(job, RotationStatus.VALIDATION_FAILED);
                job.setErrorMessage("Validation failed against target system");
                auditService.recordSecretAudit(null, job.getWorkspaceId(), null, AuditAction.ROTATION_FAILED, job.getSecretId(), null, null, "VALIDATION_FAILED");
                return RotationJobResponse.fromEntity(jobRepository.save(job));
            }
            transitionState(job, RotationStatus.VALIDATED);

            // 4. STAGING
            transitionState(job, RotationStatus.STAGING);
            rotator.stage(generatedPlaintext, policy, job);
            job.setStagedAt(Instant.now());
            transitionState(job, RotationStatus.STAGED);

            // 5. ENCRYPT & PERSIST NEW SECRET VERSION
            int newVersionNumber = secret.getCurrentVersionNumber() + 1;
            String aad = SecretAuthorizationHelper.buildAad(secret.getId(), secret.getEnvironmentId(), newVersionNumber);
            byte[] plaintextBytes = generatedPlaintext.getBytes(StandardCharsets.UTF_8);
            EncryptedPayload encrypted = encryptionService.encrypt(plaintextBytes, aad);
            Arrays.fill(plaintextBytes, (byte) 0);

            SecretVersion newVersion = new SecretVersion(
                    secret.getId(),
                    newVersionNumber,
                    VersionType.ROTATION,
                    encrypted.ciphertext(),
                    encrypted.encryptedDek(),
                    encrypted.iv(),
                    encrypted.authTag(),
                    encrypted.keyReference(),
                    job.getInitiatedBy(),
                    "Automated rotation via job " + job.getId(),
                    null,
                    null,
                    null,
                    null
            );
            newVersion = versionRepository.save(newVersion);
            job.setGeneratedVersionId(newVersion.getId());

            // 6. ACTIVATING
            transitionState(job, RotationStatus.ACTIVATING);
            rotator.activate(generatedPlaintext, policy, job);
            secret.setCurrentVersionNumber(newVersionNumber);
            secret.setUpdatedAt(Instant.now());
            secretRepository.save(secret);

            job.setActivatedAt(Instant.now());
            transitionState(job, RotationStatus.ACTIVE);
            auditService.recordSecretAudit(null, job.getWorkspaceId(), null, AuditAction.ROTATION_ACTIVATED, job.getSecretId(), null, null, "SUCCESS");

            // 7. GRACE PERIOD / ROLLOUT
            long graceSeconds = policy != null ? policy.getGracePeriodSeconds() : 1800L;
            if (graceSeconds > 0) {
                job.setGracePeriodEndsAt(Instant.now().plus(Duration.ofSeconds(graceSeconds)));
                transitionState(job, RotationStatus.GRACE_PERIOD);
            } else {
                // Immediate revocation of old version if no grace period
                if (policy != null && policy.isAutoRevokePrevious()) {
                    transitionState(job, RotationStatus.REVOKING);
                    rotator.revokePrevious(null, policy, job);
                    auditService.recordSecretAudit(null, job.getWorkspaceId(), null, AuditAction.OLD_VERSION_REVOKED, job.getSecretId(), null, null, "SUCCESS");
                }
                transitionState(job, RotationStatus.COMPLETED);
                job.setCompletedAt(Instant.now());
            }

            // Update policy last rotated timestamp
            if (policy != null) {
                policy.setLastRotatedAt(Instant.now());
                policy.setNextRotationDueAt(Instant.now().plus(Duration.ofSeconds(policy.getIntervalSeconds())));
                policyRepository.save(policy);
            }

            job = jobRepository.save(job);
            auditService.recordSecretAudit(null, job.getWorkspaceId(), null, AuditAction.ROTATION_COMPLETED, job.getSecretId(), null, null, "SUCCESS");

        } catch (Exception e) {
            log.error("Rotation execution failed for job {}: {}", job.getId(), e.getMessage(), e);
            job.setStatus(RotationStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            jobRepository.save(job);
            auditService.recordSecretAudit(null, job.getWorkspaceId(), null, AuditAction.ROTATION_FAILED, job.getSecretId(), null, null, "ERROR: " + e.getMessage());
        } finally {
            if (generatedPlaintext != null) {
                generatedPlaintext = null;
            }
            distributedLock.releaseLock(secret.getId(), lockToken);
        }

        return RotationJobResponse.fromEntity(job);
    }

    @Transactional
    public RotationJobResponse rollbackRotation(UUID workspaceId, UUID secretId, RotationRollbackRequest req, UUID actorId) {
        Secret secret = secretRepository.findById(secretId)
                .orElseThrow(() -> ApiException.notFound("Secret not found"));

        Environment env = environmentRepository.findById(secret.getEnvironmentId())
                .orElseThrow(() -> ApiException.notFound("Environment not found"));

        verifyAccess(workspaceId, env.getProjectId(), secret.getEnvironmentId(), secretId, actorId, AccessPermission.SECRET_ROTATION_ROLLBACK);

        int targetVerNum = (req != null && req.targetVersionNumber() != null)
                ? req.targetVersionNumber()
                : Math.max(1, secret.getCurrentVersionNumber() - 1);

        SecretVersion targetVersion = versionRepository.findBySecretIdAndVersionNumber(secretId, targetVerNum)
                .orElseThrow(() -> ApiException.notFound("Target secret version " + targetVerNum + " not found"));

        // Decrypt target historical version
        EncryptedPayload targetPayload = new EncryptedPayload(
                targetVersion.getCiphertext(), targetVersion.getEncryptedDek(),
                targetVersion.getIv(), targetVersion.getAuthTag(), targetVersion.getKeyReference()
        );
        String targetAad = SecretAuthorizationHelper.buildAad(secret.getId(), secret.getEnvironmentId(), targetVersion.getVersionNumber());
        byte[] targetPlaintextBytes = encryptionService.decrypt(targetPayload, targetAad);

        // Encrypt into a brand new version N+1
        int nextVersionNumber = secret.getCurrentVersionNumber() + 1;
        String nextAad = SecretAuthorizationHelper.buildAad(secret.getId(), secret.getEnvironmentId(), nextVersionNumber);
        EncryptedPayload newPayload = encryptionService.encrypt(targetPlaintextBytes, nextAad);
        Arrays.fill(targetPlaintextBytes, (byte) 0);

        SecretVersion rollbackVersion = new SecretVersion(
                secret.getId(),
                nextVersionNumber,
                VersionType.ROLLBACK,
                newPayload.ciphertext(),
                newPayload.encryptedDek(),
                newPayload.iv(),
                newPayload.authTag(),
                newPayload.keyReference(),
                actorId,
                req != null && StringUtils.hasText(req.reason()) ? req.reason() : "Rotation rollback to version " + targetVerNum,
                targetVersion.getId(),
                null,
                null,
                null
        );
        versionRepository.save(rollbackVersion);

        secret.setCurrentVersionNumber(nextVersionNumber);
        secretRepository.save(secret);

        // Create rollback job record for audit & observability
        RotationJob rollbackJob = new RotationJob();
        rollbackJob.setId(UUID.randomUUID());
        rollbackJob.setWorkspaceId(workspaceId);
        rollbackJob.setSecretId(secretId);
        rollbackJob.setStatus(RotationStatus.ROLLED_BACK);
        rollbackJob.setTriggerType(RotationStrategy.EMERGENCY);
        rollbackJob.setPreviousVersionNumber(targetVerNum);
        rollbackJob.setTargetVersionNumber(nextVersionNumber);
        rollbackJob.setInitiatedBy(actorId);
        rollbackJob.setEmergencyReason("Rolled back to version " + targetVerNum);
        rollbackJob.setRolledBackAt(Instant.now());
        rollbackJob.setCompletedAt(Instant.now());
        rollbackJob = jobRepository.save(rollbackJob);

        auditService.recordSecretAudit(null, workspaceId, actorId, AuditAction.ROTATION_ROLLED_BACK, secretId, null, null, "SUCCESS");
        return RotationJobResponse.fromEntity(rollbackJob);
    }

    @Transactional
    public void markCompromised(UUID workspaceId, UUID secretId, MarkCompromisedRequest req, UUID actorId) {
        Secret secret = secretRepository.findById(secretId)
                .orElseThrow(() -> ApiException.notFound("Secret not found"));

        Environment env = environmentRepository.findById(secret.getEnvironmentId())
                .orElseThrow(() -> ApiException.notFound("Environment not found"));

        verifyAccess(workspaceId, env.getProjectId(), secret.getEnvironmentId(), secretId, actorId, AccessPermission.SECRET_ROTATION_EMERGENCY);

        log.warn("SECURITY ALERT: Secret {} marked COMPROMISED by actor {}. Details: {}", secretId, actorId, req != null ? req.incidentDetails() : "N/A");

        // 1. Audit compromise event
        auditService.recordSecretAudit(null, workspaceId, actorId, AuditAction.SECRET_MARKED_COMPROMISED, secretId, null, null, "INCIDENT");

        // 2. Invalidate all active leases for this secret immediately
        if (req == null || req.revokeLeasesImmediately()) {
            leaseRepository.findBySecretIdAndStatus(secretId, com.secretvault.rotation.model.LeaseStatus.ACTIVE)
                    .forEach(lease -> {
                        lease.setStatus(com.secretvault.rotation.model.LeaseStatus.REVOKED);
                        lease.setRevokedAt(Instant.now());
                        leaseRepository.save(lease);
                    });
            auditService.recordSecretAudit(null, workspaceId, actorId, AuditAction.LEASE_REVOKED, secretId, null, null, "COMPROMISED_EMERGENCY");
        }

        // 3. Trigger immediate emergency rotation
        if (req == null || req.rotateImmediately()) {
            triggerRotation(workspaceId, secretId, new TriggerRotationRequest(RotationStrategy.EMERGENCY, "Automated emergency rotation following compromise detection", true, true), actorId, null);
        }
    }

    @Transactional(readOnly = true)
    public RotationJobResponse getJob(UUID workspaceId, UUID jobId, UUID actorId) {
        RotationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> ApiException.notFound("Rotation job not found"));

        Secret secret = secretRepository.findById(job.getSecretId())
                .orElseThrow(() -> ApiException.notFound("Secret not found"));
        Environment env = environmentRepository.findById(secret.getEnvironmentId())
                .orElseThrow(() -> ApiException.notFound("Environment not found"));

        verifyAccess(workspaceId, env.getProjectId(), secret.getEnvironmentId(), secret.getId(), actorId, AccessPermission.SECRET_ROTATION_READ);
        return RotationJobResponse.fromEntity(job);
    }

    @Transactional(readOnly = true)
    public Page<RotationJobResponse> listJobs(UUID workspaceId, UUID secretId, UUID actorId, Pageable pageable) {
        if (secretId != null) {
            Secret secret = secretRepository.findById(secretId)
                    .orElseThrow(() -> ApiException.notFound("Secret not found"));

            Environment env = environmentRepository.findById(secret.getEnvironmentId())
                    .orElseThrow(() -> ApiException.notFound("Environment not found"));

            verifyAccess(workspaceId, env.getProjectId(), secret.getEnvironmentId(), secretId, actorId, AccessPermission.SECRET_ROTATION_READ);
            return jobRepository.findBySecretIdOrderByCreatedAtDesc(secretId, pageable)
                    .map(RotationJobResponse::fromEntity);
        }

        verifyAccess(workspaceId, null, null, null, actorId, AccessPermission.SECRET_ROTATION_READ);
        return jobRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, pageable)
                .map(RotationJobResponse::fromEntity);
    }

    @Transactional
    public RotationJobResponse retryRotation(UUID workspaceId, UUID jobId, UUID actorId) {
        RotationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> ApiException.notFound("Rotation job not found"));

        Secret secret = secretRepository.findById(job.getSecretId())
                .orElseThrow(() -> ApiException.notFound("Secret not found"));
        Environment env = environmentRepository.findById(secret.getEnvironmentId())
                .orElseThrow(() -> ApiException.notFound("Environment not found"));

        verifyAccess(workspaceId, env.getProjectId(), secret.getEnvironmentId(), secret.getId(), actorId, AccessPermission.SECRET_ROTATION_MANAGE);

        if (job.getStatus() != RotationStatus.FAILED
                && job.getStatus() != RotationStatus.VALIDATION_FAILED
                && job.getStatus() != RotationStatus.ACTIVATION_FAILED) {
            throw ApiException.badRequest("Cannot retry rotation job in status: " + job.getStatus());
        }

        job.setStatus(RotationStatus.QUEUED);
        job.setNextRetryAt(Instant.now());
        job.setErrorMessage(null);
        job = jobRepository.save(job);

        RotationPolicy policy = policyRepository.findBySecretId(secret.getId()).orElse(null);
        return executeRotation(job, policy, secret);
    }

    @Transactional
    public void cancelRotation(UUID workspaceId, UUID jobId, UUID actorId) {
        RotationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> ApiException.notFound("Rotation job not found"));

        Secret secret = secretRepository.findById(job.getSecretId())
                .orElseThrow(() -> ApiException.notFound("Secret not found"));
        Environment env = environmentRepository.findById(secret.getEnvironmentId())
                .orElseThrow(() -> ApiException.notFound("Environment not found"));

        verifyAccess(workspaceId, env.getProjectId(), secret.getEnvironmentId(), secret.getId(), actorId, AccessPermission.SECRET_ROTATION_CANCEL);

        if (!isActiveState(job.getStatus())) {
            throw ApiException.badRequest("Cannot cancel rotation job in state " + job.getStatus());
        }

        job.setStatus(RotationStatus.CANCELLED);
        job.setCancelledAt(Instant.now());
        jobRepository.save(job);
        auditService.recordSecretAudit(null, workspaceId, actorId, AuditAction.ROTATION_CANCELLED, job.getSecretId(), null, null, "SUCCESS");
    }

    // ==========================================
    // HELPERS & AUTHORIZATION
    // ==========================================

    private void transitionState(RotationJob job, RotationStatus newStatus) {
        log.info("Transitioning rotation job {} from {} to {}", job.getId(), job.getStatus(), newStatus);
        job.setStatus(newStatus);
        jobRepository.save(job);
    }

    private boolean isActiveState(RotationStatus status) {
        return status == RotationStatus.QUEUED
                || status == RotationStatus.STARTED
                || status == RotationStatus.GENERATING
                || status == RotationStatus.VALIDATING
                || status == RotationStatus.STAGING
                || status == RotationStatus.ACTIVATING;
    }

    private void verifyAccess(UUID workspaceId, UUID projectId, UUID environmentId, UUID secretId, UUID actorId, AccessPermission permission) {
        if (actorId == null) {
            return; // Internal system background workers
        }
        AccessDecision decision = effectiveAccessService.evaluateAccess(workspaceId, projectId, environmentId, secretId, permission, actorId);
        if (!decision.allowed()) {
            throw ApiException.forbidden("Access denied: missing permission " + permission.getCode() + " in this scope");
        }
    }
}
