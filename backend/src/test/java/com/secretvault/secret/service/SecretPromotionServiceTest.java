package com.secretvault.secret.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecretPromotionServiceTest {

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private SecretVersionRepository secretVersionRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private AuditService auditService;

    @Mock
    private SecretAuthorizationHelper authHelper;

    @InjectMocks
    private SecretPromotionService promotionService;

    private UUID orgId;
    private UUID workspaceId;
    private UUID projectId;
    private UUID devEnvId;
    private UUID prodEnvId;
    private UUID devSecretId;
    private UUID devVerId;
    private UUID userId;

    private Workspace workspace;
    private Project project;
    private Environment devEnv;
    private Environment prodEnv;
    private Secret devSecret;
    private SecretVersion devVer;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        devEnvId = UUID.randomUUID();
        prodEnvId = UUID.randomUUID();
        devSecretId = UUID.randomUUID();
        devVerId = UUID.randomUUID();
        userId = UUID.randomUUID();

        workspace = new Workspace(orgId, "Org", "org", true);
        workspace.setId(workspaceId);

        project = new Project(workspaceId, "P", "p", "desc", userId);
        project.setId(projectId);

        devEnv = new Environment(projectId, "Development", "development", EnvType.DEVELOPMENT, "Dev", false, userId);
        devEnv.setId(devEnvId);

        prodEnv = new Environment(projectId, "Production", "production", EnvType.PRODUCTION, "Prod", true, userId);
        prodEnv.setId(prodEnvId);

        devSecret = new Secret(devEnvId, "STRIPE_KEY", "Stripe API Key", userId);
        try {
            var idField = Secret.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(devSecret, devSecretId);
        } catch (Exception ignored) {
        }
        devSecret.setCurrentVersionNumber(1);

        devVer = new SecretVersion(devSecretId, 1, VersionType.INITIAL, "cipher_dev".getBytes(), "dek_dev".getBytes(), new byte[12], new byte[16], "mock-kek", userId, "v1");
        try {
            var idField = SecretVersion.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(devVer, devVerId);
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("Should preview promotion and accurately classify ADDED secrets")
    void testPreviewPromotionSuccess() {
        when(authHelper.verifyHierarchyAndReadAccess(workspaceId, projectId, devEnvId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(workspace, new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER), project, devEnv));
        when(authHelper.verifyHierarchyAndReadAccess(workspaceId, projectId, prodEnvId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(workspace, new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER), project, prodEnv));

        when(environmentRepository.findByIdAndProjectId(devEnvId, projectId)).thenReturn(Optional.of(devEnv));
        when(environmentRepository.findByIdAndProjectId(prodEnvId, projectId)).thenReturn(Optional.of(prodEnv));

        when(secretRepository.findByEnvironmentIdAndStatusNot(devEnvId, SecretStatus.DELETED)).thenReturn(List.of(devSecret));
        when(secretRepository.findByEnvironmentIdAndStatusNot(prodEnvId, SecretStatus.DELETED)).thenReturn(List.of());

        PromotionPreviewRequest request = new PromotionPreviewRequest(prodEnvId, null);
        PromotionPreviewResponse response = promotionService.previewPromotion(
                workspaceId, projectId, devEnvId, request, userId
        );

        assertNotNull(response);
        assertEquals(1, response.totalCandidates());
        assertEquals(1, response.addedCount());
        assertEquals(0, response.modifiedCount());
        assertTrue(response.isDestinationProtected());
        assertEquals("ADDED", response.items().getFirst().status());
    }

    @Test
    @DisplayName("Should reject previewing promotion when source and destination environments are identical")
    void testPreviewPromotionSameEnvironmentRejected() {
        when(authHelper.verifyHierarchyAndReadAccess(workspaceId, projectId, devEnvId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(workspace, new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER), project, devEnv));

        PromotionPreviewRequest request = new PromotionPreviewRequest(devEnvId, null);
        ApiException ex = assertThrows(ApiException.class, () ->
                promotionService.previewPromotion(workspaceId, projectId, devEnvId, request, userId));
        assertEquals("BAD_REQUEST", ex.getCode());
    }

    @Test
    @DisplayName("Should execute promotion provisioning new destination secret with fresh keys and lineage")
    void testExecutePromotionSuccessWithFreshDestinationKeysAndLineage() {
        when(authHelper.verifyHierarchyAndReadAccess(workspaceId, projectId, devEnvId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(workspace, new WorkspaceMembership(workspaceId, userId, WorkspaceRole.ADMIN), project, devEnv));
        when(authHelper.verifyHierarchyAndWriteAccess(workspaceId, projectId, prodEnvId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(workspace, new WorkspaceMembership(workspaceId, userId, WorkspaceRole.ADMIN), project, prodEnv));

        when(environmentRepository.findByIdAndProjectId(prodEnvId, projectId)).thenReturn(Optional.of(prodEnv));
        when(secretRepository.findByEnvironmentIdAndStatusNot(devEnvId, SecretStatus.DELETED)).thenReturn(List.of(devSecret));
        when(secretRepository.findByEnvironmentIdAndName(prodEnvId, "STRIPE_KEY")).thenReturn(Optional.empty());

        when(secretVersionRepository.findBySecretIdAndVersionNumber(devSecretId, 1)).thenReturn(Optional.of(devVer));
        when(encryptionService.decrypt(any(EncryptedPayload.class), eq(devSecretId + ":" + devEnvId + ":1")))
                .thenReturn("sk_live_stripe_secret_12345".getBytes(StandardCharsets.UTF_8));

        UUID newProdSecretId = UUID.randomUUID();
        when(secretRepository.save(any(Secret.class))).thenAnswer(inv -> {
            Secret s = inv.getArgument(0);
            try {
                var idField = Secret.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(s, newProdSecretId);
            } catch (Exception ignored) {
            }
            return s;
        });

        EncryptedPayload prodPayload = new EncryptedPayload("prod_cipher".getBytes(), "prod_dek".getBytes(), new byte[12], new byte[16], "mock-kek");
        when(encryptionService.encrypt(eq("sk_live_stripe_secret_12345".getBytes(StandardCharsets.UTF_8)), eq(newProdSecretId + ":" + prodEnvId + ":1")))
                .thenReturn(prodPayload);

        when(secretVersionRepository.save(any(SecretVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        ExecutePromotionRequest request = new ExecutePromotionRequest(prodEnvId, null, null, "Promoting release 2026.09");
        PromotionResultResponse result = promotionService.executePromotion(
                workspaceId, projectId, devEnvId, request, userId, "req-prom", "127.0.0.1"
        );

        assertNotNull(result);
        assertEquals(1, result.promotedCount());
        assertEquals(0, result.failedCount());
        assertEquals("CREATED", result.promotedSecrets().getFirst().action());

        verify(auditService).recordSecretAudit(
                eq(orgId), eq(workspaceId), eq(userId), eq(AuditAction.SECRET_PROMOTION_COMPLETED),
                eq(prodEnvId), eq("req-prom"), eq("127.0.0.1"), eq("SUCCESS")
        );
    }
}
