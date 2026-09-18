package com.secretvault.secret.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.secret.dto.SecretRevealResponse;
import com.secretvault.secret.dto.SecretVersionResponse;
import com.secretvault.secret.dto.SecretVersionTagResponse;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.entity.SecretVersionTag;
import com.secretvault.secret.entity.VersionType;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import com.secretvault.secret.repository.SecretVersionTagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SecretVersionService {

    private static final Logger log = LoggerFactory.getLogger(SecretVersionService.class);

    private final SecretRepository secretRepository;
    private final SecretVersionRepository secretVersionRepository;
    private final SecretVersionTagRepository tagRepository;
    private final EncryptionService encryptionService;
    private final AuditService auditService;
    private final SecretAuthorizationHelper authHelper;

    public SecretVersionService(
            SecretRepository secretRepository,
            SecretVersionRepository secretVersionRepository,
            SecretVersionTagRepository tagRepository,
            EncryptionService encryptionService,
            AuditService auditService,
            SecretAuthorizationHelper authHelper
    ) {
        this.secretRepository = secretRepository;
        this.secretVersionRepository = secretVersionRepository;
        this.tagRepository = tagRepository;
        this.encryptionService = encryptionService;
        this.auditService = auditService;
        this.authHelper = authHelper;
    }

    @Transactional(readOnly = true)
    public Page<SecretVersionResponse> getVersions(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            VersionType versionTypeFilter,
            Pageable pageable,
            UUID userId
    ) {
        authHelper.verifyHierarchyAndReadAccess(workspaceId, projectId, environmentId, userId);

        Secret secret = secretRepository.findByIdAndEnvironmentId(secretId, environmentId)
                .orElseThrow(() -> ApiException.notFound("Secret not found in this environment"));

        Page<SecretVersion> versionPage;
        if (versionTypeFilter != null) {
            versionPage = secretVersionRepository.findBySecretIdAndVersionType(secretId, versionTypeFilter, pageable);
        } else {
            versionPage = secretVersionRepository.findBySecretId(secretId, pageable);
        }

        List<UUID> versionIds = versionPage.getContent().stream().map(SecretVersion::getId).toList();
        Map<UUID, List<String>> tagMap = tagRepository.findBySecretVersionIdIn(versionIds)
                .stream()
                .collect(Collectors.groupingBy(
                        SecretVersionTag::getSecretVersionId,
                        Collectors.mapping(SecretVersionTag::getName, Collectors.toList())
                ));

        return versionPage.map(v -> {
            List<String> tags = tagMap.getOrDefault(v.getId(), Collections.emptyList());
            boolean isCurrent = v.getVersionNumber().equals(secret.getCurrentVersionNumber());
            return SecretVersionResponse.fromEntity(v, tags, isCurrent);
        });
    }

    @Transactional(readOnly = true)
    public SecretVersionResponse getVersion(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            Integer versionNumber,
            UUID userId
    ) {
        authHelper.verifyHierarchyAndReadAccess(workspaceId, projectId, environmentId, userId);

        Secret secret = secretRepository.findByIdAndEnvironmentId(secretId, environmentId)
                .orElseThrow(() -> ApiException.notFound("Secret not found in this environment"));

        SecretVersion version = secretVersionRepository.findBySecretIdAndVersionNumber(secretId, versionNumber)
                .orElseThrow(() -> ApiException.notFound("Secret version " + versionNumber + " not found"));

        List<String> tags = tagRepository.findBySecretVersionId(version.getId())
                .stream()
                .map(SecretVersionTag::getName)
                .toList();

        boolean isCurrent = version.getVersionNumber().equals(secret.getCurrentVersionNumber());
        return SecretVersionResponse.fromEntity(version, tags, isCurrent);
    }

    @Transactional
    public SecretRevealResponse revealHistoricalVersion(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            Integer versionNumber,
            UUID userId,
            String requestId,
            String ipAddress
    ) {
        SecretAuthorizationHelper.WorkspaceContext context = authHelper.verifyHierarchyAndRevealAccess(
                workspaceId, projectId, environmentId, userId
        );

        Secret secret = secretRepository.findByIdAndEnvironmentId(secretId, environmentId)
                .orElseThrow(() -> ApiException.notFound("Secret not found in this environment"));

        if (secret.getStatus() == SecretStatus.DELETED) {
            throw ApiException.badRequest("Cannot reveal a version of a deleted secret");
        }

        if (secret.getStatus() == SecretStatus.DISABLED) {
            throw ApiException.badRequest("Cannot reveal a version of a disabled secret. Please enable the secret first.");
        }

        SecretVersion version = secretVersionRepository.findBySecretIdAndVersionNumber(secretId, versionNumber)
                .orElseThrow(() -> ApiException.notFound("Secret version " + versionNumber + " not found"));

        EncryptedPayload payload = new EncryptedPayload(
                version.getCiphertext(),
                version.getEncryptedDek(),
                version.getIv(),
                version.getAuthTag(),
                version.getKeyReference()
        );

        String aad = SecretAuthorizationHelper.buildAad(secret.getId(), environmentId, version.getVersionNumber());
        byte[] plaintextBytes = encryptionService.decrypt(payload, aad);
        String plaintext = new String(plaintextBytes, StandardCharsets.UTF_8);
        Arrays.fill(plaintextBytes, (byte) 0);

        auditService.recordSecretAudit(
                context.workspace().getOrganizationId(),
                workspaceId,
                userId,
                AuditAction.SECRET_HISTORICAL_REVEALED,
                secret.getId(),
                requestId,
                ipAddress,
                "SUCCESS"
        );

        log.info("Historical version [{}] of secret [{}] revealed by user [{}] in workspace [{}]",
                version.getVersionNumber(), secret.getId(), userId, workspaceId);

        return new SecretRevealResponse(
                secret.getId(),
                environmentId,
                secret.getName(),
                version.getVersionNumber(),
                plaintext,
                Instant.now()
        );
    }

    @Transactional
    public SecretVersionTagResponse addTag(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            Integer versionNumber,
            String tagName,
            UUID userId,
            String requestId,
            String ipAddress
    ) {
        SecretAuthorizationHelper.WorkspaceContext context = authHelper.verifyHierarchyAndWriteAccess(
                workspaceId, projectId, environmentId, userId
        );

        if (!secretRepository.existsById(secretId)) {
            throw ApiException.notFound("Secret not found");
        }

        SecretVersion version = secretVersionRepository.findBySecretIdAndVersionNumber(secretId, versionNumber)
                .orElseThrow(() -> ApiException.notFound("Secret version " + versionNumber + " not found"));

        String cleanTag = tagName.trim().toLowerCase();
        if (tagRepository.existsBySecretVersionIdAndName(version.getId(), cleanTag)) {
            throw ApiException.conflict("Tag '" + cleanTag + "' already exists on this version");
        }

        SecretVersionTag tag = new SecretVersionTag(version.getId(), cleanTag, userId);
        tag = tagRepository.save(tag);

        auditService.recordSecretAudit(
                context.workspace().getOrganizationId(),
                workspaceId,
                userId,
                AuditAction.SECRET_VERSION_TAGGED,
                secretId,
                requestId,
                ipAddress,
                "SUCCESS"
        );

        return SecretVersionTagResponse.fromEntity(tag);
    }

    @Transactional(readOnly = true)
    public List<SecretVersionTagResponse> getTags(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            Integer versionNumber,
            UUID userId
    ) {
        authHelper.verifyHierarchyAndReadAccess(workspaceId, projectId, environmentId, userId);

        if (!secretRepository.existsById(secretId)) {
            throw ApiException.notFound("Secret not found");
        }

        SecretVersion version = secretVersionRepository.findBySecretIdAndVersionNumber(secretId, versionNumber)
                .orElseThrow(() -> ApiException.notFound("Secret version " + versionNumber + " not found"));

        return tagRepository.findBySecretVersionId(version.getId())
                .stream()
                .map(SecretVersionTagResponse::fromEntity)
                .toList();
    }

    @Transactional
    public void removeTag(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            Integer versionNumber,
            String tagName,
            UUID userId,
            String requestId,
            String ipAddress
    ) {
        SecretAuthorizationHelper.WorkspaceContext context = authHelper.verifyHierarchyAndWriteAccess(
                workspaceId, projectId, environmentId, userId
        );

        SecretVersion version = secretVersionRepository.findBySecretIdAndVersionNumber(secretId, versionNumber)
                .orElseThrow(() -> ApiException.notFound("Secret version " + versionNumber + " not found"));

        String cleanTag = tagName.trim().toLowerCase();
        SecretVersionTag tag = tagRepository.findBySecretVersionIdAndName(version.getId(), cleanTag)
                .orElseThrow(() -> ApiException.notFound("Tag '" + cleanTag + "' not found on version " + versionNumber));

        tagRepository.delete(tag);

        auditService.recordSecretAudit(
                context.workspace().getOrganizationId(),
                workspaceId,
                userId,
                AuditAction.SECRET_VERSION_UNTAGGED,
                secretId,
                requestId,
                ipAddress,
                "SUCCESS"
        );
    }
}
