package com.secretvault.repository.service;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.repository.engine.*;
import com.secretvault.repository.entity.*;
import com.secretvault.repository.model.*;
import com.secretvault.repository.repository.*;
import com.secretvault.security.finding.dto.SecurityFindingDraft;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.model.FindingConfidence;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.security.finding.service.SecurityFindingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@Service
public class RepositoryScanService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryScanService.class);

    private final RepositoryScanRepository scanRepository;
    private final RepositoryEntityRepository repoRepository;
    private final SecretFindingRepository findingRepository;
    private final SecretFindingOccurrenceRepository occurrenceRepository;
    private final FindingAllowlistRepository allowlistRepository;
    private final RepositoryPolicyService policyService;
    private final RepositorySourceAdapter sourceAdapter;
    private final GitRepositoryScanner gitScanner;
    private final SecretInventoryCorrelator correlator;
    private final SecurityFindingService securityFindingService;
    private final EffectiveAccessService accessService;
    private final AuditService auditService;

    public RepositoryScanService(
            RepositoryScanRepository scanRepository,
            RepositoryEntityRepository repoRepository,
            SecretFindingRepository findingRepository,
            SecretFindingOccurrenceRepository occurrenceRepository,
            FindingAllowlistRepository allowlistRepository,
            RepositoryPolicyService policyService,
            RepositorySourceAdapter sourceAdapter,
            GitRepositoryScanner gitScanner,
            SecretInventoryCorrelator correlator,
            SecurityFindingService securityFindingService,
            EffectiveAccessService accessService,
            AuditService auditService) {
        this.scanRepository = scanRepository;
        this.repoRepository = repoRepository;
        this.findingRepository = findingRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.allowlistRepository = allowlistRepository;
        this.policyService = policyService;
        this.sourceAdapter = sourceAdapter;
        this.gitScanner = gitScanner;
        this.correlator = correlator;
        this.securityFindingService = securityFindingService;
        this.accessService = accessService;
        this.auditService = auditService;
    }

    @Transactional
    public RepositoryScan triggerRepositoryScan(
            UUID workspaceId,
            UUID repositoryId,
            ScanType scanType,
            String branch,
            UUID actorId) {

        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_SCAN);

        RepositoryEntity repo = repoRepository.findByIdAndWorkspaceId(repositoryId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        RepositorySecurityPolicy policy = policyService.getEffectivePolicy(workspaceId, repositoryId);

        RepositoryScan scan = new RepositoryScan();
        scan.setWorkspaceId(workspaceId);
        scan.setRepositoryId(repositoryId);
        scan.setScanType(scanType != null ? scanType : ScanType.INCREMENTAL);
        scan.setBranch(branch != null ? branch : repo.getDefaultBranch());
        scan.setStatus(ScanStatus.QUEUED);
        scan.setTriggeredBy(actorId);

        RepositoryScan savedScan = scanRepository.save(scan);

        auditService.record(
                workspaceId,
                actorId,
                AuditAction.SCAN_CREATED,
                "REPOSITORY_SCAN",
                savedScan.getId().toString(),
                Map.of("repositoryId", repositoryId.toString(), "scanType", savedScan.getScanType().name())
        );

        // Execute scan synchronously or via worker pipeline
        executeScanPipeline(savedScan, repo, policy, actorId);

        return scanRepository.findById(savedScan.getId()).orElse(savedScan);
    }

    @Transactional
    public RepositoryScan executeLocalDirectoryScan(
            UUID workspaceId,
            UUID repositoryId,
            String localPath,
            boolean scanHistory,
            UUID actorId) {

        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_SCAN);

        RepositoryEntity repo = null;
        if (repositoryId != null) {
            repo = repoRepository.findByIdAndWorkspaceId(repositoryId, workspaceId).orElse(null);
        }

        RepositorySecurityPolicy policy = policyService.getEffectivePolicy(workspaceId, repositoryId);

        RepositoryScan scan = new RepositoryScan();
        scan.setWorkspaceId(workspaceId);
        scan.setRepositoryId(repositoryId);
        scan.setScanType(scanHistory ? ScanType.GIT_HISTORY : ScanType.FULL);
        scan.setStatus(ScanStatus.QUEUED);
        scan.setTriggeredBy(actorId);
        RepositoryScan savedScan = scanRepository.save(scan);

        try (RepositorySourceAdapter.SourceContext ctx = sourceAdapter.acquireLocalDirectory(localPath)) {
            runScanOnDirectory(savedScan, repo, policy, ctx.sourceDir(), scanHistory, actorId);
        } catch (Exception e) {
            markScanFailed(savedScan, "Local directory scan failed: " + e.getMessage(), actorId);
        }

        return scanRepository.findById(savedScan.getId()).orElse(savedScan);
    }

    @Transactional
    public RepositoryScan executeArchiveScan(
            UUID workspaceId,
            UUID repositoryId,
            InputStream archiveStream,
            UUID actorId) {

        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_SCAN);

        RepositoryEntity repo = repositoryId != null ? repoRepository.findByIdAndWorkspaceId(repositoryId, workspaceId).orElse(null) : null;
        RepositorySecurityPolicy policy = policyService.getEffectivePolicy(workspaceId, repositoryId);

        RepositoryScan scan = new RepositoryScan();
        scan.setWorkspaceId(workspaceId);
        scan.setRepositoryId(repositoryId);
        scan.setScanType(ScanType.ARCHIVE);
        scan.setStatus(ScanStatus.QUEUED);
        scan.setTriggeredBy(actorId);
        RepositoryScan savedScan = scanRepository.save(scan);

        try (RepositorySourceAdapter.SourceContext ctx = sourceAdapter.acquireArchive(archiveStream)) {
            runScanOnDirectory(savedScan, repo, policy, ctx.sourceDir(), false, actorId);
        } catch (Exception e) {
            markScanFailed(savedScan, "Archive scan failed: " + e.getMessage(), actorId);
        }

        return scanRepository.findById(savedScan.getId()).orElse(savedScan);
    }

    private void executeScanPipeline(
            RepositoryScan scan, RepositoryEntity repo, RepositorySecurityPolicy policy, UUID actorId) {

        scan.setStatus(ScanStatus.CLONING);
        scan.setStartedAt(Instant.now());
        scanRepository.save(scan);

        auditService.record(
                scan.getWorkspaceId(),
                actorId,
                AuditAction.SCAN_STARTED,
                "REPOSITORY_SCAN",
                scan.getId().toString(),
                Map.of("repository", repo.getOwner() + "/" + repo.getName())
        );

        if (repo.getCloneUrl() == null || repo.getCloneUrl().isBlank()) {
            markScanFailed(scan, "Repository has no clone URL configured", actorId);
            return;
        }

        try (RepositorySourceAdapter.SourceContext ctx = sourceAdapter.cloneGitRepository(repo.getCloneUrl(), scan.getBranch())) {
            boolean includeHistory = policy.isScanHistory() || scan.getScanType() == ScanType.GIT_HISTORY;
            runScanOnDirectory(scan, repo, policy, ctx.sourceDir(), includeHistory, actorId);
        } catch (Exception e) {
            log.error("Scan pipeline execution failed for scan {}", scan.getId(), e);
            markScanFailed(scan, "Scan failure: " + e.getMessage(), actorId);
        }
    }

    private void runScanOnDirectory(
            RepositoryScan scan,
            RepositoryEntity repo,
            RepositorySecurityPolicy policy,
            Path sourceDir,
            boolean includeHistory,
            UUID actorId) {

        scan.setStatus(ScanStatus.SCANNING);
        scanRepository.save(scan);

        int maxCommits = policy != null ? policy.getMaxHistoryDepth() : 500;
        String baseSha = (repo != null && scan.getScanType() == ScanType.INCREMENTAL) ? repo.getLastCommitSha() : null;

        GitRepositoryScanner.GitScanOutput output;
        if (includeHistory) {
            output = gitScanner.scanGitHistory(sourceDir, baseSha, null, maxCommits);
        } else {
            output = gitScanner.scanWorkingTree(sourceDir, scan.getBranch());
        }

        scan.setStatus(ScanStatus.CLASSIFYING);
        scan.setFilesScanned(output.filesScanned());
        scan.setCommitsScanned(output.commitsScanned());
        scan.setCommitSha(output.headCommitSha());
        scanRepository.save(scan);

        List<FindingAllowlist> activeAllowlists = allowlistRepository.findActiveAllowlists(
                scan.getWorkspaceId(), scan.getRepositoryId(), Instant.now());

        Set<String> allowedFingerprints = new HashSet<>();
        for (FindingAllowlist al : activeAllowlists) {
            allowedFingerprints.add(al.getFingerprint());
        }

        int findingsCount = 0;
        int highRiskCount = 0;
        List<SecretFinding> persistedFindings = new ArrayList<>();

        for (SecretDetectionResult result : output.findings()) {
            boolean isAllowlisted = allowedFingerprints.contains(result.fingerprint());

            // Correlate with SecretVault inventory
            SecretInventoryCorrelator.CorrelationResult correlation = correlator.correlateByFingerprint(result.fingerprint());
            UUID matchedSecretId = correlation.isMatched() ? correlation.secretId() : null;

            RepoFindingSeverity effectiveSeverity = correlation.isMatched()
                    ? RepoFindingSeverity.CRITICAL
                    : result.severity();

            // Check if finding already exists in this repository
            Optional<SecretFinding> existingFindingOpt = findingRepository.findByWorkspaceIdAndRepositoryIdAndFingerprint(
                    scan.getWorkspaceId(), scan.getRepositoryId(), result.fingerprint());

            SecretFinding finding;
            if (existingFindingOpt.isPresent()) {
                finding = existingFindingOpt.get();
                finding.setLastSeenAt(Instant.now());
                finding.setScanId(scan.getId());
                if (finding.getStatus() == RepoFindingStatus.RESOLVED && !isAllowlisted) {
                    finding.setStatus(RepoFindingStatus.REOPENED);
                }
            } else {
                finding = new SecretFinding();
                finding.setWorkspaceId(scan.getWorkspaceId());
                finding.setRepositoryId(scan.getRepositoryId());
                finding.setScanId(scan.getId());
                finding.setFingerprint(result.fingerprint());
                finding.setDetectorType(result.detectorType());
                finding.setSecretType(result.secretType());
                finding.setSeverity(effectiveSeverity);
                finding.setConfidence(result.confidence());
                finding.setStatus(isAllowlisted ? RepoFindingStatus.IGNORED : RepoFindingStatus.DETECTED);
                finding.setFilePath(result.filePath());
                finding.setLineNumber(result.lineNumber());
                finding.setColumnNumber(result.columnNumber());
                finding.setMaskedValue(result.maskedValue());
                finding.setCommitSha(result.commitSha());
                finding.setBranch(result.branch());
                finding.setAuthor(result.author());
                finding.setEntropy(result.entropy());
                finding.setValidationStatus(ValidationStatus.UNKNOWN);
                finding.setRemediationStatus(RemediationStatus.PENDING);
                finding.setMatchedSecretId(matchedSecretId);
                finding.setFirstSeenAt(Instant.now());
                finding.setLastSeenAt(Instant.now());

                auditService.record(
                        scan.getWorkspaceId(),
                        actorId,
                        AuditAction.FINDING_CREATED,
                        "SECRET_FINDING",
                        finding.getFingerprint(),
                        Map.of("secretType", finding.getSecretType().name(), "severity", finding.getSeverity().name())
                );
            }

            SecretFinding savedFinding = findingRepository.save(finding);
            persistedFindings.add(savedFinding);

            // Record Occurrence
            SecretFindingOccurrence occ = new SecretFindingOccurrence();
            occ.setFindingId(savedFinding.getId());
            occ.setScanId(scan.getId());
            occ.setCommitSha(result.commitSha());
            occ.setBranch(result.branch());
            occ.setFilePath(result.filePath());
            occ.setLineNumber(result.lineNumber());
            occ.setDetector(result.detectorType());
            occ.setFingerprint(result.fingerprint());
            occ.setFirstSeen(Instant.now());
            occ.setLastSeen(Instant.now());
            occurrenceRepository.save(occ);

            if (!isAllowlisted) {
                findingsCount++;
                if (effectiveSeverity == RepoFindingSeverity.CRITICAL || effectiveSeverity == RepoFindingSeverity.HIGH) {
                    highRiskCount++;
                    // Propagate to Security Center
                    mirrorToSecurityCenter(scan, savedFinding, result);
                }
            }
        }

        // Finalize Scan
        scan.setStatus(ScanStatus.COMPLETED);
        scan.setCompletedAt(Instant.now());
        scan.setFindingsCount(findingsCount);
        scan.setHighRiskCount(highRiskCount);
        scanRepository.save(scan);

        if (repo != null) {
            repo.setLastScanAt(Instant.now());
            repo.setLastSuccessfulScanAt(Instant.now());
            if (output.headCommitSha() != null) {
                repo.setLastCommitSha(output.headCommitSha());
            }
            repoRepository.save(repo);
        }

        auditService.record(
                scan.getWorkspaceId(),
                actorId,
                AuditAction.SCAN_COMPLETED,
                "REPOSITORY_SCAN",
                scan.getId().toString(),
                Map.of("filesScanned", output.filesScanned(), "findingsCount", findingsCount, "highRiskCount", highRiskCount)
        );
    }

    private void mirrorToSecurityCenter(RepositoryScan scan, SecretFinding finding, SecretDetectionResult result) {
        try {
            FindingSeverity secSeverity = switch (finding.getSeverity()) {
                case CRITICAL -> FindingSeverity.CRITICAL;
                case HIGH -> FindingSeverity.HIGH;
                case MEDIUM -> FindingSeverity.MEDIUM;
                default -> FindingSeverity.LOW;
            };

            FindingConfidence secConf = switch (finding.getConfidence()) {
                case VERY_HIGH, HIGH -> FindingConfidence.HIGH;
                case MEDIUM -> FindingConfidence.MEDIUM;
                default -> FindingConfidence.LOW;
            };

            SecurityFindingDraft draft = new SecurityFindingDraft(
                    scan.getWorkspaceId(),
                    null,
                    null,
                    FindingCategory.REPOSITORY_SECRET_EXPOSURE,
                    secSeverity,
                    secConf,
                    "Exposed " + finding.getSecretType() + " in " + finding.getFilePath(),
                    "Detected " + finding.getSecretType() + " credential (" + finding.getMaskedValue() + ") in repository file " + finding.getFilePath() + " at line " + finding.getLineNumber(),
                    "Immediately revoke or rotate the exposed secret and rewrite commit history or remove the secret reference from the file.",
                    "repo-finding-" + finding.getFingerprint(),
                    Map.of(
                            "fingerprint", finding.getFingerprint(),
                            "maskedValue", finding.getMaskedValue(),
                            "filePath", finding.getFilePath(),
                            "lineNumber", finding.getLineNumber() != null ? finding.getLineNumber() : 1,
                            "secretType", finding.getSecretType().name(),
                            "repositoryId", scan.getRepositoryId() != null ? scan.getRepositoryId().toString() : "local"
                    )
            );
            securityFindingService.upsertFinding(draft);
        } catch (Exception e) {
            log.warn("Could not mirror finding {} to Security Center: {}", finding.getId(), e.getMessage());
        }
    }

    private void markScanFailed(RepositoryScan scan, String errorMessage, UUID actorId) {
        scan.setStatus(ScanStatus.FAILED);
        scan.setErrorMessage(errorMessage);
        scan.setCompletedAt(Instant.now());
        scanRepository.save(scan);

        auditService.record(
                scan.getWorkspaceId(),
                actorId,
                AuditAction.SCAN_FAILED,
                "REPOSITORY_SCAN",
                scan.getId().toString(),
                Map.of("error", errorMessage)
        );
    }

    @Transactional(readOnly = true)
    public Page<RepositoryScan> listScans(UUID workspaceId, UUID repositoryId, UUID actorId, Pageable pageable) {
        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_VIEW);
        if (repositoryId != null) {
            return scanRepository.findByWorkspaceIdAndRepositoryId(workspaceId, repositoryId, pageable);
        }
        return scanRepository.findByWorkspaceId(workspaceId, pageable);
    }

    @Transactional(readOnly = true)
    public RepositoryScan getScan(UUID workspaceId, UUID scanId, UUID actorId) {
        accessService.requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_VIEW);
        return scanRepository.findByIdAndWorkspaceId(scanId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Scan not found: " + scanId));
    }
}
