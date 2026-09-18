package com.secretvault.secret.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.secret.dto.ExecutePromotionRequest;
import com.secretvault.secret.dto.PromotionPreviewRequest;
import com.secretvault.secret.dto.PromotionPreviewResponse;
import com.secretvault.secret.dto.PromotionResultResponse;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SecretPromotionService {

    private static final Logger log = LoggerFactory.getLogger(SecretPromotionService.class);

    private final SecretRepository secretRepository;
    private final SecretVersionRepository secretVersionRepository;
    private final EnvironmentRepository environmentRepository;
    private final EncryptionService encryptionService;
    private final AuditService auditService;
    private final SecretAuthorizationHelper authHelper;

    public SecretPromotionService(
            SecretRepository secretRepository,
            SecretVersionRepository secretVersionRepository,
            EnvironmentRepository environmentRepository,
            EncryptionService encryptionService,
            AuditService auditService,
            SecretAuthorizationHelper authHelper
    ) {
        this.secretRepository = secretRepository;
        this.secretVersionRepository = secretVersionRepository;
        this.environmentRepository = environmentRepository;
        this.encryptionService = encryptionService;
        this.auditService = auditService;
        this.authHelper = authHelper;
    }

    @Transactional(readOnly = true)
    public PromotionPreviewResponse previewPromotion(
            UUID workspaceId,
            UUID projectId,
            UUID sourceEnvironmentId,
            PromotionPreviewRequest request,
            UUID userId
    ) {
        authHelper.verifyHierarchyAndReadAccess(workspaceId, projectId, sourceEnvironmentId, userId);
        authHelper.verifyHierarchyAndReadAccess(workspaceId, projectId, request.destinationEnvironmentId(), userId);

        if (sourceEnvironmentId.equals(request.destinationEnvironmentId())) {
            throw ApiException.badRequest("Source and destination environments must be different");
        }

        Environment sourceEnv = environmentRepository.findByIdAndProjectId(sourceEnvironmentId, projectId)
                .orElseThrow(() -> ApiException.notFound("Source environment not found in this project"));

        Environment destEnv = environmentRepository.findByIdAndProjectId(request.destinationEnvironmentId(), projectId)
                .orElseThrow(() -> ApiException.notFound("Destination environment not found in this project"));

        List<Secret> sourceSecrets = secretRepository.findByEnvironmentIdAndStatusNot(sourceEnvironmentId, SecretStatus.DELETED);
        if (request.secretNames() != null && !request.secretNames().isEmpty()) {
            List<String> targetNames = request.secretNames().stream().map(String::trim).toList();
            sourceSecrets = sourceSecrets.stream()
                    .filter(s -> targetNames.contains(s.getName()))
                    .toList();
        }

        Map<String, Secret> destSecretMap = secretRepository.findByEnvironmentIdAndStatusNot(destEnv.getId(), SecretStatus.DELETED)
                .stream()
                .collect(Collectors.toMap(Secret::getName, s -> s));

        List<PromotionPreviewResponse.PromotionCandidateItem> items = new ArrayList<>();
        int added = 0;
        int modified = 0;
        int unchanged = 0;
        int conflicts = 0;

        for (Secret src : sourceSecrets) {
            Secret dst = destSecretMap.get(src.getName());
            if (dst == null) {
                items.add(new PromotionPreviewResponse.PromotionCandidateItem(
                        src.getName(),
                        "ADDED",
                        src.getCurrentVersionNumber(),
                        null,
                        "New secret will be provisioned in " + destEnv.getName()
                ));
                added++;
            } else if (dst.getStatus() == SecretStatus.DISABLED) {
                items.add(new PromotionPreviewResponse.PromotionCandidateItem(
                        src.getName(),
                        "BLOCKED_DISABLED",
                        src.getCurrentVersionNumber(),
                        dst.getCurrentVersionNumber(),
                        "Destination secret is DISABLED"
                ));
                conflicts++;
            } else {
                // In-memory equality comparison
                boolean equal = areSecretValuesEqual(src, dst);
                if (equal) {
                    items.add(new PromotionPreviewResponse.PromotionCandidateItem(
                            src.getName(),
                            "UNCHANGED",
                            src.getCurrentVersionNumber(),
                            dst.getCurrentVersionNumber(),
                            "Values are identical in source and destination"
                    ));
                    unchanged++;
                } else {
                    items.add(new PromotionPreviewResponse.PromotionCandidateItem(
                            src.getName(),
                            "MODIFIED",
                            src.getCurrentVersionNumber(),
                            dst.getCurrentVersionNumber(),
                            "Value differs; new version will be promoted to " + destEnv.getName()
                    ));
                    modified++;
                }
            }
        }

        return new PromotionPreviewResponse(
                sourceEnv.getId(),
                sourceEnv.getName(),
                destEnv.getId(),
                destEnv.getName(),
                destEnv.isProtected(),
                sourceSecrets.size(),
                added,
                modified,
                unchanged,
                conflicts,
                items
        );
    }

    @Transactional
    public PromotionResultResponse executePromotion(
            UUID workspaceId,
            UUID projectId,
            UUID sourceEnvironmentId,
            ExecutePromotionRequest request,
            UUID userId,
            String requestId,
            String ipAddress
    ) {
        authHelper.verifyHierarchyAndReadAccess(workspaceId, projectId, sourceEnvironmentId, userId);
        SecretAuthorizationHelper.WorkspaceContext destContext = authHelper.verifyHierarchyAndWriteAccess(
                workspaceId, projectId, request.destinationEnvironmentId(), userId
        );

        if (sourceEnvironmentId.equals(request.destinationEnvironmentId())) {
            throw ApiException.badRequest("Source and destination environments must be different");
        }

        Environment destEnv = environmentRepository.findByIdAndProjectId(request.destinationEnvironmentId(), projectId)
                .orElseThrow(() -> ApiException.notFound("Destination environment not found in this project"));

        List<Secret> sourceSecrets = secretRepository.findByEnvironmentIdAndStatusNot(sourceEnvironmentId, SecretStatus.DELETED);
        if (request.secretNames() != null && !request.secretNames().isEmpty()) {
            List<String> targetNames = request.secretNames().stream().map(String::trim).toList();
            sourceSecrets = sourceSecrets.stream()
                    .filter(s -> targetNames.contains(s.getName()))
                    .toList();
        }

        int promotedCount = 0;
        int skippedUnchanged = 0;
        int failedCount = 0;
        List<PromotionResultResponse.PromotedSecretItem> resultItems = new ArrayList<>();

        for (Secret srcSecret : sourceSecrets) {
            try {
                Optional<Secret> destSecretOpt = secretRepository.findByEnvironmentIdAndName(destEnv.getId(), srcSecret.getName());

                // Check conflict on expected destination version
                if (destSecretOpt.isPresent() && request.expectedDestinationVersions() != null) {
                    Integer expectedVer = request.expectedDestinationVersions().get(srcSecret.getName());
                    if (expectedVer != null && !expectedVer.equals(destSecretOpt.get().getCurrentVersionNumber())) {
                        throw ApiException.conflict("Promotion Conflict: Destination secret '" + srcSecret.getName()
                                + "' is at v" + destSecretOpt.get().getCurrentVersionNumber()
                                + ", expected v" + expectedVer);
                    }
                }

                // Retrieve and decrypt source secret version
                SecretVersion srcVer = secretVersionRepository.findBySecretIdAndVersionNumber(srcSecret.getId(), srcSecret.getCurrentVersionNumber())
                        .orElseThrow(() -> ApiException.notFound("Source secret version not found"));

                byte[] sourcePlaintext = decryptVersion(srcSecret.getId(), sourceEnvironmentId, srcVer);

                if (destSecretOpt.isEmpty()) {
                    // 1. Create new Secret in destination
                    Secret newDestSecret = new Secret(
                            destEnv.getId(),
                            srcSecret.getName(),
                            srcSecret.getDescription(),
                            userId
                    );
                    newDestSecret = secretRepository.save(newDestSecret);

                    // 2. Encrypt with destination AAD
                    String destAad = SecretAuthorizationHelper.buildAad(newDestSecret.getId(), destEnv.getId(), 1);
                    EncryptedPayload payload = encryptionService.encrypt(sourcePlaintext, destAad);

                    String reason = StringUtils.hasText(request.reason()) ? request.reason().trim() : "Promoted from " + sourceEnvironmentId;
                    SecretVersion newDestVer = new SecretVersion(
                            newDestSecret.getId(),
                            1,
                            VersionType.PROMOTION,
                            payload.ciphertext(),
                            payload.encryptedDek(),
                            payload.iv(),
                            payload.authTag(),
                            payload.keyReference(),
                            userId,
                            reason,
                            srcVer.getId(),
                            srcSecret.getId(),
                            sourceEnvironmentId,
                            null
                    );
                    secretVersionRepository.save(newDestVer);

                    resultItems.add(new PromotionResultResponse.PromotedSecretItem(
                            srcSecret.getName(),
                            newDestSecret.getId(),
                            1,
                            "CREATED",
                            "SUCCESS"
                    ));
                    promotedCount++;
                } else {
                    Secret dstSecret = secretRepository.findByIdAndEnvironmentIdForUpdate(destSecretOpt.get().getId(), destEnv.getId())
                            .orElseThrow(() -> ApiException.notFound("Destination secret not found"));

                    if (dstSecret.getStatus() == SecretStatus.DELETED) {
                        throw ApiException.badRequest("Cannot promote to a deleted destination secret: " + dstSecret.getName());
                    }

                    // Check if unchanged
                    SecretVersion dstVer = secretVersionRepository.findBySecretIdAndVersionNumber(dstSecret.getId(), dstSecret.getCurrentVersionNumber())
                            .orElse(null);

                    byte[] dstPlaintext = dstVer != null ? decryptVersion(dstSecret.getId(), destEnv.getId(), dstVer) : new byte[0];
                    boolean identical = Arrays.equals(sourcePlaintext, dstPlaintext);
                    Arrays.fill(dstPlaintext, (byte) 0);

                    if (identical) {
                        resultItems.add(new PromotionResultResponse.PromotedSecretItem(
                                srcSecret.getName(),
                                dstSecret.getId(),
                                dstSecret.getCurrentVersionNumber(),
                                "SKIPPED_UNCHANGED",
                                "SUCCESS"
                        ));
                        skippedUnchanged++;
                    } else {
                        int nextVersion = dstSecret.getCurrentVersionNumber() + 1;
                        String destAad = SecretAuthorizationHelper.buildAad(dstSecret.getId(), destEnv.getId(), nextVersion);
                        EncryptedPayload payload = encryptionService.encrypt(sourcePlaintext, destAad);

                        String reason = StringUtils.hasText(request.reason()) ? request.reason().trim() : "Promoted from " + sourceEnvironmentId;
                        SecretVersion newVer = new SecretVersion(
                                dstSecret.getId(),
                                nextVersion,
                                VersionType.PROMOTION,
                                payload.ciphertext(),
                                payload.encryptedDek(),
                                payload.iv(),
                                payload.authTag(),
                                payload.keyReference(),
                                userId,
                                reason,
                                srcVer.getId(),
                                srcSecret.getId(),
                                sourceEnvironmentId,
                                null
                        );
                        secretVersionRepository.save(newVer);

                        dstSecret.setCurrentVersionNumber(nextVersion);
                        secretRepository.save(dstSecret);

                        resultItems.add(new PromotionResultResponse.PromotedSecretItem(
                                srcSecret.getName(),
                                dstSecret.getId(),
                                nextVersion,
                                "UPDATED",
                                "SUCCESS"
                        ));
                        promotedCount++;
                    }
                }

                Arrays.fill(sourcePlaintext, (byte) 0);

            } catch (Exception e) {
                log.error("Failed to promote secret [{}] to environment [{}]: {}", srcSecret.getName(), destEnv.getId(), e.getMessage());
                failedCount++;
                resultItems.add(new PromotionResultResponse.PromotedSecretItem(
                        srcSecret.getName(),
                        null,
                        null,
                        "FAILED",
                        e.getMessage()
                ));
            }
        }

        auditService.recordSecretAudit(
                destContext.workspace().getOrganizationId(),
                workspaceId,
                userId,
                AuditAction.SECRET_PROMOTION_COMPLETED,
                destEnv.getId(),
                requestId,
                ipAddress,
                "SUCCESS"
        );

        log.info("Promotion completed from env [{}] to env [{}]: promoted={}, skipped={}, failed={}",
                sourceEnvironmentId, destEnv.getId(), promotedCount, skippedUnchanged, failedCount);

        return new PromotionResultResponse(
                sourceEnvironmentId,
                destEnv.getId(),
                sourceSecrets.size(),
                promotedCount,
                skippedUnchanged,
                failedCount,
                resultItems,
                Instant.now()
        );
    }

    private boolean areSecretValuesEqual(Secret src, Secret dst) {
        try {
            SecretVersion srcVer = secretVersionRepository.findBySecretIdAndVersionNumber(src.getId(), src.getCurrentVersionNumber()).orElse(null);
            SecretVersion dstVer = secretVersionRepository.findBySecretIdAndVersionNumber(dst.getId(), dst.getCurrentVersionNumber()).orElse(null);
            if (srcVer == null || dstVer == null) return false;

            byte[] srcBytes = decryptVersion(src.getId(), src.getEnvironmentId(), srcVer);
            byte[] dstBytes = decryptVersion(dst.getId(), dst.getEnvironmentId(), dstVer);
            boolean eq = Arrays.equals(srcBytes, dstBytes);
            Arrays.fill(srcBytes, (byte) 0);
            Arrays.fill(dstBytes, (byte) 0);
            return eq;
        } catch (Exception ignored) {
            return false;
        }
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
