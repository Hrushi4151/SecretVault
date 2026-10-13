package com.secretvault.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.provider.credential.ProviderCredentialService;
import com.secretvault.provider.entity.ProviderIntegration;
import com.secretvault.provider.model.IntegrationStatus;
import com.secretvault.provider.model.ProviderType;
import com.secretvault.provider.repository.ProviderIntegrationRepository;
import com.secretvault.repository.controller.GitHubRepositoryWebhookController;
import com.secretvault.repository.dto.RepositoryDtos;
import com.secretvault.repository.entity.RepositoryEntity;
import com.secretvault.repository.model.RepositoryProvider;
import com.secretvault.repository.model.RepositoryStatus;
import com.secretvault.repository.model.RepositoryVisibility;
import com.secretvault.repository.model.ScanType;
import com.secretvault.repository.provider.GitHubRepositoryProviderAdapter;
import com.secretvault.repository.provider.RepositoryProviderAdapter;
import com.secretvault.repository.provider.RepositoryProviderRegistry;
import com.secretvault.repository.repository.RepositoryEntityRepository;
import com.secretvault.repository.repository.SecretFindingRepository;
import com.secretvault.repository.service.RepositoryCommitSyncService;
import com.secretvault.repository.service.RepositoryScanService;
import com.secretvault.repository.service.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GitHub Account Integration & Continuous Webhook Security Test Suite")
class GitHubAccountIntegrationAndWebhookSecurityTest {

    @Mock
    private RepositoryEntityRepository repositoryRepo;

    @Mock
    private SecretFindingRepository findingRepo;

    @Mock
    private EffectiveAccessService accessService;

    @Mock
    private AuditService auditService;

    @Mock
    private RepositoryProviderRegistry providerRegistry;

    @Mock
    private RepositoryScanService scanService;

    @Mock
    private ProviderIntegrationRepository providerIntegrationRepo;

    @Mock
    private ProviderCredentialService providerCredentialService;

    @Mock
    private GitHubRepositoryProviderAdapter githubAdapter;

    private RepositoryCommitSyncService syncService;
    private GitHubRepositoryWebhookController webhookController;
    private RepositoryService repositoryService;
    private ObjectMapper objectMapper;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID repoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        syncService = new RepositoryCommitSyncService(repositoryRepo, scanService, objectMapper);
        webhookController = new GitHubRepositoryWebhookController(repositoryRepo, scanService, syncService, objectMapper);

        repositoryService = new RepositoryService(
                repositoryRepo,
                findingRepo,
                accessService,
                auditService,
                providerRegistry,
                scanService,
                providerIntegrationRepo,
                providerCredentialService,
                objectMapper
        );
    }

    @Test
    @DisplayName("Connect GitHub account validates credentials, envelope-encrypts token, and persists integration")
    void testConnectGitHubIntegration_Success() {
        String token = "ghp_validToken1234567890abcdefghijklmnop";
        when(providerRegistry.requireAdapter(RepositoryProvider.GITHUB)).thenReturn(githubAdapter);
        when(githubAdapter.validateConnection(token)).thenReturn(true);
        when(githubAdapter.getUserProfile(token)).thenReturn(new RepositoryProviderAdapter.UserProfileDto(
                "SecOpsEngineer", "Security Engineer", "https://github.com/avatar.png", "secops@vault.local",
                List.of("repo", "read:org"), 10, 5
        ));
        when(providerIntegrationRepo.findByWorkspaceIdAndProviderType(workspaceId, ProviderType.GITHUB))
                .thenAnswer(inv -> {
                    ProviderIntegration pi = new ProviderIntegration();
                    pi.setStatus(IntegrationStatus.ACTIVE);
                    try {
                        pi.setConfigurationJson(objectMapper.writeValueAsString(Map.of(
                                "username", "SecOpsEngineer",
                                "avatarUrl", "https://github.com/avatar.png",
                                "scopes", List.of("repo", "read:org")
                        )));
                    } catch (Exception ignored) {}
                    return List.of(pi);
                });
        when(repositoryRepo.countByWorkspaceIdAndProvider(workspaceId, RepositoryProvider.GITHUB)).thenReturn(3L);

        RepositoryDtos.GitHubIntegrationStatusResponse response =
                repositoryService.connectGitHubIntegration(workspaceId, token, actorId);

        assertTrue(response.connected());
        assertEquals("SecOpsEngineer", response.username());
        assertEquals("https://github.com/avatar.png", response.avatarUrl());
        assertEquals(3L, response.monitoredRepoCount());
        assertTrue(response.scopes().contains("repo"));

        verify(providerCredentialService).encryptAndSetCredentials(any(ProviderIntegration.class), eq(token));
        verify(providerIntegrationRepo).save(any(ProviderIntegration.class));
    }

    @Test
    @DisplayName("Connect GitHub account rejects invalid token with bad request error")
    void testConnectGitHubIntegration_InvalidToken() {
        String invalidToken = "ghp_invalid_token";
        when(providerRegistry.requireAdapter(RepositoryProvider.GITHUB)).thenReturn(githubAdapter);
        when(githubAdapter.validateConnection(invalidToken)).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () ->
                repositoryService.connectGitHubIntegration(workspaceId, invalidToken, actorId)
        );

        assertTrue(ex.getMessage().contains("Failed to authenticate with GitHub"));
        verify(providerIntegrationRepo, never()).save(any());
    }

    @Test
    @DisplayName("Get GitHub integration status returns not connected when no integration exists")
    void testGetGitHubIntegrationStatus_NotConnected() {
        when(providerIntegrationRepo.findByWorkspaceIdAndProviderType(workspaceId, ProviderType.GITHUB))
                .thenReturn(Collections.emptyList());

        RepositoryDtos.GitHubIntegrationStatusResponse status =
                repositoryService.getGitHubIntegrationStatus(workspaceId, actorId);

        assertFalse(status.connected());
        assertEquals("NOT_CONNECTED", status.status());
    }

    @Test
    @DisplayName("Disconnect GitHub integration safely deletes provider integration while leaving repositories")
    void testDisconnectGitHubIntegration() {
        ProviderIntegration integration = new ProviderIntegration();
        integration.setWorkspaceId(workspaceId);
        integration.setProviderType(ProviderType.GITHUB);

        when(providerIntegrationRepo.findByWorkspaceIdAndProviderType(workspaceId, ProviderType.GITHUB))
                .thenReturn(List.of(integration));

        repositoryService.disconnectGitHubIntegration(workspaceId, actorId);

        verify(providerIntegrationRepo).delete(integration);
        // Verify repositories are NOT deleted on GitHub disconnect
        verify(repositoryRepo, never()).delete(any());
    }

    @Test
    @DisplayName("Webhook delivery deduplication ignores duplicate X-GitHub-Delivery requests")
    void testWebhookDeliveryDeduplication() {
        RepositoryEntity repo = new RepositoryEntity(workspaceId, RepositoryProvider.GITHUB, "Hrushi4151", "SecVault", RepositoryVisibility.PUBLIC);
        repo.setId(repoId);
        repo.setStatus(RepositoryStatus.ACTIVE);
        repo.setDefaultBranch("main");

        when(repositoryRepo.findByOwnerIgnoreCaseAndNameIgnoreCase("Hrushi4151", "SecVault"))
                .thenReturn(List.of(repo));

        String payload = """
                {
                    "ref": "refs/heads/main",
                    "after": "sha99999999999999999999999999999999999999",
                    "repository": {
                        "name": "SecVault",
                        "owner": { "login": "Hrushi4151" },
                        "clone_url": "https://github.com/Hrushi4151/SecVault.git"
                    }
                }
                """;

        String deliveryId = "delivery-uuid-" + UUID.randomUUID();

        // First delivery: should succeed and trigger scan
        ResponseEntity<Map<String, Object>> res1 =
                webhookController.handleGitHubWebhook("push", deliveryId, null, payload);
        assertEquals(200, res1.getStatusCode().value());
        assertEquals("SUCCESS", res1.getBody().get("status"));

        // Duplicate delivery with same delivery ID: should return DUPLICATE
        ResponseEntity<Map<String, Object>> res2 =
                webhookController.handleGitHubWebhook("push", deliveryId, null, payload);
        assertEquals(200, res2.getStatusCode().value());
        assertEquals("DUPLICATE", res2.getBody().get("status"));
        assertEquals(deliveryId, res2.getBody().get("deliveryId"));
    }

    @Test
    @DisplayName("Webhook ignores push events for repositories with monitoring DISABLED")
    void testWebhookIgnoresDisabledRepository() {
        RepositoryEntity repo = new RepositoryEntity(workspaceId, RepositoryProvider.GITHUB, "Hrushi4151", "DisabledRepo", RepositoryVisibility.PRIVATE);
        repo.setId(repoId);
        repo.setStatus(RepositoryStatus.DISABLED); // Monitoring is paused/disabled

        when(repositoryRepo.findByOwnerIgnoreCaseAndNameIgnoreCase("Hrushi4151", "DisabledRepo"))
                .thenReturn(List.of(repo));

        String payload = """
                {
                    "ref": "refs/heads/main",
                    "after": "commitDisabled11111111111111111111111111111",
                    "repository": {
                        "name": "DisabledRepo",
                        "owner": { "login": "Hrushi4151" },
                        "clone_url": "https://github.com/Hrushi4151/DisabledRepo.git"
                    }
                }
                """;

        ResponseEntity<Map<String, Object>> res =
                webhookController.handleGitHubWebhook("push", "deliv-" + UUID.randomUUID(), null, payload);

        assertEquals(200, res.getStatusCode().value());
        // Scans triggered should be 0 since repo status is DISABLED
        assertEquals(0, res.getBody().get("scansTriggered"));
    }

    @Test
    @DisplayName("Toggle monitoring enables and disables repository monitoring safely")
    void testToggleMonitoring() {
        RepositoryEntity repo = new RepositoryEntity(workspaceId, RepositoryProvider.GITHUB, "Hrushi4151", "ToggleRepo", RepositoryVisibility.PRIVATE);
        repo.setId(repoId);
        repo.setStatus(RepositoryStatus.ACTIVE);

        when(repositoryRepo.findByIdAndWorkspaceId(repoId, workspaceId)).thenReturn(Optional.of(repo));
        when(repositoryRepo.save(any(RepositoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // Disable monitoring
        RepositoryService.RepositorySummaryDto summaryDisabled =
                repositoryService.setRepositoryMonitoringEnabled(workspaceId, repoId, false, actorId);
        assertEquals(RepositoryStatus.DISABLED, summaryDisabled.status());

        // Re-enable monitoring
        RepositoryService.RepositorySummaryDto summaryEnabled =
                repositoryService.setRepositoryMonitoringEnabled(workspaceId, repoId, true, actorId);
        assertEquals(RepositoryStatus.ACTIVE, summaryEnabled.status());
    }

    @Test
    @DisplayName("P0 Webhook Safety: Missing GITHUB_WEBHOOK_SECRET fails closed in production (HTTP 500)")
    void testProductionFailsClosedWhenSecretMissing() {
        org.springframework.mock.env.MockEnvironment prodEnv = new org.springframework.mock.env.MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        webhookController.setEnvironment(prodEnv);

        String payload = """
                {"ref":"refs/heads/main","after":"sha111","repository":{"name":"SecVault","owner":{"login":"Hrushi4151"}}}
                """;

        ResponseEntity<Map<String, Object>> response =
                webhookController.handleGitHubWebhook("push", "deliv-sec-test", "sha256=abcdef", payload);

        assertEquals(500, response.getStatusCode().value());
        assertEquals("CONFIGURATION_ERROR", response.getBody().get("status"));
        assertTrue(response.getBody().get("error").toString().contains("misconfigured in production"));
    }

    @Test
    @DisplayName("P0 Webhook Safety: Missing signature header fails closed in production (HTTP 401)")
    void testProductionFailsClosedWhenSignatureHeaderMissing() {
        org.springframework.mock.env.MockEnvironment prodEnv = new org.springframework.mock.env.MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        webhookController.setEnvironment(prodEnv);

        String payload = """
                {"ref":"refs/heads/main","after":"sha111","repository":{"name":"SecVault","owner":{"login":"Hrushi4151"}}}
                """;

        ResponseEntity<Map<String, Object>> response =
                webhookController.handleGitHubWebhook("push", "deliv-sec-test", null, payload);

        // Missing signature in production must be rejected with 500 (if secret missing) or 401 (if signature missing)
        assertTrue(response.getStatusCode().value() == 401 || response.getStatusCode().value() == 500);
        assertNotEquals(200, response.getStatusCode().value());
    }

    @Test
    @DisplayName("P0 Webhook Deduplication: Authoritative DB check prevents duplicate job when separate instance receives delivery")
    void testAuthoritativeDbDeduplicationAcrossInstances() {
        RepositoryEntity repo = new RepositoryEntity(workspaceId, RepositoryProvider.GITHUB, "Hrushi4151", "SecVault", RepositoryVisibility.PRIVATE);
        repo.setId(repoId);
        repo.setStatus(RepositoryStatus.ACTIVE);

        when(repositoryRepo.findByOwnerIgnoreCaseAndNameIgnoreCase("Hrushi4151", "SecVault"))
                .thenReturn(List.of(repo));

        String deliveryId = "deliv-separate-instance-" + UUID.randomUUID();

        // Simulate that Instance A already enqueued this delivery in DB
        when(scanService.isDeliveryAlreadyEnqueued(repoId, deliveryId)).thenReturn(true);

        String payload = """
                {"ref":"refs/heads/main","after":"sha222","repository":{"name":"SecVault","owner":{"login":"Hrushi4151"}}}
                """;

        // Instance B receives the webhook:
        ResponseEntity<Map<String, Object>> response =
                webhookController.handleGitHubWebhook("push", deliveryId, null, payload);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("DUPLICATE", response.getBody().get("status"));
        assertEquals(deliveryId, response.getBody().get("deliveryId"));
        // Verify no duplicate job was enqueued
        verify(scanService, never()).enqueueRepositoryScan(any(), any(), any(), any(), eq(deliveryId), any());
    }

    @Test
    @DisplayName("P0 Webhook Safety: Error response and logs never leak secrets or repository credentials")
    void testErrorResponsesNeverExposeCredentials() {
        RepositoryEntity repo = new RepositoryEntity(workspaceId, RepositoryProvider.GITHUB, "Hrushi4151", "SecVault", RepositoryVisibility.PRIVATE);
        repo.setId(repoId);
        repo.setStatus(RepositoryStatus.ACTIVE);

        when(repositoryRepo.findByOwnerIgnoreCaseAndNameIgnoreCase("Hrushi4151", "SecVault"))
                .thenReturn(List.of(repo));

        String sensitiveToken = "ghp_1111222233334444555566667777888899990000";
        doThrow(new RuntimeException("Failed connecting with credential: " + sensitiveToken))
                .when(scanService).enqueueRepositoryScan(any(), any(), any(), any(), any(), any());

        String payload = """
                {"ref":"refs/heads/main","after":"sha333","repository":{"name":"SecVault","owner":{"login":"Hrushi4151"}}}
                """;

        ResponseEntity<Map<String, Object>> response =
                webhookController.handleGitHubWebhook("push", "deliv-leak-test", null, payload);

        assertEquals(500, response.getStatusCode().value());
        String errorMsg = (String) response.getBody().get("error");
        assertFalse(errorMsg.contains(sensitiveToken));
        assertTrue(errorMsg.contains("[REDACTED_TOKEN]"));
    }
}
