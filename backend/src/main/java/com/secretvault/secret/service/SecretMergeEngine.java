package com.secretvault.secret.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.secret.dto.BranchMergeRequest;
import com.secretvault.secret.dto.BranchMergeResponse;
import com.secretvault.secret.entity.BranchStatus;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretBranch;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.entity.VersionType;
import com.secretvault.secret.repository.SecretBranchRepository;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class SecretMergeEngine {

    private static final Logger log = LoggerFactory.getLogger(SecretMergeEngine.class);

    private final SecretRepository secretRepository;
    private final SecretVersionRepository secretVersionRepository;
    private final SecretBranchRepository branchRepository;
    private final EncryptionService encryptionService;
    private final AuditService auditService;
    private final SecretAuthorizationHelper authHelper;

    public SecretMergeEngine(
            SecretRepository secretRepository,
            SecretVersionRepository secretVersionRepository,
            SecretBranchRepository branchRepository,
            EncryptionService encryptionService,
            AuditService auditService,
            SecretAuthorizationHelper authHelper
    ) {
        this.secretRepository = secretRepository;
        this.secretVersionRepository = secretVersionRepository;
        this.branchRepository = branchRepository;
        this.encryptionService = encryptionService;
        this.auditService = auditService;
        this.authHelper = authHelper;
    }

    /**
     * Executes 3-way merge from feature branch into canonical main trunk.
     */
    @Transactional
    public BranchMergeResponse mergeBranch(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            UUID branchId,
            BranchMergeRequest request,
            UUID userId,
            String requestId,
            String ipAddress
    ) {
        SecretAuthorizationHelper.WorkspaceContext context = authHelper.verifyHierarchyAndBranchWriteAccess(
                workspaceId, projectId, environmentId, userId
        );

        // 1. Lock Secret row
        Secret secret = secretRepository.findByIdAndEnvironmentIdForUpdate(secretId, environmentId)
                .orElseThrow(() -> ApiException.notFound("Secret not found in this environment"));

        if (secret.getStatus() == SecretStatus.DELETED) {
            throw ApiException.badRequest("Cannot merge to a deleted secret");
        }

        SecretBranch branch = branchRepository.findByIdAndSecretId(branchId, secretId)
                .orElseThrow(() -> ApiException.notFound("Branch not found for this secret"));

        if (branch.getStatus() != BranchStatus.ACTIVE) {
            throw ApiException.badRequest("Cannot merge a branch in " + branch.getStatus() + " status");
        }

        SecretVersion baseVer = secretVersionRepository.findById(branch.getBaseVersionId())
                .orElseThrow(() -> ApiException.notFound("Base version of branch not found"));

        SecretVersion headVer = secretVersionRepository.findById(branch.getHeadVersionId())
                .orElseThrow(() -> ApiException.notFound("Head version of branch not found"));

        SecretVersion mainVer = secretVersionRepository.findBySecretIdAndVersionNumber(secretId, secret.getCurrentVersionNumber())
                .orElseThrow(() -> ApiException.notFound("Current main version not found"));

        // 2. Concurrency checks on expected versions
        if (request != null) {
            if (request.expectedMainVersion() != null && !request.expectedMainVersion().equals(mainVer.getVersionNumber())) {
                throw ApiException.conflict("Merge conflict: Main version has changed to v" + mainVer.getVersionNumber()
                        + ", expected v" + request.expectedMainVersion());
            }
            if (request.expectedBranchHeadVersion() != null && !request.expectedBranchHeadVersion().equals(headVer.getVersionNumber())) {
                throw ApiException.conflict("Merge conflict: Branch head has changed to v" + headVer.getVersionNumber()
                        + ", expected v" + request.expectedBranchHeadVersion());
            }
        }

        // 3. Decrypt 3 versions in memory (BASE, OURS = main, THEIRS = branch)
        byte[] baseBytes = decryptVersion(secret.getId(), environmentId, baseVer);
        byte[] oursBytes = decryptVersion(secret.getId(), environmentId, mainVer);
        byte[] theirsBytes = decryptVersion(secret.getId(), environmentId, headVer);

        boolean mainChanged = !Arrays.equals(baseBytes, oursBytes);
        boolean branchChanged = !Arrays.equals(baseBytes, theirsBytes);
        boolean identicalChanges = Arrays.equals(oursBytes, theirsBytes);

        // Zero base and ours early if not needed
        Arrays.fill(baseBytes, (byte) 0);

        if (mainChanged && branchChanged && !identicalChanges) {
            Arrays.fill(oursBytes, (byte) 0);
            Arrays.fill(theirsBytes, (byte) 0);
            throw ApiException.conflict("3-Way Merge Conflict: Both main (v" + mainVer.getVersionNumber()
                    + ") and branch " + branch.getName() + " (v" + headVer.getVersionNumber()
                    + ") have modified secret value differently since base (v" + baseVer.getVersionNumber() + ")");
        }

        Arrays.fill(oursBytes, (byte) 0);

        int resultVersionNumber = secret.getCurrentVersionNumber();
        if (branchChanged) {
            // Case 1 & 3: Branch changes need to be committed to main as a new MERGE version
            int nextVersionNumber = secret.getCurrentVersionNumber() + 1;
            Optional<SecretVersion> topVer = secretVersionRepository.findTopBySecretIdOrderByVersionNumberDesc(secretId);
            if (topVer.isPresent() && topVer.get().getVersionNumber() >= nextVersionNumber) {
                nextVersionNumber = topVer.get().getVersionNumber() + 1;
            }

            String aad = SecretAuthorizationHelper.buildAad(secret.getId(), environmentId, nextVersionNumber);
            EncryptedPayload payload = encryptionService.encrypt(theirsBytes, aad);

            String reason = (request != null && StringUtils.hasText(request.reason()))
                    ? request.reason().trim()
                    : "Merged branch " + branch.getName() + " (v" + headVer.getVersionNumber() + ") into main";

            SecretVersion mergeVersion = new SecretVersion(
                    secret.getId(),
                    nextVersionNumber,
                    VersionType.MERGE,
                    payload.ciphertext(),
                    payload.encryptedDek(),
                    payload.iv(),
                    payload.authTag(),
                    payload.keyReference(),
                    userId,
                    reason,
                    headVer.getId(),
                    null,
                    null,
                    null
            );
            secretVersionRepository.save(mergeVersion);

            secret.setCurrentVersionNumber(nextVersionNumber);
            secretRepository.save(secret);
            resultVersionNumber = nextVersionNumber;
        }

        Arrays.fill(theirsBytes, (byte) 0);

        // Mark branch as MERGED
        branch.setStatus(BranchStatus.MERGED);
        branch.setMergedAt(Instant.now());
        branch.setMergedBy(userId);
        branchRepository.save(branch);

        auditService.recordSecretAudit(
                context.workspace().getOrganizationId(),
                workspaceId,
                userId,
                AuditAction.SECRET_BRANCH_MERGED,
                secretId,
                requestId,
                ipAddress,
                "SUCCESS"
        );

        log.info("Merged branch [{}] into main for secret [{}] resulting in version v{} by user [{}]",
                branch.getName(), secretId, resultVersionNumber, userId);

        return new BranchMergeResponse(
                secretId,
                branch.getId(),
                branch.getName(),
                resultVersionNumber,
                "MERGED",
                "Branch '" + branch.getName() + "' successfully merged into main (v" + resultVersionNumber + ")"
        );
    }

    private byte[] decryptVersion(UUID secretId, UUID environmentId, SecretVersion version) {
        EncryptedPayload payload = new EncryptedPayload(
                version.getCiphertext(),
                version.getEncryptedDek(),
                version.getIv(),
                version.getAuthTag(),
                version.getKeyReference()
        );
        String aad = SecretAuthorizationHelper.buildAad(secretId, environmentId, version.getVersionNumber());
        return encryptionService.decrypt(payload, aad);
    }
}
