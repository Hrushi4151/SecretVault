package com.secretvault.secret.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.secret.dto.RollbackSecretRequest;
import com.secretvault.secret.dto.SecretVersionResponse;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.entity.VersionType;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

@Service
public class SecretRollbackService {

    private static final Logger log = LoggerFactory.getLogger(SecretRollbackService.class);

    private final SecretRepository secretRepository;
    private final SecretVersionRepository secretVersionRepository;
    private final EncryptionService encryptionService;
    private final AuditService auditService;
    private final SecretAuthorizationHelper authHelper;

    public SecretRollbackService(
            SecretRepository secretRepository,
            SecretVersionRepository secretVersionRepository,
            EncryptionService encryptionService,
            AuditService auditService,
            SecretAuthorizationHelper authHelper
    ) {
        this.secretRepository = secretRepository;
        this.secretVersionRepository = secretVersionRepository;
        this.encryptionService = encryptionService;
        this.auditService = auditService;
        this.authHelper = authHelper;
    }

    /**
     * Executes atomic rollback-as-a-new-version (vN+1) with fresh encryption keys and conflict detection.
     */
    @Transactional
    public SecretVersionResponse rollbackSecret(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            RollbackSecretRequest request,
            UUID userId,
            String requestId,
            String ipAddress
    ) {
        SecretAuthorizationHelper.WorkspaceContext context = authHelper.verifyHierarchyAndWriteAccess(
                workspaceId, projectId, environmentId, userId
        );

        // 1. Acquire pessimistic lock on Secret row
        Secret secret = secretRepository.findByIdAndEnvironmentIdForUpdate(secretId, environmentId)
                .orElseThrow(() -> ApiException.notFound("Secret not found in this environment"));

        if (secret.getStatus() == SecretStatus.DELETED) {
            throw ApiException.badRequest("Cannot rollback a deleted secret");
        }

        // 2. Optimistic conflict detection on expected current version
        if (request.expectedCurrentVersion() != null && !request.expectedCurrentVersion().equals(secret.getCurrentVersionNumber())) {
            throw ApiException.conflict(
                    "Rollback conflict: Secret current version is v" + secret.getCurrentVersionNumber()
                            + ", but client expected v" + request.expectedCurrentVersion()
            );
        }

        // 3. Locate and validate target historical version
        SecretVersion targetVersion = secretVersionRepository.findBySecretIdAndVersionNumber(secretId, request.targetVersion())
                .orElseThrow(() -> ApiException.notFound("Target rollback version " + request.targetVersion() + " not found"));

        if (targetVersion.getVersionNumber().equals(secret.getCurrentVersionNumber())) {
            throw ApiException.badRequest("Secret is already at version " + targetVersion.getVersionNumber());
        }

        // 4. Decrypt target version in memory using target's AAD
        EncryptedPayload targetPayload = new EncryptedPayload(
                targetVersion.getCiphertext(),
                targetVersion.getEncryptedDek(),
                targetVersion.getIv(),
                targetVersion.getAuthTag(),
                targetVersion.getKeyReference()
        );
        String targetAad = SecretAuthorizationHelper.buildAad(secret.getId(), environmentId, targetVersion.getVersionNumber());
        byte[] targetPlaintextBytes = encryptionService.decrypt(targetPayload, targetAad);

        // 5. Encrypt target plaintext into a brand new version (vN+1) with fresh DEK, IV, and new AAD
        int nextVersionNumber = secret.getCurrentVersionNumber() + 1;
        String nextAad = SecretAuthorizationHelper.buildAad(secret.getId(), environmentId, nextVersionNumber);
        EncryptedPayload newPayload = encryptionService.encrypt(targetPlaintextBytes, nextAad);

        // Memory zeroization
        Arrays.fill(targetPlaintextBytes, (byte) 0);

        String reason = StringUtils.hasText(request.reason())
                ? request.reason().trim()
                : "Rollback to version " + targetVersion.getVersionNumber();

        SecretVersion rollbackVersion = new SecretVersion(
                secret.getId(),
                nextVersionNumber,
                VersionType.ROLLBACK,
                newPayload.ciphertext(),
                newPayload.encryptedDek(),
                newPayload.iv(),
                newPayload.authTag(),
                newPayload.keyReference(),
                userId,
                reason,
                targetVersion.getId(),
                null,
                null,
                null
        );
        rollbackVersion = secretVersionRepository.save(rollbackVersion);

        // 6. Advance current version pointer
        secret.setCurrentVersionNumber(nextVersionNumber);
        secretRepository.save(secret);

        // 7. Record audit log
        auditService.recordSecretAudit(
                context.workspace().getOrganizationId(),
                workspaceId,
                userId,
                AuditAction.SECRET_ROLLBACK_COMPLETED,
                secret.getId(),
                requestId,
                ipAddress,
                "SUCCESS"
        );

        log.info("Rolled back secret [{}] to target v{} as new version v{} by user [{}] in workspace [{}]",
                secret.getId(), targetVersion.getVersionNumber(), nextVersionNumber, userId, workspaceId);

        return SecretVersionResponse.fromEntity(rollbackVersion, Collections.emptyList(), true);
    }
}
