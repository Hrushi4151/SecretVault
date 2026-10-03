package com.secretvault.repository.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.repository.entity.FindingAllowlist;
import com.secretvault.repository.entity.RepositoryEntity;
import com.secretvault.repository.entity.SecretFinding;
import com.secretvault.repository.entity.SecretFindingOccurrence;
import com.secretvault.repository.model.*;
import com.secretvault.repository.repository.FindingAllowlistRepository;
import com.secretvault.repository.repository.RepositoryEntityRepository;
import com.secretvault.repository.repository.SecretFindingOccurrenceRepository;
import com.secretvault.repository.repository.SecretFindingRepository;
import com.secretvault.repository.validator.LiveCredentialValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class SecretFindingService {

    private final SecretFindingRepository findingRepository;
    private final SecretFindingOccurrenceRepository occurrenceRepository;
    private final RepositoryEntityRepository repoRepository;
    private final FindingAllowlistRepository allowlistRepository;
    private final LiveCredentialValidator liveValidator;
    private final EffectiveAccessService accessService;
    private final AuditService auditService;

    public SecretFindingService(
            SecretFindingRepository findingRepository,
            SecretFindingOccurrenceRepository occurrenceRepository,
            RepositoryEntityRepository repoRepository,
            FindingAllowlistRepository allowlistRepository,
            LiveCredentialValidator liveValidator,
            EffectiveAccessService accessService,
            AuditService auditService) {
        this.findingRepository = findingRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.repoRepository = repoRepository;
        this.allowlistRepository = allowlistRepository;
        this.liveValidator = liveValidator;
        this.accessService = accessService;
        this.auditService = auditService;
    }

    public record WhyExposedExplanation(
            UUID findingId,
            String secretType,
            String severity,
            String confidence,
            String maskedValue,
            String filePath,
            Integer lineNumber,
            String commitSha,
            String branch,
            String repositoryName,
            String repositoryVisibility,
            boolean isMatchedWithSecretVault,
            UUID matchedSecretId,
            String validationStatus,
            List<String> riskFactors,
            String recommendedRemediation
    ) {}

    public record FindingStatsDto(
            long totalFindings,
            long criticalFindings,
            long highFindings,
            long mediumFindings,
            long lowFindings,
            long activeFindings,
            long resolvedFindings,
            long falsePositiveFindings
    ) {}

    @Transactional(readOnly = true)
    public Page<SecretFinding> listFindings(
            UUID workspaceId,
            UUID repositoryId,
            RepoFindingSeverity severity,
            RepoFindingStatus status,
            String search,
            UUID actorId,
            Pageable pageable) {

        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_VIEW);

        Specification<SecretFinding> spec = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("workspaceId"), workspaceId));

            if (repositoryId != null) {
                predicates.add(cb.equal(root.get("repositoryId"), repositoryId));
            }
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (search != null && !search.isBlank()) {
                String searchPattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("filePath")), searchPattern),
                        cb.like(cb.lower(root.get("detectorType")), searchPattern),
                        cb.like(cb.lower(root.get("fingerprint")), searchPattern)
                ));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return findingRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public SecretFinding getFinding(UUID workspaceId, UUID findingId, UUID actorId) {
        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_VIEW);
        return findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Secret finding not found: " + findingId));
    }

    @Transactional(readOnly = true)
    public Page<SecretFindingOccurrence> getOccurrences(
            UUID workspaceId, UUID findingId, UUID actorId, Pageable pageable) {
        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_VIEW);
        // Verify finding belongs to workspace
        findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Secret finding not found: " + findingId));
        return occurrenceRepository.findByFindingId(findingId, pageable);
    }

    @Transactional
    public SecretFinding updateStatus(
            UUID workspaceId, UUID findingId, RepoFindingStatus newStatus, String reason, UUID actorId) {
        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_FINDING_MANAGE);

        SecretFinding finding = findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Secret finding not found: " + findingId));

        RepoFindingStatus previousStatus = finding.getStatus();
        finding.setStatus(newStatus);
        finding.setUpdatedAt(Instant.now());

        AuditAction auditAction = switch (newStatus) {
            case CONFIRMED -> AuditAction.FINDING_CONFIRMED;
            case FALSE_POSITIVE -> AuditAction.FINDING_FALSE_POSITIVE;
            case IGNORED -> AuditAction.FINDING_IGNORED;
            case REOPENED -> AuditAction.FINDING_REOPENED;
            default -> AuditAction.FINDING_CREATED;
        };

        auditService.record(
                workspaceId,
                actorId,
                auditAction,
                "SECRET_FINDING",
                finding.getId().toString(),
                Map.of("previousStatus", previousStatus.name(), "newStatus", newStatus.name(), "reason", reason != null ? reason : "")
        );

        return findingRepository.save(finding);
    }

    @Transactional(readOnly = true)
    public WhyExposedExplanation generateWhyExposed(UUID workspaceId, UUID findingId, UUID actorId) {
        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_VIEW);

        SecretFinding finding = findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Secret finding not found: " + findingId));

        RepositoryEntity repo = null;
        if (finding.getRepositoryId() != null) {
            repo = repoRepository.findByIdAndWorkspaceId(finding.getRepositoryId(), workspaceId).orElse(null);
        }

        List<String> riskFactors = new ArrayList<>();
        if (repo != null && repo.getVisibility() == RepositoryVisibility.PUBLIC) {
            riskFactors.add("Repository visibility is PUBLIC: any external party with access can scrape credentials.");
        }
        if (finding.getMatchedSecretId() != null) {
            riskFactors.add("Cryptographic fingerprint matches an ACTIVE SecretVault managed secret inventory item.");
        }
        if (finding.getValidationStatus() == ValidationStatus.ACTIVE) {
            riskFactors.add("Live credential validation confirmed the token is ACTIVE on the provider.");
        }
        if (finding.getEntropy() != null && finding.getEntropy() >= 4.5) {
            riskFactors.add(String.format("Calculated Shannon entropy is high (%.2f bits/symbol), indicating non-trivial randomness.", finding.getEntropy()));
        }
        if (finding.getCommitSha() != null && !finding.getCommitSha().isBlank()) {
            riskFactors.add("Credential is present in Git commit history and persists across git objects.");
        }

        String recommendedRemediation = finding.getSeverity() == RepoFindingSeverity.CRITICAL
                ? "Trigger immediate emergency compromise workflow, rotate secret in SecretVault, and rewrite Git commit history."
                : "Remove hardcoded secret reference from source code and replace with runtime SecretVault injection.";

        return new WhyExposedExplanation(
                finding.getId(),
                finding.getSecretType().name(),
                finding.getSeverity().name(),
                finding.getConfidence().name(),
                finding.getMaskedValue(),
                finding.getFilePath(),
                finding.getLineNumber(),
                finding.getCommitSha(),
                finding.getBranch(),
                repo != null ? repo.getOwner() + "/" + repo.getName() : "Local Workspace Repo",
                repo != null ? repo.getVisibility().name() : "PRIVATE",
                finding.getMatchedSecretId() != null,
                finding.getMatchedSecretId(),
                finding.getValidationStatus().name(),
                riskFactors,
                recommendedRemediation
        );
    }

    @Transactional
    public FindingAllowlist allowlistFinding(
            UUID workspaceId,
            UUID repositoryId,
            String fingerprint,
            String detector,
            String path,
            String reason,
            Instant expiresAt,
            UUID actorId) {

        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_FINDING_MANAGE);

        FindingAllowlist allowlist = new FindingAllowlist();
        allowlist.setWorkspaceId(workspaceId);
        allowlist.setRepositoryId(repositoryId);
        allowlist.setFingerprint(fingerprint);
        allowlist.setDetectorType(detector);
        allowlist.setFilePattern(path);
        allowlist.setReason(reason);
        allowlist.setCreatedBy(actorId);
        allowlist.setExpiresAt(expiresAt);
        allowlist.setActive(true);

        FindingAllowlist saved = allowlistRepository.save(allowlist);

        // Update any existing findings with this fingerprint
        List<SecretFinding> matches = findingRepository.findByWorkspaceIdAndFingerprint(workspaceId, fingerprint);
        for (SecretFinding f : matches) {
            f.setStatus(RepoFindingStatus.IGNORED);
            findingRepository.save(f);
        }

        auditService.record(
                workspaceId,
                actorId,
                AuditAction.ALLOWLIST_CREATED,
                "FINDING_ALLOWLIST",
                saved.getId().toString(),
                Map.of("fingerprint", fingerprint, "reason", reason != null ? reason : "")
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public FindingStatsDto getWorkspaceStats(UUID workspaceId, UUID actorId) {
        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_VIEW);

        long total = findingRepository.countByWorkspaceId(workspaceId);
        long critical = findingRepository.countByWorkspaceIdAndSeverity(workspaceId, RepoFindingSeverity.CRITICAL);
        long high = findingRepository.countByWorkspaceIdAndSeverity(workspaceId, RepoFindingSeverity.HIGH);
        long medium = findingRepository.countByWorkspaceIdAndSeverity(workspaceId, RepoFindingSeverity.MEDIUM);
        long low = findingRepository.countByWorkspaceIdAndSeverity(workspaceId, RepoFindingSeverity.LOW);

        long active = findingRepository.countByWorkspaceIdAndStatusIn(
                workspaceId, List.of(RepoFindingStatus.DETECTED, RepoFindingStatus.CONFIRMED, RepoFindingStatus.REMEDIATION_PENDING));
        long resolved = findingRepository.countByWorkspaceIdAndStatus(workspaceId, RepoFindingStatus.RESOLVED);
        long falsePositive = findingRepository.countByWorkspaceIdAndStatus(workspaceId, RepoFindingStatus.FALSE_POSITIVE);

        return new FindingStatsDto(total, critical, high, medium, low, active, resolved, falsePositive);
    }
}
