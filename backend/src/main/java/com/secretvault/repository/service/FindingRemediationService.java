package com.secretvault.repository.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.repository.entity.FindingRemediationJob;
import com.secretvault.repository.entity.SecretFinding;
import com.secretvault.repository.model.RemediationAction;
import com.secretvault.repository.model.RemediationStatus;
import com.secretvault.repository.model.RepoFindingStatus;
import com.secretvault.repository.repository.FindingRemediationJobRepository;
import com.secretvault.repository.repository.SecretFindingRepository;
import com.secretvault.rotation.dto.RotationDtos.MarkCompromisedRequest;
import com.secretvault.rotation.dto.RotationDtos.TriggerRotationRequest;
import com.secretvault.rotation.model.RotationStrategy;
import com.secretvault.rotation.service.RotationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class FindingRemediationService {

    private static final Logger log = LoggerFactory.getLogger(FindingRemediationService.class);

    private final SecretFindingRepository findingRepository;
    private final FindingRemediationJobRepository remediationJobRepository;
    private final RotationService rotationService;
    private final SecretFindingService secretFindingService;
    private final EffectiveAccessService accessService;
    private final AuditService auditService;

    public FindingRemediationService(
            SecretFindingRepository findingRepository,
            FindingRemediationJobRepository remediationJobRepository,
            RotationService rotationService,
            SecretFindingService secretFindingService,
            EffectiveAccessService accessService,
            AuditService auditService) {
        this.findingRepository = findingRepository;
        this.remediationJobRepository = remediationJobRepository;
        this.rotationService = rotationService;
        this.secretFindingService = secretFindingService;
        this.accessService = accessService;
        this.auditService = auditService;
    }

    @Transactional
    public FindingRemediationJob remediateFinding(
            UUID workspaceId,
            UUID findingId,
            RemediationAction action,
            String notes,
            UUID actorId) {

        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_REMEDIATE);

        SecretFinding finding = findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Secret finding not found: " + findingId));

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
                case MARK_FALSE_POSITIVE -> {
                    finding.setStatus(RepoFindingStatus.FALSE_POSITIVE);
                    finding.setRemediationStatus(RemediationStatus.RESOLVED);
                    secretFindingService.allowlistFinding(
                            workspaceId, finding.getRepositoryId(), finding.getFingerprint(),
                            finding.getDetectorType(), finding.getFilePath(),
                            "False positive marked by user: " + (notes != null ? notes : ""),
                            null, actorId);
                }
                case IGNORE -> {
                    finding.setStatus(RepoFindingStatus.IGNORED);
                    finding.setRemediationStatus(RemediationStatus.RESOLVED);
                }
                case ROTATE_SECRET -> {
                    if (finding.getMatchedSecretId() != null) {
                        rotationService.triggerRotation(
                                workspaceId,
                                finding.getMatchedSecretId(),
                                new TriggerRotationRequest(
                                        RotationStrategy.EMERGENCY,
                                        "Automated emergency rotation triggered for leaked secret in finding " + findingId,
                                        true,
                                        true
                                ),
                                actorId,
                                null
                        );
                        finding.setRemediationStatus(RemediationStatus.ROTATED);
                        finding.setStatus(RepoFindingStatus.RESOLVED);

                        auditService.record(
                                workspaceId,
                                actorId,
                                AuditAction.SECRET_ROTATION_TRIGGERED,
                                "SECRET",
                                finding.getMatchedSecretId().toString(),
                                Map.of("findingId", findingId.toString())
                        );
                    } else {
                        finding.setRemediationStatus(RemediationStatus.PENDING);
                    }
                }
                case REVOKE_SECRET -> {
                    if (finding.getMatchedSecretId() != null) {
                        rotationService.markCompromised(
                                workspaceId,
                                finding.getMatchedSecretId(),
                                new MarkCompromisedRequest(
                                        "Credential confirmed exposed in code repository: " + finding.getFilePath() + " (Finding " + findingId + ")",
                                        true,
                                        true
                                ),
                                actorId
                        );
                        finding.setRemediationStatus(RemediationStatus.REVOKED);
                        finding.setStatus(RepoFindingStatus.RESOLVED);

                        auditService.record(
                                workspaceId,
                                actorId,
                                AuditAction.SECRET_REVOKED,
                                "SECRET",
                                finding.getMatchedSecretId().toString(),
                                Map.of("findingId", findingId.toString())
                        );
                    } else {
                        finding.setRemediationStatus(RemediationStatus.REVOKED);
                        finding.setStatus(RepoFindingStatus.RESOLVED);
                    }
                }
                default -> {
                    finding.setRemediationStatus(RemediationStatus.RESOLVED);
                    finding.setStatus(RepoFindingStatus.RESOLVED);
                }
            }

            finding.setUpdatedAt(Instant.now());
            findingRepository.save(finding);

            savedJob.setStatus(RemediationStatus.COMPLETED);
            savedJob.setCompletedAt(Instant.now());
            remediationJobRepository.save(savedJob);

            auditService.record(
                    workspaceId,
                    actorId,
                    AuditAction.REMEDIATION_COMPLETED,
                    "FINDING_REMEDIATION",
                    savedJob.getId().toString(),
                    Map.of("status", "SUCCESS")
            );

        } catch (Exception e) {
            log.error("Remediation failed for job {}", savedJob.getId(), e);
            savedJob.setStatus(RemediationStatus.FAILED);
            savedJob.setErrorMessage("Remediation execution error: " + e.getMessage());
            savedJob.setCompletedAt(Instant.now());
            remediationJobRepository.save(savedJob);
        }

        return savedJob;
    }
}
