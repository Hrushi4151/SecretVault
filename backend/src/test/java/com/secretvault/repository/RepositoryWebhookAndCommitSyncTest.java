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

        // Verify scanService.triggerRepositoryScan was called immediately with ScanType.FULL
        verify(scanService, times(1)).triggerRepositoryScan(
                eq(workspaceId),
                eq(repositoryId),
                eq(ScanType.FULL),
                eq("main"),
                isNull()
        );

        // Verify lastCommitSha was updated
        assertEquals("newcommit22222222222222222222222222222222222222", testRepo.getLastCommitSha());
        verify(repositoryRepo, atLeastOnce()).save(testRepo);
    }

    @Test
    @DisplayName("Dedicated repository webhook endpoint triggers immediate FULL scan")
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
                workspaceId, repositoryId, "push", payload
        );

        assertEquals(200, response.getStatusCode().value());
        verify(scanService, times(1)).triggerRepositoryScan(
                eq(workspaceId),
                eq(repositoryId),
                eq(ScanType.FULL),
                eq("feature-branch"),
                isNull()
        );
        assertEquals("commit33333333333333333333333333333333333333", testRepo.getLastCommitSha());
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
