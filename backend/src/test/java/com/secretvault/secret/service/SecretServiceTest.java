package com.secretvault.secret.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.environment.access.repository.EnvironmentAccessRepository;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.access.repository.ProjectAccessRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecretServiceTest {

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private SecretVersionRepository secretVersionRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private AuditService auditService;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMembershipRepository membershipRepository;

    @Mock
    private ProjectAccessRepository projectAccessRepository;

    @Mock
    private EnvironmentAccessRepository environmentAccessRepository;

    @InjectMocks
    private SecretService secretService;

    private UUID orgId;
    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID secretId;
    private UUID userId;

    private Workspace workspace;
    private Project project;
    private Environment environment;
    private Secret secret;
    private SecretVersion secretVersion;
    private WorkspaceMembership ownerMembership;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        secretId = UUID.randomUUID();
        userId = UUID.randomUUID();

        workspace = new Workspace(orgId, "Production Org", "prod-org", true);
        workspace.setId(workspaceId);

        project = new Project(workspaceId, "Payment Gateway", "payment-gw", "Payments", userId);
        project.setId(projectId);

        environment = new Environment(projectId, "Production", "production", EnvType.PRODUCTION, "Prod Tier", true, userId);
        environment.setId(environmentId);

        secret = new Secret(environmentId, "STRIPE_API_KEY", "Stripe Live Secret Key", userId);
        // set ID by reflection or save mock
        try {
            var idField = Secret.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(secret, secretId);
        } catch (Exception ignored) {
        }

        secretVersion = new SecretVersion(
                secretId,
                1,
                "ciphertext".getBytes(StandardCharsets.UTF_8),
                "encryptedDek".getBytes(StandardCharsets.UTF_8),
                new byte[12],
                new byte[16],
                "mock-primary-kek",
                userId,
                "Initial version"
        );

        ownerMembership = new WorkspaceMembership(workspaceId, userId, WorkspaceRole.OWNER);
    }

    private void mockValidHierarchy(WorkspaceRole role) {
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(membershipRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(new WorkspaceMembership(workspaceId, userId, role)));
        when(projectRepository.findByIdAndWorkspaceId(projectId, workspaceId))
                .thenReturn(Optional.of(project));
        when(environmentRepository.findByIdAndProjectId(environmentId, projectId))
                .thenReturn(Optional.of(environment));
    }

    @Test
    @DisplayName("Should list secret metadata without revealing plaintext")
    void testGetSecretsSuccess() {
        mockValidHierarchy(WorkspaceRole.DEVELOPER);
        when(secretRepository.findByEnvironmentIdAndStatusNot(environmentId, SecretStatus.DELETED))
                .thenReturn(List.of(secret));

        List<SecretMetadataResponse> result = secretService.getSecrets(
                workspaceId, projectId, environmentId, null, null, userId
        );

        assertEquals(1, result.size());
        assertEquals("STRIPE_API_KEY", result.getFirst().name());
        assertEquals("••••••••••••••••", result.getFirst().maskedValue());
        assertEquals(1, result.getFirst().currentVersionNumber());
    }

    @Test
    @DisplayName("Should fetch secret metadata by ID")
    void testGetSecretByIdSuccess() {
        mockValidHierarchy(WorkspaceRole.DEVELOPER);
        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId))
                .thenReturn(Optional.of(secret));

        SecretMetadataResponse response = secretService.getSecretById(
                workspaceId, projectId, environmentId, secretId, userId
        );

        assertNotNull(response);
        assertEquals("STRIPE_API_KEY", response.name());
        assertEquals(SecretStatus.ACTIVE, response.status());
    }

    @Test
    @DisplayName("Should create a secret with AES-256-GCM envelope encryption and audit log")
    void testCreateSecretSuccess() {
        mockValidHierarchy(WorkspaceRole.DEVELOPER);
        when(projectAccessRepository.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.empty());
        when(environmentAccessRepository.findByEnvironmentIdAndUserId(environmentId, userId)).thenReturn(Optional.empty());
        when(secretRepository.existsByEnvironmentIdAndName(environmentId, "STRIPE_API_KEY")).thenReturn(false);

        when(secretRepository.save(any(Secret.class))).thenAnswer(inv -> {
            Secret s = inv.getArgument(0);
            try {
                var idField = Secret.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(s, secretId);
            } catch (Exception ignored) {
            }
            return s;
        });

        EncryptedPayload mockPayload = new EncryptedPayload(
                "enc_cipher".getBytes(StandardCharsets.UTF_8),
                "enc_dek".getBytes(StandardCharsets.UTF_8),
                new byte[12],
                new byte[16],
                "mock-primary-kek"
        );
        when(encryptionService.encrypt(any(byte[].class), anyString())).thenReturn(mockPayload);

        CreateSecretRequest request = new CreateSecretRequest("STRIPE_API_KEY", "sk_live_1234567890", "Stripe API Key");

        SecretMetadataResponse response = secretService.createSecret(
                workspaceId, projectId, environmentId, request, userId, "req-1", "127.0.0.1"
        );

        assertNotNull(response);
        assertEquals("STRIPE_API_KEY", response.name());
        assertEquals(1, response.currentVersionNumber());

        verify(encryptionService).encrypt(
                eq("sk_live_1234567890".getBytes(StandardCharsets.UTF_8)),
                eq(secretId + ":" + environmentId + ":1")
        );
        verify(secretVersionRepository).save(any(SecretVersion.class));
        verify(auditService).recordSecretAudit(
                eq(orgId), eq(workspaceId), eq(userId), eq(AuditAction.SECRET_CREATED),
                eq(secretId), eq("req-1"), eq("127.0.0.1"), eq("SUCCESS")
        );
    }

    @Test
    @DisplayName("Should throw 409 Conflict when creating secret with duplicate name in same environment")
    void testCreateSecretDuplicateConflict() {
        mockValidHierarchy(WorkspaceRole.DEVELOPER);
        when(projectAccessRepository.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.empty());
        when(environmentAccessRepository.findByEnvironmentIdAndUserId(environmentId, userId)).thenReturn(Optional.empty());
        when(secretRepository.existsByEnvironmentIdAndName(environmentId, "STRIPE_API_KEY")).thenReturn(true);

        CreateSecretRequest request = new CreateSecretRequest("STRIPE_API_KEY", "sk_live_1234567890", "Stripe API Key");

        ApiException ex = assertThrows(ApiException.class, () ->
                secretService.createSecret(workspaceId, projectId, environmentId, request, userId, "req-1", "127.0.0.1"));
        assertEquals("RESOURCE_CONFLICT", ex.getCode());
    }

    @Test
    @DisplayName("Should forbid secret creation for VIEWER role")
    void testCreateSecretForbiddenForViewer() {
        mockValidHierarchy(WorkspaceRole.VIEWER);

        CreateSecretRequest request = new CreateSecretRequest("DATABASE_URL", "postgres://...", "DB url");

        ApiException ex = assertThrows(ApiException.class, () ->
                secretService.createSecret(workspaceId, projectId, environmentId, request, userId, "req-1", "127.0.0.1"));
        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    @DisplayName("Should update secret metadata without version increment")
    void testUpdateSecretMetadataOnly() {
        mockValidHierarchy(WorkspaceRole.DEVELOPER);
        when(projectAccessRepository.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.empty());
        when(environmentAccessRepository.findByEnvironmentIdAndUserId(environmentId, userId)).thenReturn(Optional.empty());
        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(secretRepository.save(any(Secret.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateSecretRequest request = new UpdateSecretRequest("Updated description", SecretStatus.DISABLED, null, null);

        SecretMetadataResponse response = secretService.updateSecret(
                workspaceId, projectId, environmentId, secretId, request, userId, "req-2", "127.0.0.1"
        );

        assertEquals("Updated description", response.description());
        assertEquals(SecretStatus.DISABLED, response.status());
        assertEquals(1, response.currentVersionNumber()); // Version number unchanged

        verify(encryptionService, never()).encrypt(any(), anyString());
        verify(auditService).recordSecretAudit(
                eq(orgId), eq(workspaceId), eq(userId), eq(AuditAction.SECRET_DISABLED),
                eq(secretId), eq("req-2"), eq("127.0.0.1"), eq("SUCCESS")
        );
    }

    @Test
    @DisplayName("Should increment version to v2 when secret value is updated")
    void testUpdateSecretNewValueIncrementsVersion() {
        mockValidHierarchy(WorkspaceRole.DEVELOPER);
        when(projectAccessRepository.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.empty());
        when(environmentAccessRepository.findByEnvironmentIdAndUserId(environmentId, userId)).thenReturn(Optional.empty());
        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(secretRepository.save(any(Secret.class))).thenAnswer(inv -> inv.getArgument(0));

        EncryptedPayload mockPayload = new EncryptedPayload(
                "enc_v2_cipher".getBytes(StandardCharsets.UTF_8),
                "enc_v2_dek".getBytes(StandardCharsets.UTF_8),
                new byte[12],
                new byte[16],
                "mock-primary-kek"
        );
        when(encryptionService.encrypt(any(byte[].class), anyString())).thenReturn(mockPayload);

        UpdateSecretRequest request = new UpdateSecretRequest(null, null, "new_rotated_secret_val", "Quarterly key rotation");

        SecretMetadataResponse response = secretService.updateSecret(
                workspaceId, projectId, environmentId, secretId, request, userId, "req-3", "127.0.0.1"
        );

        assertEquals(2, response.currentVersionNumber());
        verify(encryptionService).encrypt(
                eq("new_rotated_secret_val".getBytes(StandardCharsets.UTF_8)),
                eq(secretId + ":" + environmentId + ":2")
        );
        verify(secretVersionRepository).save(any(SecretVersion.class));
        verify(auditService).recordSecretAudit(
                eq(orgId), eq(workspaceId), eq(userId), eq(AuditAction.SECRET_VALUE_UPDATED),
                eq(secretId), eq("req-3"), eq("127.0.0.1"), eq("SUCCESS")
        );
    }

    @Test
    @DisplayName("Should reveal secret in memory and emit SECRET_REVEALED audit log")
    void testRevealSecretSuccess() {
        mockValidHierarchy(WorkspaceRole.DEVELOPER);
        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 1)).thenReturn(Optional.of(secretVersion));
        when(encryptionService.decrypt(any(EncryptedPayload.class), eq(secretId + ":" + environmentId + ":1")))
                .thenReturn("sk_live_supersecret".getBytes(StandardCharsets.UTF_8));

        SecretRevealResponse response = secretService.revealSecret(
                workspaceId, projectId, environmentId, secretId, null, userId, "req-4", "127.0.0.1"
        );

        assertNotNull(response);
        assertEquals("STRIPE_API_KEY", response.name());
        assertEquals("sk_live_supersecret", response.value());
        assertEquals(1, response.versionNumber());

        verify(auditService).recordSecretAudit(
                eq(orgId), eq(workspaceId), eq(userId), eq(AuditAction.SECRET_REVEALED),
                eq(secretId), eq("req-4"), eq("127.0.0.1"), eq("SUCCESS")
        );
    }

    @Test
    @DisplayName("Should forbid secret reveal for VIEWER role")
    void testRevealSecretForbiddenForViewer() {
        mockValidHierarchy(WorkspaceRole.VIEWER);

        ApiException ex = assertThrows(ApiException.class, () ->
                secretService.revealSecret(workspaceId, projectId, environmentId, secretId, null, userId, "req-5", "127.0.0.1"));
        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    @DisplayName("Should reject reveal on a DELETED secret")
    void testRevealDeletedSecret() {
        mockValidHierarchy(WorkspaceRole.DEVELOPER);
        secret.setStatus(SecretStatus.DELETED);
        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));

        ApiException ex = assertThrows(ApiException.class, () ->
                secretService.revealSecret(workspaceId, projectId, environmentId, secretId, null, userId, "req-6", "127.0.0.1"));
        assertEquals("BAD_REQUEST", ex.getCode());
    }

    @Test
    @DisplayName("Should soft-delete a secret and emit SECRET_DELETED audit log")
    void testDeleteSecretSuccess() {
        mockValidHierarchy(WorkspaceRole.ADMIN);
        when(projectAccessRepository.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.empty());
        when(environmentAccessRepository.findByEnvironmentIdAndUserId(environmentId, userId)).thenReturn(Optional.empty());
        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));

        secretService.deleteSecret(workspaceId, projectId, environmentId, secretId, userId, "req-7", "127.0.0.1");

        assertEquals(SecretStatus.DELETED, secret.getStatus());
        verify(secretRepository).save(secret);
        verify(auditService).recordSecretAudit(
                eq(orgId), eq(workspaceId), eq(userId), eq(AuditAction.SECRET_DELETED),
                eq(secretId), eq("req-7"), eq("127.0.0.1"), eq("SUCCESS")
        );
    }
}
