package com.secretvault.secret.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.environment.access.entity.EnvironmentAccess;
import com.secretvault.environment.access.entity.PermissionLevel;
import com.secretvault.environment.access.repository.EnvironmentAccessRepository;
import com.secretvault.environment.access.service.EnvironmentAccessService;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.access.entity.ProjectAccess;
import com.secretvault.project.access.repository.ProjectAccessRepository;
import com.secretvault.project.access.service.ProjectAccessService;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.secret.dto.CreateSecretRequest;
import com.secretvault.secret.dto.SecretMetadataResponse;
import com.secretvault.secret.dto.SecretRevealResponse;
import com.secretvault.secret.dto.SecretVersionResponse;
import com.secretvault.secret.dto.UpdateSecretRequest;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Core Secret Management Engine providing multi-tenant hierarchical authorization,
 * envelope encryption lifecycle, immutable versioning, in-memory explicit reveal,
 * soft deletion, and non-repudiable audit logging.
 */
@Service
public class SecretService {

    private static final Logger log = LoggerFactory.getLogger(SecretService.class);

    private final SecretRepository secretRepository;
    private final SecretVersionRepository secretVersionRepository;
    private final EncryptionService encryptionService;
    private final AuditService auditService;
    private final EnvironmentRepository environmentRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final ProjectAccessRepository projectAccessRepository;
    private final EnvironmentAccessRepository environmentAccessRepository;

    public SecretService(
            SecretRepository secretRepository,
            SecretVersionRepository secretVersionRepository,
            EncryptionService encryptionService,
            AuditService auditService,
            EnvironmentRepository environmentRepository,
            ProjectRepository projectRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            ProjectAccessRepository projectAccessRepository,
            EnvironmentAccessRepository environmentAccessRepository
    ) {
        this.secretRepository = secretRepository;
        this.secretVersionRepository = secretVersionRepository;
        this.encryptionService = encryptionService;
        this.auditService = auditService;
        this.environmentRepository = environmentRepository;
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.projectAccessRepository = projectAccessRepository;
        this.environmentAccessRepository = environmentAccessRepository;
    }

    /**
     * Lists secret metadata for an environment with optional status and name search filters.
     * Never returns plaintext secrets.
     */
    @Transactional(readOnly = true)
    public List<SecretMetadataResponse> getSecrets(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            String search,
            SecretStatus statusFilter,
            UUID userId
    ) {
        verifyHierarchyAndReadAccess(workspaceId, projectId, environmentId, userId);

        List<Secret> secrets;
        if (statusFilter != null) {
            secrets = secretRepository.findByEnvironmentIdAndStatus(environmentId, statusFilter);
        } else {
            secrets = secretRepository.findByEnvironmentIdAndStatusNot(environmentId, SecretStatus.DELETED);
        }

        if (StringUtils.hasText(search)) {
            String query = search.trim().toLowerCase(Locale.ROOT);
            secrets = secrets.stream()
                    .filter(s -> s.getName().toLowerCase(Locale.ROOT).contains(query))
                    .toList();
        }

        return secrets.stream()
                .map(SecretMetadataResponse::fromEntity)
                .toList();
    }

    /**
     * Retrieves secret metadata and container info.
     * Never returns plaintext secrets.
     */
    @Transactional(readOnly = true)
    public SecretMetadataResponse getSecretById(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            UUID userId
    ) {
        verifyHierarchyAndReadAccess(workspaceId, projectId, environmentId, userId);

        Secret secret = secretRepository.findByIdAndEnvironmentId(secretId, environmentId)
                .orElseThrow(() -> ApiException.notFound("Secret not found in this environment"));

        return SecretMetadataResponse.fromEntity(secret);
    }

    /**
     * Retrieves the immutable version history metadata for a secret.
     */
    @Transactional(readOnly = true)
    public List<SecretVersionResponse> getSecretVersions(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            UUID userId
    ) {
        verifyHierarchyAndReadAccess(workspaceId, projectId, environmentId, userId);

        if (!secretRepository.existsById(secretId)) {
            throw ApiException.notFound("Secret not found");
        }

        return secretVersionRepository.findBySecretIdOrderByVersionNumberDesc(secretId)
                .stream()
                .map(SecretVersionResponse::fromEntity)
                .toList();
    }

    /**
     * Creates a new Secret container and persists its initial encrypted version (v1) atomically.
     */
    @Transactional
    public SecretMetadataResponse createSecret(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            CreateSecretRequest request,
            UUID userId,
            String requestId,
            String ipAddress
    ) {
        WorkspaceContext context = verifyHierarchyAndWriteAccess(workspaceId, projectId, environmentId, userId);

        String normalizedName = request.name().trim();
        if (secretRepository.existsByEnvironmentIdAndName(environmentId, normalizedName)) {
            throw ApiException.conflict("A secret with name '" + normalizedName + "' already exists in this environment");
        }

        // 1. Create and persist Secret container
        Secret secret = new Secret(
                environmentId,
                normalizedName,
                request.description() != null ? request.description().trim() : null,
                userId
        );
        secret = secretRepository.save(secret);

        // 2. Encrypt value using AES-256-GCM Envelope Encryption
        String aad = buildAad(secret.getId(), environmentId, 1);
        EncryptedPayload payload = encryptionService.encrypt(
                request.value().getBytes(StandardCharsets.UTF_8),
                aad
        );

        // 3. Persist initial immutable version 1
        SecretVersion version = new SecretVersion(
                secret.getId(),
                1,
                payload.ciphertext(),
                payload.encryptedDek(),
                payload.iv(),
                payload.authTag(),
                payload.keyReference(),
                userId,
                "Initial secret version created"
        );
        secretVersionRepository.save(version);

        // 4. Emit Audit Log
        auditService.recordSecretAudit(
                context.workspace().getOrganizationId(),
                workspaceId,
                userId,
                AuditAction.SECRET_CREATED,
                secret.getId(),
                requestId,
                ipAddress,
                "SUCCESS"
        );

        log.info("Created secret [{}] (version 1) in environment [{}] by user [{}]",
                secret.getId(), environmentId, userId);

        return SecretMetadataResponse.fromEntity(secret);
    }

    /**
     * Updates secret metadata or creates a new immutable version (vN+1) if a new value is supplied.
     */
    @Transactional
    public SecretMetadataResponse updateSecret(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            UpdateSecretRequest request,
            UUID userId,
            String requestId,
            String ipAddress
    ) {
        WorkspaceContext context = verifyHierarchyAndWriteAccess(workspaceId, projectId, environmentId, userId);

        Secret secret = secretRepository.findByIdAndEnvironmentId(secretId, environmentId)
                .orElseThrow(() -> ApiException.notFound("Secret not found in this environment"));

        if (secret.getStatus() == SecretStatus.DELETED) {
            throw ApiException.badRequest("Cannot update a deleted secret");
        }

        boolean valueUpdated = false;
        boolean metadataUpdated = false;

        // 1. Version increment if new value provided
        if (StringUtils.hasText(request.value())) {
            int nextVersionNumber = secret.getCurrentVersionNumber() + 1;
            String aad = buildAad(secret.getId(), environmentId, nextVersionNumber);

            EncryptedPayload payload = encryptionService.encrypt(
                    request.value().getBytes(StandardCharsets.UTF_8),
                    aad
            );

            SecretVersion nextVersion = new SecretVersion(
                    secret.getId(),
                    nextVersionNumber,
                    payload.ciphertext(),
                    payload.encryptedDek(),
                    payload.iv(),
                    payload.authTag(),
                    payload.keyReference(),
                    userId,
                    StringUtils.hasText(request.reason()) ? request.reason().trim() : "Updated secret value"
            );
            secretVersionRepository.save(nextVersion);

            secret.setCurrentVersionNumber(nextVersionNumber);
            valueUpdated = true;
        }

        // 2. Metadata updates
        if (request.description() != null) {
            secret.setDescription(request.description().trim());
            metadataUpdated = true;
        }

        if (request.status() != null && request.status() != secret.getStatus()) {
            secret.setStatus(request.status());
            metadataUpdated = true;
        }

        secret = secretRepository.save(secret);

        // 3. Emit appropriate audit events
        if (valueUpdated) {
            auditService.recordSecretAudit(
                    context.workspace().getOrganizationId(),
                    workspaceId,
                    userId,
                    AuditAction.SECRET_VALUE_UPDATED,
                    secret.getId(),
                    requestId,
                    ipAddress,
                    "SUCCESS"
            );
        } else if (metadataUpdated) {
            AuditAction action = switch (secret.getStatus()) {
                case DISABLED -> AuditAction.SECRET_DISABLED;
                case ACTIVE -> AuditAction.SECRET_ENABLED;
                default -> AuditAction.SECRET_METADATA_UPDATED;
            };

            auditService.recordSecretAudit(
                    context.workspace().getOrganizationId(),
                    workspaceId,
                    userId,
                    action,
                    secret.getId(),
                    requestId,
                    ipAddress,
                    "SUCCESS"
            );
        }

        log.info("Updated secret [{}] (currentVersion: {}) in environment [{}] by user [{}]",
                secret.getId(), secret.getCurrentVersionNumber(), environmentId, userId);

        return SecretMetadataResponse.fromEntity(secret);
    }

    /**
     * Explicit Reveal endpoint decrypting secret in memory on-demand.
     * Enforces strict RBAC and security audit trail. Never caches plaintext.
     */
    @Transactional
    public SecretRevealResponse revealSecret(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            Integer versionNumber,
            UUID userId,
            String requestId,
            String ipAddress
    ) {
        WorkspaceContext context = verifyHierarchyAndRevealAccess(workspaceId, projectId, environmentId, userId);

        Secret secret = secretRepository.findByIdAndEnvironmentId(secretId, environmentId)
                .orElseThrow(() -> ApiException.notFound("Secret not found in this environment"));

        if (secret.getStatus() == SecretStatus.DELETED) {
            throw ApiException.badRequest("Cannot reveal a deleted secret");
        }

        int targetVersion = (versionNumber != null && versionNumber > 0)
                ? versionNumber
                : secret.getCurrentVersionNumber();

        SecretVersion version = secretVersionRepository.findBySecretIdAndVersionNumber(secretId, targetVersion)
                .orElseThrow(() -> ApiException.notFound("Secret version " + targetVersion + " not found"));

        // 1. Reconstruct payload and decrypt in memory
        EncryptedPayload payload = new EncryptedPayload(
                version.getCiphertext(),
                version.getEncryptedDek(),
                version.getIv(),
                version.getAuthTag(),
                version.getKeyReference()
        );

        String aad = buildAad(secret.getId(), environmentId, version.getVersionNumber());
        byte[] plaintextBytes = encryptionService.decrypt(payload, aad);
        String plaintext = new String(plaintextBytes, StandardCharsets.UTF_8);

        // Memory zeroization of temporary byte array
        Arrays.fill(plaintextBytes, (byte) 0);

        // 2. Emit non-repudiable audit event
        auditService.recordSecretAudit(
                context.workspace().getOrganizationId(),
                workspaceId,
                userId,
                AuditAction.SECRET_REVEALED,
                secret.getId(),
                requestId,
                ipAddress,
                "SUCCESS"
        );

        log.info("Secret [{}] (version {}) revealed by user [{}] in workspace [{}]",
                secret.getId(), version.getVersionNumber(), userId, workspaceId);

        return new SecretRevealResponse(
                secret.getId(),
                environmentId,
                secret.getName(),
                version.getVersionNumber(),
                plaintext,
                Instant.now()
        );
    }

    /**
     * Soft-deletes a secret by setting status to DELETED.
     */
    @Transactional
    public void deleteSecret(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            UUID userId,
            String requestId,
            String ipAddress
    ) {
        WorkspaceContext context = verifyHierarchyAndWriteAccess(workspaceId, projectId, environmentId, userId);

        Secret secret = secretRepository.findByIdAndEnvironmentId(secretId, environmentId)
                .orElseThrow(() -> ApiException.notFound("Secret not found in this environment"));

        secret.setStatus(SecretStatus.DELETED);
        secretRepository.save(secret);

        auditService.recordSecretAudit(
                context.workspace().getOrganizationId(),
                workspaceId,
                userId,
                AuditAction.SECRET_DELETED,
                secret.getId(),
                requestId,
                ipAddress,
                "SUCCESS"
        );

        log.info("Soft deleted secret [{}] in environment [{}] by user [{}]",
                secret.getId(), environmentId, userId);
    }

    // =========================================================================
    // Security & Authorization Helpers
    // =========================================================================

    private String buildAad(UUID secretId, UUID environmentId, int versionNumber) {
        return secretId.toString() + ":" + environmentId.toString() + ":" + versionNumber;
    }

    private record WorkspaceContext(Workspace workspace, WorkspaceMembership membership) {
    }

    private WorkspaceContext verifyHierarchy(UUID workspaceId, UUID projectId, UUID environmentId, UUID userId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> ApiException.notFound("Workspace not found"));

        WorkspaceMembership membership = membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this workspace"));

        Project project = projectRepository.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Project not found in this workspace"));

        Environment environment = environmentRepository.findByIdAndProjectId(environmentId, projectId)
                .orElseThrow(() -> ApiException.notFound("Environment not found in this project"));

        return new WorkspaceContext(workspace, membership);
    }

    private WorkspaceContext verifyHierarchyAndReadAccess(UUID workspaceId, UUID projectId, UUID environmentId, UUID userId) {
        return verifyHierarchy(workspaceId, projectId, environmentId, userId);
    }

    private WorkspaceContext verifyHierarchyAndWriteAccess(UUID workspaceId, UUID projectId, UUID environmentId, UUID userId) {
        WorkspaceContext context = verifyHierarchy(workspaceId, projectId, environmentId, userId);
        WorkspaceRole wsRole = context.membership().getRole();

        // 1. VIEWER cannot write
        if (wsRole == WorkspaceRole.VIEWER) {
            throw ApiException.forbidden("VIEWER role cannot modify secrets");
        }

        // 2. Intersect with Scoped Project Access
        Optional<ProjectAccess> projAccess = projectAccessRepository.findByProjectIdAndUserId(projectId, userId);
        WorkspaceRole effProjectRole = ProjectAccessService.computeEffectiveRole(
                wsRole,
                projAccess.map(ProjectAccess::getRole).orElse(wsRole)
        );

        // 3. Intersect with Scoped Environment Access
        Optional<EnvironmentAccess> envAccess = environmentAccessRepository.findByEnvironmentIdAndUserId(environmentId, userId);
        PermissionLevel effEnvPerm = EnvironmentAccessService.computeEffectivePermission(
                effProjectRole,
                envAccess.map(EnvironmentAccess::getPermissionLevel).orElse(null)
        );

        if (effEnvPerm == PermissionLevel.READ) {
            throw ApiException.forbidden("Insufficient permissions: effective permission on this environment is READ only");
        }

        return context;
    }

    private WorkspaceContext verifyHierarchyAndRevealAccess(UUID workspaceId, UUID projectId, UUID environmentId, UUID userId) {
        WorkspaceContext context = verifyHierarchy(workspaceId, projectId, environmentId, userId);
        WorkspaceRole wsRole = context.membership().getRole();

        // VIEWER role is strictly forbidden from revealing secrets
        if (wsRole == WorkspaceRole.VIEWER) {
            throw ApiException.forbidden("VIEWER role is not authorized to reveal secret values");
        }

        return context;
    }
}
