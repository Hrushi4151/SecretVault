package com.secretvault.secret.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.project.entity.Project;
import com.secretvault.secret.dto.RollbackSecretRequest;
import com.secretvault.secret.dto.SecretVersionResponse;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecretRollbackServiceTest {

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private SecretVersionRepository secretVersionRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private AuditService auditService;

    @Mock
    private SecretAuthorizationHelper authHelper;

    @InjectMocks
    private SecretRollbackService rollbackService;

    private UUID orgId;
    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID secretId;
    private UUID version1Id;
    private UUID userId;

    private Workspace workspace;
    private Project project;
    private Environment environment;
    private Secret secret;
    private SecretVersion targetVersion1;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        secretId = UUID.randomUUID();
        version1Id = UUID.randomUUID();
        userId = UUID.randomUUID();

        workspace = new Workspace(orgId, "Org", "org", true);
        workspace.setId(workspaceId);

        project = new Project(workspaceId, "P", "p", "desc", userId);
        project.setId(projectId);

        environment = new Environment(projectId, "E", "e", EnvType.DEVELOPMENT, "desc", false, userId);
        environment.setId(environmentId);

        secret = new Secret(environmentId, "API_KEY", "Key", userId);
        try {
            var idField = Secret.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(secret, secretId);
        } catch (Exception ignored) {
        }
        secret.setCurrentVersionNumber(3); // Currently at v3

        targetVersion1 = new SecretVersion(secretId, 1, VersionType.INITIAL, "cipher1".getBytes(), "dek1".getBytes(), new byte[12], new byte[16], "mock-kek", userId, "v1");
        try {
            var idField = SecretVersion.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(targetVersion1, version1Id);
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("Should execute atomic rollback as new version (v4) with fresh encryption keys")
    void testRollbackSuccessCreatesNewVersionWithFreshKeys() {
        when(authHelper.verifyHierarchyAndWriteAccess(workspaceId, projectId, environmentId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(workspace, new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER), project, environment));

        when(secretRepository.findByIdAndEnvironmentIdForUpdate(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 1)).thenReturn(Optional.of(targetVersion1));

        when(encryptionService.decrypt(any(EncryptedPayload.class), eq(secretId + ":" + environmentId + ":1")))
                .thenReturn("original_v1_api_key_secret".getBytes(StandardCharsets.UTF_8));

        EncryptedPayload newPayload = new EncryptedPayload(
                "cipher_v4".getBytes(), "dek_v4".getBytes(), new byte[12], new byte[16], "mock-kek"
        );
        when(encryptionService.encrypt(eq("original_v1_api_key_secret".getBytes(StandardCharsets.UTF_8)), eq(secretId + ":" + environmentId + ":4")))
                .thenReturn(newPayload);

        when(secretVersionRepository.save(any(SecretVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(secretRepository.save(any(Secret.class))).thenAnswer(inv -> inv.getArgument(0));

        RollbackSecretRequest request = new RollbackSecretRequest(1, 3, "Rollback to stable v1");
        SecretVersionResponse response = rollbackService.rollbackSecret(
                workspaceId, projectId, environmentId, secretId, request, userId, "req-rb", "127.0.0.1"
        );

        assertNotNull(response);
        assertEquals(4, response.versionNumber()); // New version is 4
        assertEquals(VersionType.ROLLBACK, response.versionType());
        assertEquals(version1Id, response.sourceVersionId());
        assertTrue(response.isCurrent());

        assertEquals(4, secret.getCurrentVersionNumber()); // Secret pointer advanced to 4

        verify(auditService).recordSecretAudit(
                eq(orgId), eq(workspaceId), eq(userId), eq(AuditAction.SECRET_ROLLBACK_COMPLETED),
                eq(secretId), eq("req-rb"), eq("127.0.0.1"), eq("SUCCESS")
        );
    }

    @Test
    @DisplayName("Should reject rollback if secret is already at the target version")
    void testRollbackToCurrentVersionRejected() {
        when(authHelper.verifyHierarchyAndWriteAccess(workspaceId, projectId, environmentId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(workspace, new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER), project, environment));

        secret.setCurrentVersionNumber(1);
        when(secretRepository.findByIdAndEnvironmentIdForUpdate(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 1)).thenReturn(Optional.of(targetVersion1));

        RollbackSecretRequest request = new RollbackSecretRequest(1, 1, "Rollback to current");
        ApiException ex = assertThrows(ApiException.class, () ->
                rollbackService.rollbackSecret(workspaceId, projectId, environmentId, secretId, request, userId, "req-rb", "127.0.0.1"));
        assertEquals("BAD_REQUEST", ex.getCode());
        assertTrue(ex.getMessage().contains("already at version"));
    }

    @Test
    @DisplayName("Should reject rollback with 409 Conflict if expectedCurrentVersion does not match actual state")
    void testRollbackExpectedVersionConflictRejected() {
        when(authHelper.verifyHierarchyAndWriteAccess(workspaceId, projectId, environmentId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(workspace, new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER), project, environment));

        secret.setCurrentVersionNumber(5); // Actual is 5
        when(secretRepository.findByIdAndEnvironmentIdForUpdate(secretId, environmentId)).thenReturn(Optional.of(secret));

        RollbackSecretRequest request = new RollbackSecretRequest(1, 3, "Stale rollback"); // Expected 3
        ApiException ex = assertThrows(ApiException.class, () ->
                rollbackService.rollbackSecret(workspaceId, projectId, environmentId, secretId, request, userId, "req-rb", "127.0.0.1"));
        assertEquals("RESOURCE_CONFLICT", ex.getCode());
    }
}
