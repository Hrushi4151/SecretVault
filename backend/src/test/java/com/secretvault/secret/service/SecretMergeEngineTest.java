package com.secretvault.secret.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.project.entity.Project;
import com.secretvault.secret.dto.BranchMergeRequest;
import com.secretvault.secret.dto.BranchMergeResponse;
import com.secretvault.secret.entity.BranchStatus;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretBranch;
import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.entity.VersionType;
import com.secretvault.secret.repository.SecretBranchRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecretMergeEngineTest {

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private SecretVersionRepository secretVersionRepository;

    @Mock
    private SecretBranchRepository branchRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private AuditService auditService;

    @Mock
    private SecretAuthorizationHelper authHelper;

    @InjectMocks
    private SecretMergeEngine mergeEngine;

    private UUID orgId;
    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID stagingEnvId;
    private UUID prodEnvId;
    private UUID secretId;
    private UUID baseVerId;
    private UUID headVerId;
    private UUID mainVerId;
    private UUID branchId;
    private UUID userId;

    private Workspace workspace;
    private Project project;
    private Environment environment;
    private Secret secret;
    private SecretBranch branch;
    private SecretVersion baseVer;
    private SecretVersion mainVer;
    private SecretVersion headVer;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        stagingEnvId = UUID.randomUUID();
        prodEnvId = UUID.randomUUID();
        secretId = UUID.randomUUID();
        baseVerId = UUID.randomUUID();
        headVerId = UUID.randomUUID();
        mainVerId = UUID.randomUUID();
        branchId = UUID.randomUUID();
        userId = UUID.randomUUID();

        workspace = new Workspace(orgId, "Org", "org", true);
        workspace.setId(workspaceId);

        project = new Project(workspaceId, "P", "p", "desc", userId);
        project.setId(projectId);

        environment = new Environment(projectId, "Development", "dev", EnvType.DEVELOPMENT, "desc", false, userId);
        environment.setId(environmentId);

        secret = new Secret(environmentId, "DATABASE_URL", "DB connection", userId);
        try {
            var idField = Secret.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(secret, secretId);
        } catch (Exception ignored) {
        }
        secret.setCurrentVersionNumber(2);

        branch = new SecretBranch(secretId, "feature/db-migration", "Migration branch", baseVerId, headVerId, userId);
        try {
            var idField = SecretBranch.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(branch, branchId);
        } catch (Exception ignored) {
        }

        baseVer = new SecretVersion(secretId, 1, VersionType.INITIAL, "base_cipher".getBytes(), "dek".getBytes(), new byte[12], new byte[16], "mock-kek", userId, "v1");
        mainVer = new SecretVersion(secretId, 2, VersionType.VALUE_UPDATE, "main_cipher".getBytes(), "dek".getBytes(), new byte[12], new byte[16], "mock-kek", userId, "v2");
        headVer = new SecretVersion(secretId, 3, VersionType.BRANCH_COMMIT, "head_cipher".getBytes(), "dek".getBytes(), new byte[12], new byte[16], "mock-kek", userId, "v3");
    }

    private void mockAuth() {
        when(authHelper.verifyHierarchyAndBranchWriteAccess(workspaceId, projectId, environmentId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(workspace, new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER), project, environment));
    }

    @Test
    @DisplayName("Case 1: Main unchanged from base, branch changed -> Clean merge creates new version on main")
    void testMergeBranchMainUnchangedBranchChangedSuccess() {
        mockAuth();

        secret.setCurrentVersionNumber(1); // Main is still at base (v1)
        branch.setBaseVersionId(baseVerId);
        branch.setHeadVersionId(headVerId);

        when(secretRepository.findByIdAndEnvironmentIdForUpdate(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(branchRepository.findByIdAndSecretId(branchId, secretId)).thenReturn(Optional.of(branch));
        when(secretVersionRepository.findById(baseVerId)).thenReturn(Optional.of(baseVer));
        when(secretVersionRepository.findById(headVerId)).thenReturn(Optional.of(headVer));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 1)).thenReturn(Optional.of(baseVer));

        when(encryptionService.decrypt(any(EncryptedPayload.class), eq(secretId + ":" + environmentId + ":1")))
                .thenReturn("postgres://db:5432/v1".getBytes(StandardCharsets.UTF_8));
        when(encryptionService.decrypt(any(EncryptedPayload.class), eq(secretId + ":" + environmentId + ":3")))
                .thenReturn("postgres://db:5432/v3_branch".getBytes(StandardCharsets.UTF_8));

        when(secretVersionRepository.findTopBySecretIdOrderByVersionNumberDesc(secretId)).thenReturn(Optional.of(headVer));

        EncryptedPayload mergePayload = new EncryptedPayload(
                "merged_cipher".getBytes(), "merged_dek".getBytes(), new byte[12], new byte[16], "mock-kek"
        );
        when(encryptionService.encrypt(eq("postgres://db:5432/v3_branch".getBytes(StandardCharsets.UTF_8)), eq(secretId + ":" + environmentId + ":4")))
                .thenReturn(mergePayload);

        when(secretVersionRepository.save(any(SecretVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(secretRepository.save(any(Secret.class))).thenAnswer(inv -> inv.getArgument(0));
        when(branchRepository.save(any(SecretBranch.class))).thenAnswer(inv -> inv.getArgument(0));

        BranchMergeRequest request = new BranchMergeRequest(1, 3, "Merged migration branch");
        BranchMergeResponse response = mergeEngine.mergeBranch(
                workspaceId, projectId, environmentId, secretId, branchId, request, userId, "req-m", "127.0.0.1"
        );

        assertNotNull(response);
        assertEquals(4, response.mergeVersionNumber());
        assertEquals("MERGED", response.status());
        assertEquals(BranchStatus.MERGED, branch.getStatus());
        assertEquals(4, secret.getCurrentVersionNumber());

        verify(auditService).recordSecretAudit(
                eq(orgId), eq(workspaceId), eq(userId), eq(AuditAction.SECRET_BRANCH_MERGED),
                eq(secretId), eq("req-m"), eq("127.0.0.1"), eq("SUCCESS")
        );
    }

    @Test
    @DisplayName("Case 2: 3-Way Merge Conflict detected when both main and branch modified value differently")
    void testMergeBranchConflictDetected() {
        mockAuth();

        secret.setCurrentVersionNumber(2);
        branch.setBaseVersionId(baseVerId);
        branch.setHeadVersionId(headVerId);

        when(secretRepository.findByIdAndEnvironmentIdForUpdate(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(branchRepository.findByIdAndSecretId(branchId, secretId)).thenReturn(Optional.of(branch));
        when(secretVersionRepository.findById(baseVerId)).thenReturn(Optional.of(baseVer));
        when(secretVersionRepository.findById(headVerId)).thenReturn(Optional.of(headVer));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 2)).thenReturn(Optional.of(mainVer));

        when(encryptionService.decrypt(any(EncryptedPayload.class), eq(secretId + ":" + environmentId + ":1")))
                .thenReturn("postgres://db:5432/v1".getBytes(StandardCharsets.UTF_8));
        when(encryptionService.decrypt(any(EncryptedPayload.class), eq(secretId + ":" + environmentId + ":2")))
                .thenReturn("postgres://db:5432/v2_main_edit".getBytes(StandardCharsets.UTF_8));
        when(encryptionService.decrypt(any(EncryptedPayload.class), eq(secretId + ":" + environmentId + ":3")))
                .thenReturn("postgres://db:5432/v3_branch_edit".getBytes(StandardCharsets.UTF_8));

        BranchMergeRequest request = new BranchMergeRequest(2, 3, "Attempt conflicting merge");
        ApiException ex = assertThrows(ApiException.class, () ->
                mergeEngine.mergeBranch(workspaceId, projectId, environmentId, secretId, branchId, request, userId, "req-m", "127.0.0.1"));

        assertEquals("RESOURCE_CONFLICT", ex.getCode());
        assertTrue(ex.getMessage().contains("3-Way Merge Conflict"));
    }

    @Test
    @DisplayName("Staging: Should reject branch merge with BRANCHES_NOT_ALLOWED_FOR_ENVIRONMENT")
    void testMergeBranch_RejectedForStaging() {
        when(authHelper.verifyHierarchyAndBranchWriteAccess(workspaceId, projectId, stagingEnvId, userId))
                .thenThrow(ApiException.branchesNotAllowed("Staging", EnvType.STAGING));

        BranchMergeRequest request = new BranchMergeRequest(1, 2, "Staging merge attempt");
        ApiException ex = assertThrows(ApiException.class, () ->
                mergeEngine.mergeBranch(workspaceId, projectId, stagingEnvId, secretId, branchId, request, userId, "req-stg", "127.0.0.1"));

        assertEquals("BRANCHES_NOT_ALLOWED_FOR_ENVIRONMENT", ex.getCode());
    }

    @Test
    @DisplayName("Production: Should reject branch merge with BRANCHES_NOT_ALLOWED_FOR_ENVIRONMENT")
    void testMergeBranch_RejectedForProduction() {
        when(authHelper.verifyHierarchyAndBranchWriteAccess(workspaceId, projectId, prodEnvId, userId))
                .thenThrow(ApiException.branchesNotAllowed("Production", EnvType.PRODUCTION));

        BranchMergeRequest request = new BranchMergeRequest(1, 2, "Prod merge attempt");
        ApiException ex = assertThrows(ApiException.class, () ->
                mergeEngine.mergeBranch(workspaceId, projectId, prodEnvId, secretId, branchId, request, userId, "req-prod", "127.0.0.1"));

        assertEquals("BRANCHES_NOT_ALLOWED_FOR_ENVIRONMENT", ex.getCode());
    }
}
