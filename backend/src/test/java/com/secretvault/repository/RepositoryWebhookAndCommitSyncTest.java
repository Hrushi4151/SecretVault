package com.secretvault.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.repository.controller.GitHubRepositoryWebhookController;
import com.secretvault.repository.entity.RepositoryEntity;
import com.secretvault.repository.entity.RepositoryScan;
import com.secretvault.repository.model.RepositoryProvider;
import com.secretvault.repository.model.RepositoryStatus;
import com.secretvault.repository.model.RepositoryVisibility;
import com.secretvault.repository.model.ScanType;
import com.secretvault.repository.repository.RepositoryEntityRepository;
import com.secretvault.repository.service.RepositoryCommitSyncService;
import com.secretvault.repository.service.RepositoryScanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Repository Webhook & Commit-Triggered Immediate Scanning Tests")
class RepositoryWebhookAndCommitSyncTest {

    @Mock
    private RepositoryEntityRepository repositoryRepo;

    @Mock
    private RepositoryScanService scanService;

    private RepositoryCommitSyncService syncService;
    private GitHubRepositoryWebhookController webhookController;
    private ObjectMapper objectMapper;

    private UUID workspaceId;
    private UUID repositoryId;
    private RepositoryEntity testRepo;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        syncService = new RepositoryCommitSyncService(repositoryRepo, scanService, objectMapper);
        webhookController = new GitHubRepositoryWebhookController(repositoryRepo, scanService, syncService, objectMapper);

        workspaceId = UUID.randomUUID();
        repositoryId = UUID.randomUUID();

        testRepo = new RepositoryEntity();
        testRepo.setId(repositoryId);
        testRepo.setWorkspaceId(workspaceId);
        testRepo.setProvider(RepositoryProvider.GITHUB);
        testRepo.setOwner("Hrushi4151");
        testRepo.setName("Rally");
        testRepo.setDefaultBranch("main");
        testRepo.setStatus(RepositoryStatus.ACTIVE);
        testRepo.setVisibility(RepositoryVisibility.PUBLIC);
        testRepo.setLastCommitSha("oldcommit11111111111111111111111111111111111111");
    }

    @Test
    @DisplayName("GitHub push webhook immediately triggers FULL scan on matching repository")
    void testGitHubPushWebhookTriggersImmediateScan() {
        when(repositoryRepo.findByOwnerIgnoreCaseAndNameIgnoreCase("Hrushi4151", "Rally"))
                .thenReturn(List.of(testRepo));

        String pushPayload = """
                {
                    "ref": "refs/heads/main",
                    "after": "newcommit22222222222222222222222222222222222222",
                    "repository": {
                        "name": "Rally",
                        "owner": {
                            "login": "Hrushi4151"
                        },
                        "clone_url": "https://github.com/Hrushi4151/Rally.git"
                    },
                    "head_commit": {
                        "id": "newcommit22222222222222222222222222222222222222"
                    }
                }
                """;

        ResponseEntity<Map<String, Object>> response = webhookController.handleGitHubWebhook("push", null, pushPayload);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(Boolean.TRUE.equals(response.getBody().get("scansTriggered") != null));
        assertEquals(1, response.getBody().get("scansEnqueued"));

        // Verify scanService.enqueueRepositoryScan was called to durably queue the scan
        verify(scanService, times(1)).enqueueRepositoryScan(
                eq(workspaceId),
                eq(repositoryId),
                eq(ScanType.FULL),
                eq("main"),
                isNull(),
                isNull()
        );

        // Verify lastCommitSha was updated
        assertEquals("newcommit22222222222222222222222222222222222222", testRepo.getLastCommitSha());
        verify(repositoryRepo, atLeastOnce()).save(testRepo);
    }

    @Test
    @DisplayName("Dedicated repository webhook endpoint durably enqueues FULL scan")
    void testDedicatedRepositoryWebhookTriggersScan() {
        when(repositoryRepo.findByIdAndWorkspaceId(repositoryId, workspaceId))
                .thenReturn(Optional.of(testRepo));

        String payload = """
                {
                    "after": "commit33333333333333333333333333333333333333",
                    "ref": "refs/heads/feature-branch"
                }
                """;

        ResponseEntity<Map<String, Object>> response = webhookController.handleRepositorySpecificWebhook(
                workspaceId, repositoryId, "push", "deliv-spec-123", payload
        );

        assertEquals(200, response.getStatusCode().value());
        verify(scanService, times(1)).enqueueRepositoryScan(
                eq(workspaceId),
                eq(repositoryId),
                eq(ScanType.FULL),
                eq("feature-branch"),
                eq("deliv-spec-123"),
                isNull()
        );
        assertEquals("commit33333333333333333333333333333333333333", testRepo.getLastCommitSha());
    }

    @Test
    @DisplayName("Fast acknowledgement: Webhook accepts delivery and responds without blocking on scan execution")
    void testWebhookFastAcknowledgement() {
        when(repositoryRepo.findByOwnerIgnoreCaseAndNameIgnoreCase("Hrushi4151", "Rally"))
                .thenReturn(List.of(testRepo));

        String payload = """
                {
                    "ref": "refs/heads/main",
                    "after": "fastcommit1111111111111111111111111111111111",
                    "repository": {
                        "name": "Rally",
                        "owner": { "login": "Hrushi4151" }
                    }
                }
                """;

        long start = System.currentTimeMillis();
        ResponseEntity<Map<String, Object>> response =
                webhookController.handleGitHubWebhook("push", "fast-deliv-001", null, payload);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(200, response.getStatusCode().value());
        assertEquals("SUCCESS", response.getBody().get("status"));
        assertEquals("fast-deliv-001", response.getBody().get("deliveryId"));
        assertTrue(elapsed < 500, "Webhook acknowledgement took longer than expected: " + elapsed + "ms");

        verify(scanService, times(1)).enqueueRepositoryScan(
                eq(workspaceId), eq(repositoryId), eq(ScanType.FULL), eq("main"), eq("fast-deliv-001"), isNull()
        );
    }

    @Test
    @DisplayName("Duplicate deliveries: Same delivery ID is acknowledged as DUPLICATE without re-enqueue")
    void testWebhookDuplicateDeliveryDeduplication() {
        when(repositoryRepo.findByOwnerIgnoreCaseAndNameIgnoreCase("Hrushi4151", "Rally"))
                .thenReturn(List.of(testRepo));

        String payload = """
                {
                    "ref": "refs/heads/main",
                    "after": "dupcommit2222222222222222222222222222222222",
                    "repository": {
                        "name": "Rally",
                        "owner": { "login": "Hrushi4151" }
                    }
                }
                """;

        String deliveryId = "dup-deliv-" + UUID.randomUUID();

        // 1. First delivery -> accepted & enqueued
        ResponseEntity<Map<String, Object>> first =
                webhookController.handleGitHubWebhook("push", deliveryId, null, payload);
        assertEquals(200, first.getStatusCode().value());
        assertEquals("SUCCESS", first.getBody().get("status"));

        // 2. Duplicate delivery -> DUPLICATE status, scan not enqueued again
        ResponseEntity<Map<String, Object>> second =
                webhookController.handleGitHubWebhook("push", deliveryId, null, payload);
        assertEquals(200, second.getStatusCode().value());
        assertEquals("DUPLICATE", second.getBody().get("status"));
        assertEquals(deliveryId, second.getBody().get("deliveryId"));

        verify(scanService, times(1)).enqueueRepositoryScan(any(), any(), any(), any(), eq(deliveryId), isNull());
    }

    @Test
    @DisplayName("Enqueue failure & retry: Enqueue exceptions clear delivery cache allowing successful retry")
    void testWebhookEnqueueFailureAndRetry() {
        when(repositoryRepo.findByOwnerIgnoreCaseAndNameIgnoreCase("Hrushi4151", "Rally"))
                .thenReturn(List.of(testRepo));

        String payload = """
                {
                    "ref": "refs/heads/main",
                    "after": "retrycommit333333333333333333333333333333333",
                    "repository": {
                        "name": "Rally",
                        "owner": { "login": "Hrushi4151" }
                    }
                }
                """;

        String deliveryId = "retry-deliv-" + UUID.randomUUID();

        // Simulate database/enqueue failure on first attempt
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("DB Connection timeout"))
                .doAnswer(inv -> new RepositoryScan())
                .when(scanService).enqueueRepositoryScan(any(), any(), any(), any(), eq(deliveryId), isNull());

        // Attempt 1: Fails with 500 ERROR and clears delivery cache
        ResponseEntity<Map<String, Object>> first =
                webhookController.handleGitHubWebhook("push", deliveryId, null, payload);
        assertEquals(500, first.getStatusCode().value());
        assertEquals("ERROR", first.getBody().get("status"));

        // Attempt 2 (GitHub retry): Delivery cache cleared, retry succeeds with 200 SUCCESS
        ResponseEntity<Map<String, Object>> retry =
                webhookController.handleGitHubWebhook("push", deliveryId, null, payload);
        assertEquals(200, retry.getStatusCode().value());
        assertEquals("SUCCESS", retry.getBody().get("status"));

        verify(scanService, times(2)).enqueueRepositoryScan(any(), any(), any(), any(), eq(deliveryId), isNull());
    }

    @Test
    @DisplayName("On-demand sync endpoint triggers immediate FULL scan")
    void testOnDemandSyncEndpoint() {
        when(repositoryRepo.findByIdAndWorkspaceId(repositoryId, workspaceId))
                .thenReturn(Optional.of(testRepo));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = webhookController.syncAndScanRepository(workspaceId, repositoryId, null);

        assertEquals(200, response.getStatusCode().value());
        verify(scanService, atLeastOnce()).triggerRepositoryScan(
                eq(workspaceId),
                eq(repositoryId),
                eq(ScanType.FULL),
                any(),
                isNull()
        );
    }
}
