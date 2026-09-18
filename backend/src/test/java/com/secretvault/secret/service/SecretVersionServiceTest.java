package com.secretvault.secret.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.project.entity.Project;
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
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecretVersionServiceTest {

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private SecretVersionRepository secretVersionRepository;

    @Mock
    private SecretVersionTagRepository tagRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private AuditService auditService;

    @Mock
    private SecretAuthorizationHelper authHelper;

    @InjectMocks
    private SecretVersionService versionService;

    private UUID orgId;
    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID secretId;
    private UUID versionId;
    private UUID userId;

    private Workspace workspace;
    private Project project;
    private Environment environment;
    private Secret secret;
    private SecretVersion secretVersion;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        secretId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        userId = UUID.randomUUID();

        workspace = new Workspace(orgId, "Production Org", "prod-org", true);
        workspace.setId(workspaceId);

        project = new Project(workspaceId, "Payment Gateway", "payment-gw", "Payments", userId);
        project.setId(projectId);

        environment = new Environment(projectId, "Production", "production", EnvType.PRODUCTION, "Prod Tier", true, userId);
        environment.setId(environmentId);

        secret = new Secret(environmentId, "DATABASE_URL", "Primary DB URL", userId);
        try {
            var idField = Secret.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(secret, secretId);
        } catch (Exception ignored) {
        }
        secret.setCurrentVersionNumber(1);

        secretVersion = new SecretVersion(
                secretId,
                1,
                VersionType.INITIAL,
                "ciphertext".getBytes(StandardCharsets.UTF_8),
                "encryptedDek".getBytes(StandardCharsets.UTF_8),
                new byte[12],
                new byte[16],
                "mock-kek",
                userId,
                "Initial creation"
        );
        try {
            var idField = SecretVersion.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(secretVersion, versionId);
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("Should return paginated version history with tag mapping")
    void testGetVersionsSuccess() {
        when(authHelper.verifyHierarchyAndReadAccess(workspaceId, projectId, environmentId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(workspace, new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER), project, environment));

        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));

        Pageable pageable = PageRequest.of(0, 10);
        Page<SecretVersion> pagedResult = new PageImpl<>(List.of(secretVersion), pageable, 1);
        when(secretVersionRepository.findBySecretId(secretId, pageable)).thenReturn(pagedResult);

        SecretVersionTag mockTag = new SecretVersionTag(versionId, "production", userId);
        when(tagRepository.findBySecretVersionIdIn(List.of(versionId))).thenReturn(List.of(mockTag));

        Page<SecretVersionResponse> result = versionService.getVersions(
                workspaceId, projectId, environmentId, secretId, null, pageable, userId
        );

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        SecretVersionResponse vResp = result.getContent().getFirst();
        assertEquals(1, vResp.versionNumber());
        assertEquals(VersionType.INITIAL, vResp.versionType());
        assertTrue(vResp.isCurrent());
        assertEquals(List.of("production"), vResp.tags());
    }

    @Test
    @DisplayName("Should fetch single version metadata by version number")
    void testGetVersionSuccess() {
        when(authHelper.verifyHierarchyAndReadAccess(workspaceId, projectId, environmentId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(workspace, new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER), project, environment));

        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 1)).thenReturn(Optional.of(secretVersion));
        when(tagRepository.findBySecretVersionId(versionId)).thenReturn(Collections.emptyList());

        SecretVersionResponse response = versionService.getVersion(
                workspaceId, projectId, environmentId, secretId, 1, userId
        );

        assertNotNull(response);
        assertEquals(1, response.versionNumber());
        assertTrue(response.isCurrent());
    }

    @Test
    @DisplayName("Should reveal historical secret version and emit audit event")
    void testRevealHistoricalVersionSuccess() {
        when(authHelper.verifyHierarchyAndRevealAccess(workspaceId, projectId, environmentId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(workspace, new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER), project, environment));

        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 1)).thenReturn(Optional.of(secretVersion));
        when(encryptionService.decrypt(any(EncryptedPayload.class), eq(secretId + ":" + environmentId + ":1")))
                .thenReturn("postgres://admin:secret@db:5432/main".getBytes(StandardCharsets.UTF_8));

        SecretRevealResponse response = versionService.revealHistoricalVersion(
                workspaceId, projectId, environmentId, secretId, 1, userId, "req-hist", "127.0.0.1"
        );

        assertNotNull(response);
        assertEquals("DATABASE_URL", response.name());
        assertEquals("postgres://admin:secret@db:5432/main", response.value());
        assertEquals(1, response.versionNumber());

        verify(auditService).recordSecretAudit(
                eq(orgId), eq(workspaceId), eq(userId), eq(AuditAction.SECRET_HISTORICAL_REVEALED),
                eq(secretId), eq("req-hist"), eq("127.0.0.1"), eq("SUCCESS")
        );
    }

    @Test
    @DisplayName("Should reject historical reveal on a DELETED secret")
    void testRevealHistoricalVersionDeletedRejected() {
        when(authHelper.verifyHierarchyAndRevealAccess(workspaceId, projectId, environmentId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(workspace, new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER), project, environment));

        secret.setStatus(SecretStatus.DELETED);
        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));

        ApiException ex = assertThrows(ApiException.class, () ->
                versionService.revealHistoricalVersion(workspaceId, projectId, environmentId, secretId, 1, userId, "req-del", "127.0.0.1"));
        assertEquals("BAD_REQUEST", ex.getCode());
        assertTrue(ex.getMessage().contains("deleted"));
    }

    @Test
    @DisplayName("Should reject historical reveal on a DISABLED secret")
    void testRevealHistoricalVersionDisabledRejected() {
        when(authHelper.verifyHierarchyAndRevealAccess(workspaceId, projectId, environmentId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(workspace, new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER), project, environment));

        secret.setStatus(SecretStatus.DISABLED);
        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));

        ApiException ex = assertThrows(ApiException.class, () ->
                versionService.revealHistoricalVersion(workspaceId, projectId, environmentId, secretId, 1, userId, "req-dis", "127.0.0.1"));
        assertEquals("BAD_REQUEST", ex.getCode());
        assertTrue(ex.getMessage().contains("disabled"));
    }

    @Test
    @DisplayName("Should add tag to a secret version and emit audit event")
    void testAddTagSuccess() {
        when(authHelper.verifyHierarchyAndWriteAccess(workspaceId, projectId, environmentId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(workspace, new WorkspaceMembership(workspaceId, userId, WorkspaceRole.ADMIN), project, environment));

        when(secretRepository.existsById(secretId)).thenReturn(true);
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 1)).thenReturn(Optional.of(secretVersion));
        when(tagRepository.existsBySecretVersionIdAndName(versionId, "stable")).thenReturn(false);

        SecretVersionTag savedTag = new SecretVersionTag(versionId, "stable", userId);
        when(tagRepository.save(any(SecretVersionTag.class))).thenReturn(savedTag);

        SecretVersionTagResponse response = versionService.addTag(
                workspaceId, projectId, environmentId, secretId, 1, "stable", userId, "req-tag", "127.0.0.1"
        );

        assertNotNull(response);
        assertEquals("stable", response.name());
        verify(auditService).recordSecretAudit(
                eq(orgId), eq(workspaceId), eq(userId), eq(AuditAction.SECRET_VERSION_TAGGED),
                eq(secretId), eq("req-tag"), eq("127.0.0.1"), eq("SUCCESS")
        );
    }

    @Test
    @DisplayName("Should reject duplicate tag on same secret version")
    void testAddDuplicateTagRejected() {
        when(authHelper.verifyHierarchyAndWriteAccess(workspaceId, projectId, environmentId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(workspace, new WorkspaceMembership(workspaceId, userId, WorkspaceRole.ADMIN), project, environment));

        when(secretRepository.existsById(secretId)).thenReturn(true);
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 1)).thenReturn(Optional.of(secretVersion));
        when(tagRepository.existsBySecretVersionIdAndName(versionId, "stable")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () ->
                versionService.addTag(workspaceId, projectId, environmentId, secretId, 1, "stable", userId, "req-dup", "127.0.0.1"));
        assertEquals("RESOURCE_CONFLICT", ex.getCode());
    }

    @Test
    @DisplayName("Should remove tag and emit audit event")
    void testRemoveTagSuccess() {
        when(authHelper.verifyHierarchyAndWriteAccess(workspaceId, projectId, environmentId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(workspace, new WorkspaceMembership(workspaceId, userId, WorkspaceRole.ADMIN), project, environment));

        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 1)).thenReturn(Optional.of(secretVersion));

        SecretVersionTag tag = new SecretVersionTag(versionId, "stable", userId);
        when(tagRepository.findBySecretVersionIdAndName(versionId, "stable")).thenReturn(Optional.of(tag));

        versionService.removeTag(
                workspaceId, projectId, environmentId, secretId, 1, "stable", userId, "req-untag", "127.0.0.1"
        );

        verify(tagRepository).delete(tag);
        verify(auditService).recordSecretAudit(
                eq(orgId), eq(workspaceId), eq(userId), eq(AuditAction.SECRET_VERSION_UNTAGGED),
                eq(secretId), eq("req-untag"), eq("127.0.0.1"), eq("SUCCESS")
        );
    }
}
