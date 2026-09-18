package com.secretvault.secret.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.secret.dto.BranchCommitRequest;
import com.secretvault.secret.dto.BranchComparisonResponse;
import com.secretvault.secret.dto.CreateBranchRequest;
import com.secretvault.secret.dto.SecretBranchResponse;
import com.secretvault.secret.dto.SecretVersionResponse;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class SecretBranchService {

    private static final Logger log = LoggerFactory.getLogger(SecretBranchService.class);
    public static final String MAIN_BRANCH = "main";

    private final SecretRepository secretRepository;
    private final SecretVersionRepository secretVersionRepository;
    private final SecretBranchRepository branchRepository;
    private final EncryptionService encryptionService;
    private final AuditService auditService;
    private final SecretAuthorizationHelper authHelper;

    public SecretBranchService(
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

    @Transactional
    public SecretBranchResponse createBranch(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            CreateBranchRequest request,
            UUID userId,
            String requestId,
            String ipAddress
    ) {
        SecretAuthorizationHelper.WorkspaceContext context = authHelper.verifyHierarchyAndBranchWriteAccess(
                workspaceId, projectId, environmentId, userId
        );

        Secret secret = secretRepository.findByIdAndEnvironmentId(secretId, environmentId)
                .orElseThrow(() -> ApiException.notFound("Secret not found in this environment"));

        if (secret.getStatus() == SecretStatus.DELETED) {
            throw ApiException.badRequest("Cannot create a branch for a deleted secret");
        }

        String normalizedName = request.name().trim();
        if (MAIN_BRANCH.equalsIgnoreCase(normalizedName)) {
            throw ApiException.badRequest("The 'main' branch is the canonical trunk and cannot be created as a custom branch");
        }

        if (branchRepository.existsBySecretIdAndName(secretId, normalizedName)) {
            throw ApiException.conflict("A branch named '" + normalizedName + "' already exists for this secret");
        }

        SecretVersion baseVersion = secretVersionRepository.findBySecretIdAndVersionNumber(secretId, request.fromVersion())
                .orElseThrow(() -> ApiException.notFound("Base version " + request.fromVersion() + " not found for this secret"));

        SecretBranch branch = new SecretBranch(
                secretId,
                normalizedName,
                request.description() != null ? request.description().trim() : null,
                baseVersion.getId(),
                baseVersion.getId(),
                userId
        );
        branch = branchRepository.save(branch);

        auditService.recordSecretAudit(
                context.workspace().getOrganizationId(),
                workspaceId,
                userId,
                AuditAction.SECRET_BRANCH_CREATED,
                secretId,
                requestId,
                ipAddress,
                "SUCCESS"
        );

        log.info("Created secret branch [{}] from v{} on secret [{}] by user [{}]",
                branch.getName(), baseVersion.getVersionNumber(), secretId, userId);

        return SecretBranchResponse.fromEntity(branch, baseVersion.getVersionNumber(), baseVersion.getVersionNumber());
    }

    @Transactional(readOnly = true)
    public List<SecretBranchResponse> getBranches(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            UUID userId
    ) {
        SecretAuthorizationHelper.WorkspaceContext context = authHelper.verifyHierarchyAndReadAccess(
                workspaceId, projectId, environmentId, userId
        );

        Secret secret = secretRepository.findByIdAndEnvironmentId(secretId, environmentId)
                .orElseThrow(() -> ApiException.notFound("Secret not found in this environment"));

        List<SecretBranchResponse> responses = new ArrayList<>();

        // Add virtual canonical "main" branch at the top
        SecretVersion currentMainVer = secretVersionRepository.findBySecretIdAndVersionNumber(secretId, secret.getCurrentVersionNumber())
                .orElse(null);

        SecretVersion initialMainVer = secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 1)
                .orElse(currentMainVer);

        responses.add(new SecretBranchResponse(
                null,
                secretId,
                MAIN_BRANCH,
                "Canonical trunk branch",
                initialMainVer != null ? initialMainVer.getId() : null,
                1,
                currentMainVer != null ? currentMainVer.getId() : null,
                secret.getCurrentVersionNumber(),
                BranchStatus.ACTIVE,
                secret.getCreatedBy(),
                secret.getCreatedAt(),
                secret.getUpdatedAt(),
                null,
                null
        ));

        // Feature branches are only permitted in DEVELOPMENT environments
        if (context.environment().getEnvType() == com.secretvault.environment.entity.EnvType.DEVELOPMENT) {
            List<SecretBranch> customBranches = branchRepository.findBySecretId(secretId);
            for (SecretBranch b : customBranches) {
                Integer baseNum = resolveVersionNumber(b.getBaseVersionId());
                Integer headNum = resolveVersionNumber(b.getHeadVersionId());
                responses.add(SecretBranchResponse.fromEntity(b, baseNum, headNum));
            }
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public SecretBranchResponse getBranchById(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            UUID branchId,
            UUID userId
    ) {
        authHelper.verifyHierarchyAndBranchReadAccess(workspaceId, projectId, environmentId, userId);

        if (!secretRepository.existsById(secretId)) {
            throw ApiException.notFound("Secret not found");
        }

        SecretBranch branch = branchRepository.findByIdAndSecretId(branchId, secretId)
                .orElseThrow(() -> ApiException.notFound("Branch not found for this secret"));

        Integer baseNum = resolveVersionNumber(branch.getBaseVersionId());
        Integer headNum = resolveVersionNumber(branch.getHeadVersionId());
        return SecretBranchResponse.fromEntity(branch, baseNum, headNum);
    }

    @Transactional
    public SecretVersionResponse createBranchVersion(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            UUID branchId,
            BranchCommitRequest request,
            UUID userId,
            String requestId,
            String ipAddress
    ) {
        SecretAuthorizationHelper.WorkspaceContext context = authHelper.verifyHierarchyAndBranchWriteAccess(
                workspaceId, projectId, environmentId, userId
        );

        Secret secret = secretRepository.findByIdAndEnvironmentIdForUpdate(secretId, environmentId)
                .orElseThrow(() -> ApiException.notFound("Secret not found in this environment"));

        if (secret.getStatus() == SecretStatus.DELETED) {
            throw ApiException.badRequest("Cannot commit to a branch of a deleted secret");
        }

        SecretBranch branch = branchRepository.findByIdAndSecretId(branchId, secretId)
                .orElseThrow(() -> ApiException.notFound("Branch not found for this secret"));

        if (branch.getStatus() != BranchStatus.ACTIVE) {
            throw ApiException.badRequest("Cannot commit to a branch in " + branch.getStatus() + " status");
        }

        // Verify expected head version
        if (request.expectedHeadVersion() != null) {
            Integer actualHeadNum = resolveVersionNumber(branch.getHeadVersionId());
            if (!Objects.equals(actualHeadNum, request.expectedHeadVersion())) {
                throw ApiException.conflict("Branch head has changed. Expected v" + request.expectedHeadVersion() + " but found v" + actualHeadNum);
            }
        }

        // Determine next overall version number for this secret
        int nextVersionNumber = secret.getCurrentVersionNumber() + 1;
        Optional<SecretVersion> topVer = secretVersionRepository.findTopBySecretIdOrderByVersionNumberDesc(secretId);
        if (topVer.isPresent() && topVer.get().getVersionNumber() >= nextVersionNumber) {
            nextVersionNumber = topVer.get().getVersionNumber() + 1;
        }

        // Encrypt branch payload
        String aad = SecretAuthorizationHelper.buildAad(secret.getId(), environmentId, nextVersionNumber);
        EncryptedPayload payload = encryptionService.encrypt(
                request.value().getBytes(StandardCharsets.UTF_8),
                aad
        );

        String reason = StringUtils.hasText(request.reason())
                ? request.reason().trim()
                : "Branch commit on " + branch.getName();

        SecretVersion newVersion = new SecretVersion(
                secret.getId(),
                nextVersionNumber,
                VersionType.BRANCH_COMMIT,
                payload.ciphertext(),
                payload.encryptedDek(),
                payload.iv(),
                payload.authTag(),
                payload.keyReference(),
                userId,
                reason,
                branch.getHeadVersionId(),
                null,
                null,
                branch.getId()
        );
        newVersion = secretVersionRepository.save(newVersion);

        // Advance branch head without modifying main currentVersionNumber
        branch.setHeadVersionId(newVersion.getId());
        branchRepository.save(branch);

        auditService.recordSecretAudit(
                context.workspace().getOrganizationId(),
                workspaceId,
                userId,
                AuditAction.SECRET_BRANCH_COMMITTED,
                secretId,
                requestId,
                ipAddress,
                "SUCCESS"
        );

        log.info("Committed version v{} on branch [{}] of secret [{}] by user [{}]",
                nextVersionNumber, branch.getName(), secretId, userId);

        return SecretVersionResponse.fromEntity(newVersion, Collections.emptyList(), false);
    }

    @Transactional(readOnly = true)
    public BranchComparisonResponse compareBranchWithMain(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            UUID branchId,
            UUID userId
    ) {
        authHelper.verifyHierarchyAndBranchReadAccess(workspaceId, projectId, environmentId, userId);

        Secret secret = secretRepository.findByIdAndEnvironmentId(secretId, environmentId)
                .orElseThrow(() -> ApiException.notFound("Secret not found in this environment"));

        SecretBranch branch = branchRepository.findByIdAndSecretId(branchId, secretId)
                .orElseThrow(() -> ApiException.notFound("Branch not found for this secret"));

        Integer baseNum = resolveVersionNumber(branch.getBaseVersionId());
        Integer headNum = resolveVersionNumber(branch.getHeadVersionId());
        Integer mainNum = secret.getCurrentVersionNumber();

        boolean hasDiverged = !Objects.equals(baseNum, mainNum) || !Objects.equals(baseNum, headNum);
        boolean mainUnchanged = Objects.equals(baseNum, mainNum);
        boolean branchUnchanged = Objects.equals(baseNum, headNum);

        String mergeStatus;
        boolean canAutoMerge;
        String details;

        if (branchUnchanged && mainUnchanged) {
            mergeStatus = "UP_TO_DATE";
            canAutoMerge = true;
            details = "Branch and main are at the identical base version";
        } else if (mainUnchanged && !branchUnchanged) {
            mergeStatus = "FAST_FORWARD";
            canAutoMerge = true;
            details = "Main has not moved since branch creation. Fast-forward merge is possible.";
        } else if (!mainUnchanged && branchUnchanged) {
            mergeStatus = "UP_TO_DATE";
            canAutoMerge = true;
            details = "Main has advanced but branch has no new commits.";
        } else {
            // Both have moved
            mergeStatus = "CLEAN_MERGE";
            canAutoMerge = true;
            details = "Both branch and main have progressed. 3-way merge check required.";
        }

        return new BranchComparisonResponse(
                secretId,
                branch.getName(),
                baseNum,
                mainNum,
                headNum,
                hasDiverged,
                canAutoMerge,
                mergeStatus,
                details
        );
    }

    @Transactional
    public SecretBranchResponse archiveBranch(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            UUID branchId,
            UUID userId,
            String requestId,
            String ipAddress
    ) {
        SecretAuthorizationHelper.WorkspaceContext context = authHelper.verifyHierarchyAndBranchWriteAccess(
                workspaceId, projectId, environmentId, userId
        );

        SecretBranch branch = branchRepository.findByIdAndSecretId(branchId, secretId)
                .orElseThrow(() -> ApiException.notFound("Branch not found for this secret"));

        branch.setStatus(BranchStatus.ARCHIVED);
        branch = branchRepository.save(branch);

        auditService.recordSecretAudit(
                context.workspace().getOrganizationId(),
                workspaceId,
                userId,
                AuditAction.SECRET_BRANCH_ARCHIVED,
                secretId,
                requestId,
                ipAddress,
                "SUCCESS"
        );

        Integer baseNum = resolveVersionNumber(branch.getBaseVersionId());
        Integer headNum = resolveVersionNumber(branch.getHeadVersionId());
        return SecretBranchResponse.fromEntity(branch, baseNum, headNum);
    }

    private Integer resolveVersionNumber(UUID versionId) {
        if (versionId == null) return null;
        return secretVersionRepository.findById(versionId)
                .map(SecretVersion::getVersionNumber)
                .orElse(null);
    }
}
