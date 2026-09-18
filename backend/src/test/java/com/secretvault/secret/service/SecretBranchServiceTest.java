package com.secretvault.secret.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.project.entity.Project;
import com.secretvault.secret.dto.BranchCommitRequest;
import com.secretvault.secret.dto.BranchComparisonResponse;
import com.secretvault.secret.dto.CreateBranchRequest;
import com.secretvault.secret.dto.SecretBranchResponse;
import com.secretvault.secret.dto.SecretVersionResponse;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecretBranchServiceTest {

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
    private SecretBranchService branchService;

    private UUID orgId;
    private UUID workspaceId;
    private UUID projectId;
    private UUID environmentId;
    private UUID stagingEnvId;
    private UUID prodEnvId;
    private UUID secretId;
    private UUID versionId;
    private UUID branchId;
    private UUID userId;

    private Workspace workspace;
    private Project project;
    private Environment devEnvironment;
    private Environment stagingEnvironment;
    private Environment prodEnvironment;
    private Secret secret;
    private SecretVersion baseVersion;
    private SecretBranch branch;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        workspaceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();
        stagingEnvId = UUID.randomUUID();
        prodEnvId = UUID.randomUUID();
        secretId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        branchId = UUID.randomUUID();
        userId = UUID.randomUUID();

        workspace = new Workspace(orgId, "Org", "org", true);
        workspace.setId(workspaceId);

        project = new Project(workspaceId, "P", "p", "desc", userId);
        project.setId(projectId);

        devEnvironment = new Environment(projectId, "Development", "dev", EnvType.DEVELOPMENT, "desc", false, userId);
        devEnvironment.setId(environmentId);

        stagingEnvironment = new Environment(projectId, "Staging", "staging", EnvType.STAGING, "desc", false, userId);
        stagingEnvironment.setId(stagingEnvId);

        prodEnvironment = new Environment(projectId, "Production", "prod", EnvType.PRODUCTION, "desc", true, userId);
        prodEnvironment.setId(prodEnvId);

        secret = new Secret(environmentId, "JWT_SECRET", "Auth token secret", userId);
        try {
            var idField = Secret.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(secret, secretId);
        } catch (Exception ignored) {
        }
        secret.setCurrentVersionNumber(2);

        baseVersion = new SecretVersion(secretId, 2, VersionType.VALUE_UPDATE, "cipher".getBytes(), "dek".getBytes(), new byte[12], new byte[16], "mock-kek", userId, "v2");
        try {
            var idField = SecretVersion.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(baseVersion, versionId);
        } catch (Exception ignored) {
        }

        branch = new SecretBranch(secretId, "feature/auth-v2", "Auth upgrade branch", versionId, versionId, userId);
        try {
            var idField = SecretBranch.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(branch, branchId);
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("Development: Should create a new branch from a base version and emit audit event")
    void testCreateBranchSuccess() {
        when(authHelper.verifyHierarchyAndBranchWriteAccess(workspaceId, projectId, environmentId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(workspace, new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER), project, devEnvironment));

        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(branchRepository.existsBySecretIdAndName(secretId, "feature/auth-v2")).thenReturn(false);
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 2)).thenReturn(Optional.of(baseVersion));

        when(branchRepository.save(any(SecretBranch.class))).thenAnswer(inv -> {
            SecretBranch b = inv.getArgument(0);
            b.setId(branchId);
            return b;
        });

        CreateBranchRequest request = new CreateBranchRequest("feature/auth-v2", 2, "Auth upgrade branch");
        SecretBranchResponse response = branchService.createBranch(
                workspaceId, projectId, environmentId, secretId, request, userId, "req-br", "127.0.0.1"
        );

        assertNotNull(response);
        assertEquals("feature/auth-v2", response.name());
        assertEquals(2, response.baseVersionNumber());
        assertEquals(BranchStatus.ACTIVE, response.status());

        verify(auditService).recordSecretAudit(
                eq(orgId), eq(workspaceId), eq(userId), eq(AuditAction.SECRET_BRANCH_CREATED),
                eq(secretId), eq("req-br"), eq("127.0.0.1"), eq("SUCCESS")
        );
    }

    @Test
    @DisplayName("Staging: Should reject creating a branch with BRANCHES_NOT_ALLOWED_FOR_ENVIRONMENT")
    void testCreateBranch_RejectedForStaging() {
        when(authHelper.verifyHierarchyAndBranchWriteAccess(workspaceId, projectId, stagingEnvId, userId))
                .thenThrow(ApiException.branchesNotAllowed("Staging", EnvType.STAGING));

        CreateBranchRequest request = new CreateBranchRequest("feature/new-feature", 1, "Staging branch attempt");
        ApiException ex = assertThrows(ApiException.class, () ->
                branchService.createBranch(workspaceId, projectId, stagingEnvId, secretId, request, userId, "req-stg", "127.0.0.1"));

        assertEquals("BRANCHES_NOT_ALLOWED_FOR_ENVIRONMENT", ex.getCode());
    }

    @Test
    @DisplayName("Production: Should reject creating a branch with BRANCHES_NOT_ALLOWED_FOR_ENVIRONMENT")
    void testCreateBranch_RejectedForProduction() {
        when(authHelper.verifyHierarchyAndBranchWriteAccess(workspaceId, projectId, prodEnvId, userId))
                .thenThrow(ApiException.branchesNotAllowed("Production", EnvType.PRODUCTION));

        CreateBranchRequest request = new CreateBranchRequest("feature/prod-hotfix", 1, "Prod branch attempt");
        ApiException ex = assertThrows(ApiException.class, () ->
                branchService.createBranch(workspaceId, projectId, prodEnvId, secretId, request, userId, "req-prod", "127.0.0.1"));

        assertEquals("BRANCHES_NOT_ALLOWED_FOR_ENVIRONMENT", ex.getCode());
    }

    @Test
    @DisplayName("Should reject creating a branch named 'main'")
    void testCreateBranchWithMainNameRejected() {
        when(authHelper.verifyHierarchyAndBranchWriteAccess(workspaceId, projectId, environmentId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(workspace, new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER), project, devEnvironment));

        when(secretRepository.findByIdAndEnvironmentId(secretId, environmentId)).thenReturn(Optional.of(secret));

        CreateBranchRequest request = new CreateBranchRequest("main", 1, "Main branch");
        ApiException ex = assertThrows(ApiException.class, () ->
                branchService.createBranch(workspaceId, projectId, environmentId, secretId, request, userId, "req-br", "127.0.0.1"));
        assertEquals("BAD_REQUEST", ex.getCode());
    }

    @Test
    @DisplayName("Development: Should create branch commit without mutating main trunk current version pointer")
    void testCreateBranchVersionSuccessDoesNotMutateMainCurrentPointer() {
        when(authHelper.verifyHierarchyAndBranchWriteAccess(workspaceId, projectId, environmentId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(workspace, new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER), project, devEnvironment));

        when(secretRepository.findByIdAndEnvironmentIdForUpdate(secretId, environmentId)).thenReturn(Optional.of(secret));
        when(branchRepository.findByIdAndSecretId(branchId, secretId)).thenReturn(Optional.of(branch));
        when(secretVersionRepository.findTopBySecretIdOrderByVersionNumberDesc(secretId)).thenReturn(Optional.of(baseVersion));

        EncryptedPayload mockPayload = new EncryptedPayload(
                "branch_cipher".getBytes(), "branch_dek".getBytes(), new byte[12], new byte[16], "mock-kek"
        );
        when(encryptionService.encrypt(eq("new_branch_value".getBytes(StandardCharsets.UTF_8)), eq(secretId + ":" + environmentId + ":3")))
                .thenReturn(mockPayload);

        when(secretVersionRepository.save(any(SecretVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(branchRepository.save(any(SecretBranch.class))).thenAnswer(inv -> inv.getArgument(0));

        BranchCommitRequest request = new BranchCommitRequest("new_branch_value", null, "Updated JWT algorithm in branch");
        SecretVersionResponse response = branchService.createBranchVersion(
                workspaceId, projectId, environmentId, secretId, branchId, request, userId, "req-commit", "127.0.0.1"
        );

        assertNotNull(response);
        assertEquals(3, response.versionNumber());
        assertEquals(VersionType.BRANCH_COMMIT, response.versionType());
        assertEquals(branchId, response.branchId());
        assertFalse(response.isCurrent()); // NOT current version on main

        assertEquals(2, secret.getCurrentVersionNumber()); // Main current version remains unchanged at 2!

        verify(auditService).recordSecretAudit(
                eq(orgId), eq(workspaceId), eq(userId), eq(AuditAction.SECRET_BRANCH_COMMITTED),
                eq(secretId), eq("req-commit"), eq("127.0.0.1"), eq("SUCCESS")
        );
    }

    @Test
    @DisplayName("Production: Should reject branch commit with BRANCHES_NOT_ALLOWED_FOR_ENVIRONMENT")
    void testBranchCommit_RejectedForProduction() {
        when(authHelper.verifyHierarchyAndBranchWriteAccess(workspaceId, projectId, prodEnvId, userId))
                .thenThrow(ApiException.branchesNotAllowed("Production", EnvType.PRODUCTION));

        BranchCommitRequest request = new BranchCommitRequest("injected_val", null, "Illegal prod branch commit");
        ApiException ex = assertThrows(ApiException.class, () ->
                branchService.createBranchVersion(workspaceId, projectId, prodEnvId, secretId, branchId, request, userId, "req-pcommit", "127.0.0.1"));

        assertEquals("BRANCHES_NOT_ALLOWED_FOR_ENVIRONMENT", ex.getCode());
    }

    @Test
    @DisplayName("Non-development (Staging/Prod): getBranches returns only canonical main branch without querying custom branches")
    void testGetBranches_NonDevelopmentReturnsOnlyMainTrunk() {
        when(authHelper.verifyHierarchyAndReadAccess(workspaceId, projectId, prodEnvId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(workspace, new WorkspaceMembership(workspaceId, userId, WorkspaceRole.VIEWER), project, prodEnvironment));

        when(secretRepository.findByIdAndEnvironmentId(secretId, prodEnvId)).thenReturn(Optional.of(secret));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 2)).thenReturn(Optional.of(baseVersion));
        when(secretVersionRepository.findBySecretIdAndVersionNumber(secretId, 1)).thenReturn(Optional.of(baseVersion));

        List<SecretBranchResponse> list = branchService.getBranches(workspaceId, projectId, prodEnvId, secretId, userId);

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("main", list.get(0).name());
        verify(branchRepository, never()).findBySecretId(any());
    }

    @Test
    @DisplayName("IDOR / Cross-Secret: Attempting to use a branch ID against a different secret is rejected with 404")
    void testCrossSecretBranchId_Rejected() {
        UUID otherSecretId = UUID.randomUUID();
        when(authHelper.verifyHierarchyAndBranchReadAccess(workspaceId, projectId, environmentId, userId))
                .thenReturn(new SecretAuthorizationHelper.WorkspaceContext(workspace, new WorkspaceMembership(workspaceId, userId, WorkspaceRole.DEVELOPER), project, devEnvironment));

        when(secretRepository.existsById(otherSecretId)).thenReturn(true);
        when(branchRepository.findByIdAndSecretId(branchId, otherSecretId)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () ->
                branchService.getBranchById(workspaceId, projectId, environmentId, otherSecretId, branchId, userId));

        assertEquals("RESOURCE_NOT_FOUND", ex.getCode());
    }
}
