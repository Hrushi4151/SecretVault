package com.secretvault.repository.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.repository.dto.RepositoryDtos.RemediateFindingRequest;
import com.secretvault.repository.dto.RepositoryDtos.RemediationTargetInfo;
import com.secretvault.repository.entity.FindingRemediationJob;
import com.secretvault.repository.entity.SecretFinding;
import com.secretvault.repository.model.*;
import com.secretvault.repository.repository.FindingRemediationJobRepository;
import com.secretvault.repository.repository.SecretFindingRepository;
import com.secretvault.repository.validator.LiveCredentialValidator;
import com.secretvault.rotation.dto.RotationDtos.MarkCompromisedRequest;
import com.secretvault.rotation.dto.RotationDtos.RotationJobResponse;
import com.secretvault.rotation.dto.RotationDtos.TriggerRotationRequest;
import com.secretvault.rotation.model.RotationStatus;
import com.secretvault.rotation.model.RotationStrategy;
import com.secretvault.rotation.service.RotationService;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.repository.SecretRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class FindingRemediationService {

    private static final Logger log = LoggerFactory.getLogger(FindingRemediationService.class);

    private final SecretFindingRepository findingRepository;
    private final FindingRemediationJobRepository remediationJobRepository;
    private final RotationService rotationService;
    private final SecretFindingService secretFindingService;
    private final EffectiveAccessService accessService;
    private final AuditService auditService;
    private final SecretRepository secretRepository;
    private final EnvironmentRepository environmentRepository;
    private final ProjectRepository projectRepository;
    private final SecretInventoryCorrelator correlator;
    private final LiveCredentialValidator liveValidator;

    public FindingRemediationService(
            SecretFindingRepository findingRepository,
            FindingRemediationJobRepository remediationJobRepository,
            RotationService rotationService,
            SecretFindingService secretFindingService,
            EffectiveAccessService accessService,
            AuditService auditService
    ) {
        this(
                findingRepository,
                remediationJobRepository,
                rotationService,
                secretFindingService,
                accessService,
                auditService,
                null,
                null,
                null,
                null,
                null
        );
    }

    @Autowired
    public FindingRemediationService(
            SecretFindingRepository findingRepository,
            FindingRemediationJobRepository remediationJobRepository,
            RotationService rotationService,
            SecretFindingService secretFindingService,
            EffectiveAccessService accessService,
            AuditService auditService,
            @Autowired(required = false) SecretRepository secretRepository,
            @Autowired(required = false) EnvironmentRepository environmentRepository,
            @Autowired(required = false) ProjectRepository projectRepository,
            @Autowired(required = false) SecretInventoryCorrelator correlator,
            @Autowired(required = false) LiveCredentialValidator liveValidator
    ) {
        this.findingRepository = findingRepository;
        this.remediationJobRepository = remediationJobRepository;
        this.rotationService = rotationService;
        this.secretFindingService = secretFindingService;
        this.accessService = accessService;
        this.auditService = auditService;
        this.secretRepository = secretRepository;
        this.environmentRepository = environmentRepository;
        this.projectRepository = projectRepository;
        this.correlator = correlator;
        this.liveValidator = liveValidator;
    }

    /**
     * Inspect remediation target, ownership, and manual playbook before executing.
     */
    @Transactional(readOnly = true)
    public RemediationTargetInfo getRemediationTargetInfo(UUID workspaceId, UUID findingId, UUID actorId) {
        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_VIEW);

        SecretFinding finding = findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Secret finding not found: " + findingId));

        UUID matchedSecretId = finding.getMatchedSecretId();
        if (matchedSecretId == null && correlator != null) {
            var correlation = correlator.correlateByFingerprint(finding.getFingerprint());
            if (correlation != null && correlation.isMatched()) {
                matchedSecretId = correlation.secretId();
            }
        }

        boolean isManaged = false;
        String matchedSecretName = null;
        String matchedEnvName = null;
        String matchedProjName = null;
        Integer currentVersionNumber = null;
        String rotationStrategy = null;
        String impactWarning = null;
        boolean rotationSupported = false;
        boolean revocationSupported = false;
        List<String> manualInstructions;

        if (matchedSecretId != null && secretRepository != null) {
            Optional<Secret> secretOpt = secretRepository.findById(matchedSecretId);
            if (secretOpt.isPresent()) {
                Secret secret = secretOpt.get();
                isManaged = true;
                matchedSecretName = secret.getName();
                currentVersionNumber = secret.getCurrentVersionNumber();
                rotationSupported = true;
                revocationSupported = true;

                if (secret.getEnvironmentId() != null && environmentRepository != null) {
                    Optional<Environment> envOpt = environmentRepository.findById(secret.getEnvironmentId());
                    if (envOpt.isPresent()) {
                        Environment env = envOpt.get();
                        matchedEnvName = env.getName();
                        if (env.getProjectId() != null && projectRepository != null) {
                            Optional<Project> projOpt = projectRepository.findById(env.getProjectId());
                            if (projOpt.isPresent()) {
                                matchedProjName = projOpt.get().getName();
                            }
                        }
                    }
                }

                rotationStrategy = "EMERGENCY";
                impactWarning = "Executing emergency rotation invalidates active consumer leases, generates Version "
                        + ((currentVersionNumber != null ? currentVersionNumber : 1) + 1)
                        + ", and rolls out the replacement credential.";
            }
        }

        String providerName = resolveProviderName(finding.getSecretType());

        if (!isManaged) {
            rotationSupported = false;
            revocationSupported = false;
            impactWarning = "This credential is not managed in SecretVault. Automatic zero-downtime rotation cannot be performed directly. Follow the manual remediation playbook below to generate a replacement and store it in SecretVault.";
            manualInstructions = buildManualInstructions(finding.getSecretType(), finding.getFilePath());
        } else {
            manualInstructions = List.of(
                    "1. Confirm the impact warning and verify dependent services.",
                    "2. Trigger Emergency Secret Rotation to roll over the credential.",
                    "3. Update any legacy code references in " + finding.getFilePath() + " to use SecretVault injection.",
                    "4. Purge exposed commit SHA (" + (finding.getCommitSha() != null ? finding.getCommitSha() : "HEAD") + ") from git history if repository is public."
            );
        }

        return new RemediationTargetInfo(
                finding.getId(),
                finding.getSecretType() != null ? finding.getSecretType().name() : "UNKNOWN",
                finding.getSeverity() != null ? finding.getSeverity().name() : "HIGH",
                finding.getMaskedValue(),
                finding.getFilePath(),
                finding.getLineNumber(),
                finding.getCommitSha(),
                finding.getBranch(),
                isManaged,
                matchedSecretId,
                matchedSecretName,
                matchedEnvName,
                matchedProjName,
                currentVersionNumber,
                providerName,
                rotationSupported,
                revocationSupported,
                rotationStrategy,
                impactWarning,
                manualInstructions
        );
    }

    @Transactional
    public FindingRemediationJob remediateFinding(
            UUID workspaceId,
            UUID findingId,
            RemediationAction action,
            String notes,
            UUID actorId
    ) {
        return remediateFinding(workspaceId, findingId, new RemediateFindingRequest(action, notes), actorId);
    }

    @Transactional
    public FindingRemediationJob remediateFinding(
            UUID workspaceId,
            UUID findingId,
            RemediateFindingRequest request,
            UUID actorId
    ) {
        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_REMEDIATE);

        SecretFinding finding = findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Secret finding not found: " + findingId));

        // Idempotency & duplicate submission guard: prevent concurrent execution on same finding
        boolean activeJobExists = remediationJobRepository.existsByFindingIdAndStatusIn(
                findingId, List.of(RemediationStatus.IN_PROGRESS, RemediationStatus.ROTATION_PENDING, RemediationStatus.REVOCATION_PENDING)
        );
        if (activeJobExists) {
            throw ApiException.conflict("A remediation operation is already in progress for this finding.");
        }

        RemediationAction action = request.action();
        String notes = request.notes();

        // Enforce meaningful reason for suppression actions
        if (action == RemediationAction.MARK_FALSE_POSITIVE || action == RemediationAction.IGNORE) {
            validateMeaningfulReason(notes);
        }

        FindingRemediationJob job = new FindingRemediationJob();
        job.setWorkspaceId(workspaceId);
        job.setFindingId(findingId);
        job.setAction(action);
        job.setStatus(RemediationStatus.IN_PROGRESS);
        job.setInitiatedBy(actorId);
        job.setStartedAt(Instant.now());
        job.setNotes(notes);

        FindingRemediationJob savedJob = remediationJobRepository.save(job);

        auditService.record(
                workspaceId,
                actorId,
                AuditAction.REMEDIATION_STARTED,
                "FINDING_REMEDIATION",
                savedJob.getId().toString(),
                Map.of("findingId", findingId.toString(), "action", action.name())
        );

        try {
            switch (action) {
                case ROTATE_SECRET -> {
                    UUID targetSecretId = finding.getMatchedSecretId() != null
                            ? finding.getMatchedSecretId()
                            : request.targetSecretId();

                    if (targetSecretId == null && correlator != null) {
                        var corr = correlator.correlateByFingerprint(finding.getFingerprint());
                        if (corr != null && corr.isMatched()) {
                            targetSecretId = corr.secretId();
                            finding.setMatchedSecretId(targetSecretId);
                        }
                    }

                    if (targetSecretId != null) {
                        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.SECRET_ROTATION_EMERGENCY);
                        finding.setStatus(RepoFindingStatus.ROTATION_PENDING);
                        savedJob.setStatus(RemediationStatus.ROTATION_PENDING);

                        RotationJobResponse rotResult = rotationService.triggerRotation(
                                workspaceId,
                                targetSecretId,
                                new TriggerRotationRequest(
                                        RotationStrategy.EMERGENCY,
                                        "Emergency rotation triggered for leaked secret in " + finding.getFilePath() + " (Finding " + findingId + "): " + (notes != null ? notes : ""),
                                        true,
                                        true
                                ),
                                actorId,
                                null
                        );

                        if (rotResult != null && (rotResult.status() == RotationStatus.COMPLETED || rotResult.status() == RotationStatus.ACTIVE || rotResult.status() == RotationStatus.GRACE_PERIOD)) {
                            finding.setRemediationStatus(RemediationStatus.ROTATED);
                            finding.setStatus(RepoFindingStatus.RESOLVED);
                            finding.setUpdatedAt(Instant.now());
                            findingRepository.save(finding);

                            savedJob.setStatus(RemediationStatus.ROTATION_SUCCEEDED);
                            savedJob.setDetailsJson("{\"rotationJobId\":\"" + rotResult.id() + "\",\"status\":\"SUCCESS\",\"targetVersion\":" + rotResult.targetVersionNumber() + "}");
                            savedJob.setCompletedAt(Instant.now());
                            remediationJobRepository.save(savedJob);

                            auditService.record(workspaceId, actorId, AuditAction.SECRET_ROTATION_TRIGGERED, "SECRET", targetSecretId.toString(), Map.of("findingId", findingId.toString(), "status", "ROTATION_SUCCEEDED"));
                            auditService.record(workspaceId, actorId, AuditAction.REMEDIATION_COMPLETED, "FINDING_REMEDIATION", savedJob.getId().toString(), Map.of("status", "SUCCESS", "action", "ROTATE_SECRET"));
                        } else {
                            String err = rotResult != null && rotResult.errorMessage() != null ? rotResult.errorMessage() : "Rotation failed during execution";
                            finding.setRemediationStatus(RemediationStatus.ROTATION_FAILED);
                            finding.setStatus(RepoFindingStatus.CONFIRMED);
                            finding.setUpdatedAt(Instant.now());
                            findingRepository.save(finding);

                            savedJob.setStatus(RemediationStatus.ROTATION_FAILED);
                            savedJob.setErrorMessage(err);
                            savedJob.setCompletedAt(Instant.now());
                            remediationJobRepository.save(savedJob);

                            auditService.record(workspaceId, actorId, AuditAction.ROTATION_FAILED, "SECRET", targetSecretId.toString(), Map.of("findingId", findingId.toString(), "error", err));
                        }
                    } else {
                        // Unmanaged credential: cannot claim rotation succeeded
                        String manualMsg = "Credential is not managed in SecretVault. Automatic rotation is unavailable. Follow manual playbook.";
                        finding.setRemediationStatus(RemediationStatus.MANUAL_ACTION_REQUIRED);
                        finding.setStatus(RepoFindingStatus.CONFIRMED);
                        finding.setUpdatedAt(Instant.now());
                        findingRepository.save(finding);

                        savedJob.setStatus(RemediationStatus.MANUAL_ACTION_REQUIRED);
                        savedJob.setErrorMessage(manualMsg);
                        savedJob.setDetailsJson(String.join("\n", buildManualInstructions(finding.getSecretType(), finding.getFilePath())));
                        savedJob.setCompletedAt(Instant.now());
                        remediationJobRepository.save(savedJob);
                    }
                }

                case REVOKE_SECRET -> {
                    // Mark compromised immediately
                    finding.setStatus(RepoFindingStatus.COMPROMISED);
                    finding.setUpdatedAt(Instant.now());
                    findingRepository.save(finding);
                    auditService.record(workspaceId, actorId, AuditAction.SECRET_MARKED_COMPROMISED, "SECRET_FINDING", findingId.toString(), Map.of("action", "REVOKE_SECRET", "reason", notes != null ? notes : ""));

                    UUID targetSecretId = finding.getMatchedSecretId() != null
                            ? finding.getMatchedSecretId()
                            : request.targetSecretId();

                    if (targetSecretId == null && correlator != null) {
                        var corr = correlator.correlateByFingerprint(finding.getFingerprint());
                        if (corr != null && corr.isMatched()) {
                            targetSecretId = corr.secretId();
                            finding.setMatchedSecretId(targetSecretId);
                        }
                    }

                    if (targetSecretId != null) {
                        rotationService.markCompromised(
                                workspaceId,
                                targetSecretId,
                                new MarkCompromisedRequest(
                                        "Credential compromised in repo finding " + findingId + ": " + (notes != null ? notes : ""),
                                        true,
                                        true
                                ),
                                actorId
                        );

                        finding.setRemediationStatus(RemediationStatus.REVOCATION_SUCCEEDED);
                        finding.setStatus(RepoFindingStatus.RESOLVED);
                        finding.setUpdatedAt(Instant.now());
                        findingRepository.save(finding);

                        savedJob.setStatus(RemediationStatus.REVOCATION_SUCCEEDED);
                        savedJob.setDetailsJson("{\"secretId\":\"" + targetSecretId + "\",\"leasesRevoked\":true,\"status\":\"REVOCATION_SUCCEEDED\"}");
                        savedJob.setCompletedAt(Instant.now());
                        remediationJobRepository.save(savedJob);

                        auditService.record(workspaceId, actorId, AuditAction.SECRET_REVOKED, "SECRET", targetSecretId.toString(), Map.of("findingId", findingId.toString(), "status", "REVOCATION_SUCCEEDED"));
                        auditService.record(workspaceId, actorId, AuditAction.REMEDIATION_COMPLETED, "FINDING_REMEDIATION", savedJob.getId().toString(), Map.of("status", "SUCCESS", "action", "REVOKE_SECRET"));
                    } else {
                        // External provider unmanaged token: distinguish manual action required
                        String manualMsg = "External provider (" + finding.getSecretType() + ") does not support direct remote revocation. Follow the manual revocation playbook.";
                        finding.setRemediationStatus(RemediationStatus.MANUAL_ACTION_REQUIRED);
                        finding.setStatus(RepoFindingStatus.COMPROMISED);
                        finding.setUpdatedAt(Instant.now());
                        findingRepository.save(finding);

                        savedJob.setStatus(RemediationStatus.MANUAL_ACTION_REQUIRED);
                        savedJob.setErrorMessage(manualMsg);
                        savedJob.setDetailsJson(String.join("\n", buildManualInstructions(finding.getSecretType(), finding.getFilePath())));
                        savedJob.setCompletedAt(Instant.now());
                        remediationJobRepository.save(savedJob);
                    }
                }

                case MARK_FALSE_POSITIVE -> {
                    String scopePath = request.filePattern() != null && !request.filePattern().isBlank()
                            ? request.filePattern().trim()
                            : finding.getFilePath();

                    secretFindingService.allowlistFinding(
                            workspaceId,
                            finding.getRepositoryId(),
                            finding.getFingerprint(),
                            finding.getDetectorType(),
                            scopePath,
                            notes != null ? notes.trim() : "Approved false positive",
                            request.expiresAt(),
                            actorId
                    );

                    finding.setStatus(RepoFindingStatus.FALSE_POSITIVE);
                    finding.setRemediationStatus(RemediationStatus.RESOLVED);
                    finding.setUpdatedAt(Instant.now());
                    findingRepository.save(finding);

                    savedJob.setStatus(RemediationStatus.COMPLETED);
                    savedJob.setCompletedAt(Instant.now());
                    remediationJobRepository.save(savedJob);

                    auditService.record(workspaceId, actorId, AuditAction.REMEDIATION_COMPLETED, "FINDING_REMEDIATION", savedJob.getId().toString(), Map.of("status", "SUCCESS", "action", "MARK_FALSE_POSITIVE"));
                }

                case IGNORE -> {
                    finding.setStatus(RepoFindingStatus.IGNORED);
                    finding.setRemediationStatus(RemediationStatus.RESOLVED);
                    finding.setUpdatedAt(Instant.now());
                    findingRepository.save(finding);

                    savedJob.setStatus(RemediationStatus.COMPLETED);
                    savedJob.setCompletedAt(Instant.now());
                    remediationJobRepository.save(savedJob);

                    auditService.record(workspaceId, actorId, AuditAction.FINDING_IGNORED, "SECRET_FINDING", findingId.toString(), Map.of("reason", notes != null ? notes : ""));
                    auditService.record(workspaceId, actorId, AuditAction.REMEDIATION_COMPLETED, "FINDING_REMEDIATION", savedJob.getId().toString(), Map.of("status", "SUCCESS", "action", "IGNORE"));
                }

                default -> {
                    finding.setRemediationStatus(RemediationStatus.RESOLVED);
                    finding.setStatus(RepoFindingStatus.RESOLVED);
                    finding.setUpdatedAt(Instant.now());
                    findingRepository.save(finding);

                    savedJob.setStatus(RemediationStatus.COMPLETED);
                    savedJob.setCompletedAt(Instant.now());
                    remediationJobRepository.save(savedJob);
                }
            }
        } catch (ApiException ae) {
            log.error("Remediation business exception for job {}: {}", savedJob.getId(), ae.getMessage());
            savedJob.setStatus(RemediationStatus.FAILED);
            savedJob.setErrorMessage(ae.getMessage());
            savedJob.setCompletedAt(Instant.now());
            remediationJobRepository.save(savedJob);
            throw ae;
        } catch (Exception e) {
            log.error("Remediation failed for job {}", savedJob.getId(), e);
            savedJob.setStatus(RemediationStatus.FAILED);
            savedJob.setErrorMessage("Remediation execution error: " + e.getMessage());
            savedJob.setCompletedAt(Instant.now());
            remediationJobRepository.save(savedJob);
        }

        return savedJob;
    }

    private void validateMeaningfulReason(String reason) {
        if (reason == null || reason.trim().length() < 10) {
            throw ApiException.badRequest("A meaningful audit reason (at least 10 characters) is required to suppress or allowlist a finding.");
        }
        String normalized = reason.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("false positive") || normalized.equals("not a secret") || normalized.equals("placeholder value") || normalized.equals("test test test")) {
            throw ApiException.badRequest("Please provide a specific, contextual rationale explaining why this finding is safe.");
        }
    }

    private String resolveProviderName(SecretType type) {
        if (type == null) return "Generic";
        return switch (type) {
            case AWS_ACCESS_KEY, AWS_SECRET_KEY -> "Amazon Web Services (AWS)";
            case GITHUB_TOKEN -> "GitHub";
            case GITLAB_TOKEN -> "GitLab";
            case STRIPE_KEY -> "Stripe Payments";
            case SLACK_TOKEN -> "Slack";
            case TWILIO_KEY -> "Twilio";
            case SENDGRID_KEY -> "SendGrid";
            case DATABASE_PASSWORD, DATABASE_CONNECTION_STRING -> "Database Server";
            case PRIVATE_KEY, SSH_PRIVATE_KEY -> "PKI / TLS / SSH Key";
            case GOOGLE_API_KEY -> "Google Cloud Platform";
            case AZURE_CREDENTIAL -> "Microsoft Azure";
            default -> "External API Provider";
        };
    }

    private List<String> buildManualInstructions(SecretType type, String filePath) {
        List<String> steps = new ArrayList<>();
        if (type == null) type = SecretType.GENERIC_API_KEY;

        switch (type) {
            case AWS_ACCESS_KEY, AWS_SECRET_KEY -> {
                steps.add("1. Log in to the AWS Management Console or execute `aws iam create-access-key` to generate a new key pair.");
                steps.add("2. Update all deployment pipelines, ECS tasks, and Kubernetes secrets with the new key.");
                steps.add("3. Execute `aws iam update-access-key --status Inactive` and then `aws iam delete-access-key` on the exposed key.");
                steps.add("4. Import the new key into SecretVault for automated rotation.");
                steps.add("5. Remove the hardcoded secret from " + (filePath != null ? filePath : "source code") + " and purge Git history.");
            }
            case GITHUB_TOKEN, GITLAB_TOKEN -> {
                steps.add("1. Open provider Settings -> Developer settings -> Personal access tokens.");
                steps.add("2. Generate a replacement token with minimum required repository scopes.");
                steps.add("3. Update CI/CD workflows and repository secrets.");
                steps.add("4. Revoke the exposed token immediately in the provider UI.");
                steps.add("5. Store the new token in SecretVault.");
            }
            case STRIPE_KEY -> {
                steps.add("1. Open the Stripe Dashboard -> Developers -> API Keys.");
                steps.add("2. Click 'Roll key' or create a new Restricted API Key.");
                steps.add("3. Deploy the new key to backend billing services.");
                steps.add("4. Expire the old Stripe key.");
                steps.add("5. Save the new key in SecretVault.");
            }
            case SLACK_TOKEN -> {
                steps.add("1. Open Slack API dashboard (api.slack.com/apps) -> OAuth & Permissions.");
                steps.add("2. Reinstall the Slack App or regenerate the bot/user token.");
                steps.add("3. Update integration endpoints and revoke the exposed token.");
                steps.add("4. Store the token in SecretVault.");
            }
            case DATABASE_PASSWORD, DATABASE_CONNECTION_STRING -> {
                steps.add("1. Connect to the database server via administrative CLI.");
                steps.add("2. Execute `ALTER USER <username> WITH PASSWORD '<new-secure-password>';`");
                steps.add("3. Update application connection strings in SecretVault and trigger config reload.");
                steps.add("4. Verify database connection pool health and remove the plaintext credentials from " + (filePath != null ? filePath : "source code") + ".");
            }
            default -> {
                steps.add("1. Log in to the provider management portal and regenerate the secret.");
                steps.add("2. Update all consumer services with the newly generated credential.");
                steps.add("3. Revoke the exposed credential at the provider portal.");
                steps.add("4. Store the new credential securely in SecretVault.");
                steps.add("5. Remove the reference from " + (filePath != null ? filePath : "source code") + ".");
            }
        }
        return steps;
    }
}
