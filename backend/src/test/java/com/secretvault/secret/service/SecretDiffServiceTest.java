package com.secretvault.secret.service;

import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.project.entity.Project;
import com.secretvault.secret.dto.SecretDiffResponse;
import com.secretvault.secret.dto.SecretValueDiffResponse;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.entity.VersionType;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
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

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecretDiffServiceTest {

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private SecretVersionRepository secretVersionRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private SecretAuthorizationHelper authHelper;

    @InjectMocks
    private SecretDiffService diffService;

    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID secretId;
    private UUID userId;

    private Secret secret;
    private SecretVersion version1;
    private SecretVersion version2;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        secretId = UUID.randomUUID();
        userId = UUID.randomUUID();

        secret = new Secret(environmentId, "CONFIG_JSON", "Application configuration", userId);
        try {
            var idField = Secret.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(secret, secretId);
        } catch (Exception ignored) {
        }
        secret.setCurrentVersionNumber(2);

        version1 = new SecretVersion(secretId, 1, VersionType.INITIAL, "cipher1".getBytes(), "dek1".getBytes(), new byte[12], new byte[16], "mock-kek", userId, "v1");
        version2 = new SecretVersion(secretId, 2, VersionType.VALUE_UPDATE, "cipher2".getBytes(), "dek2".getBytes(), new byte[12], new byte[16], "mock-kek", userId, "v2");
    }

    @Test
    @DisplayName("Should detect identical versions in metadata comparison")
    void testCompareVersionsIdentical() {
        when(authHelper.verifyHierarchyAndReadAccess(workspaceId, projectId, environmentId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(
                        new Workspace(UUID.randomUUID(), "Org", "org", true),
                        new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER),
                        new Project(workspaceId, "P", "p", "desc", userId),
                        new Environment(projectId, "E", "e", EnvType.DEVELOPMENT, "desc", false, userId)
                ));

        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 1)).thenReturn(Optional.of(version1));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 2)).thenReturn(Optional.of(version2));

        when(encryptionService.decrypt(any(EncryptedPayload.class), eq(secretId + ":" + environmentId + ":1")))
                .thenReturn("PORT=8080".getBytes(StandardCharsets.UTF_8));
        when(encryptionService.decrypt(any(EncryptedPayload.class), eq(secretId + ":" + environmentId + ":2")))
                .thenReturn("PORT=8080".getBytes(StandardCharsets.UTF_8));

        SecretDiffResponse response = diffService.compareVersions(
                workspaceId, projectId, environmentId, secretId, 1, 2, userId
        );

        assertNotNull(response);
        assertTrue(response.isEqual());
        assertEquals("IDENTICAL", response.diffType());
    }

    @Test
    @DisplayName("Should detect modified versions in metadata comparison")
    void testCompareVersionsModified() {
        when(authHelper.verifyHierarchyAndReadAccess(workspaceId, projectId, environmentId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(
                        new Workspace(UUID.randomUUID(), "Org", "org", true),
                        new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER),
                        new Project(workspaceId, "P", "p", "desc", userId),
                        new Environment(projectId, "E", "e", EnvType.DEVELOPMENT, "desc", false, userId)
                ));

        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 1)).thenReturn(Optional.of(version1));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 2)).thenReturn(Optional.of(version2));

        when(encryptionService.decrypt(any(EncryptedPayload.class), eq(secretId + ":" + environmentId + ":1")))
                .thenReturn("PORT=8080".getBytes(StandardCharsets.UTF_8));
        when(encryptionService.decrypt(any(EncryptedPayload.class), eq(secretId + ":" + environmentId + ":2")))
                .thenReturn("PORT=9090".getBytes(StandardCharsets.UTF_8));

        SecretDiffResponse response = diffService.compareVersions(
                workspaceId, projectId, environmentId, secretId, 1, 2, userId
        );

        assertNotNull(response);
        assertFalse(response.isEqual());
        assertEquals("MODIFIED", response.diffType());
    }

    @Test
    @DisplayName("Should compute line-by-line value diff for authorized user")
    void testComputeValueDiffSuccess() {
        when(authHelper.verifyHierarchyAndRevealAccess(workspaceId, projectId, environmentId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(
                        new Workspace(UUID.randomUUID(), "Org", "org", true),
                        new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER),
                        new Project(workspaceId, "P", "p", "desc", userId),
                        new Environment(projectId, "E", "e", EnvType.DEVELOPMENT, "desc", false, userId)
                ));

        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 1)).thenReturn(Optional.of(version1));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 2)).thenReturn(Optional.of(version2));

        String textA = "HOST=localhost\nPORT=8080\nDEBUG=true";
        String textB = "HOST=localhost\nPORT=9090\nDEBUG=true";

        when(encryptionService.decrypt(any(EncryptedPayload.class), eq(secretId + ":" + environmentId + ":1")))
                .thenReturn(textA.getBytes(StandardCharsets.UTF_8));
        when(encryptionService.decrypt(any(EncryptedPayload.class), eq(secretId + ":" + environmentId + ":2")))
                .thenReturn(textB.getBytes(StandardCharsets.UTF_8));

        SecretValueDiffResponse response = diffService.computeValueDiff(
                workspaceId, projectId, environmentId, secretId, 1, 2, userId
        );

        assertNotNull(response);
        assertFalse(response.isEqual());
        assertFalse(response.diffLines().isEmpty());
    }

    @Test
    @DisplayName("Should reject diff exceeding payload size limits with PAYLOAD_TOO_LARGE")
    void testComputeValueDiffExceedsLimitRejected() {
        when(authHelper.verifyHierarchyAndRevealAccess(workspaceId, projectId, environmentId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(
                        new Workspace(UUID.randomUUID(), "Org", "org", true),
                        new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER),
                        new Project(workspaceId, "P", "p", "desc", userId),
                        new Environment(projectId, "E", "e", EnvType.DEVELOPMENT, "desc", false, userId)
                ));

        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 1)).thenReturn(Optional.of(version1));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 2)).thenReturn(Optional.of(version2));

        byte[] hugeBytes = new byte[70000]; // > 64KB
        when(encryptionService.decrypt(any(EncryptedPayload.class), eq(secretId + ":" + environmentId + ":1")))
                .thenReturn(hugeBytes);
        when(encryptionService.decrypt(any(EncryptedPayload.class), eq(secretId + ":" + environmentId + ":2")))
                .thenReturn("PORT=8080".getBytes(StandardCharsets.UTF_8));

        ApiException ex = assertThrows(ApiException.class, () ->
                diffService.computeValueDiff(workspaceId, projectId, environmentId, secretId, 1, 2, userId));
        assertEquals("PAYLOAD_TOO_LARGE", ex.getCode());
    }
}
