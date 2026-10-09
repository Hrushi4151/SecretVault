package com.secretvault.repository.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.common.exception.ApiException;
import com.secretvault.repository.entity.RepositoryEntity;
import com.secretvault.repository.model.ScanType;
import com.secretvault.repository.repository.RepositoryEntityRepository;
import com.secretvault.repository.service.RepositoryCommitSyncService;
import com.secretvault.repository.service.RepositoryScanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Inbound webhook controller for GitHub events (push, commit, pull_request).
 * Immediately executes a FULL secret leak scan when commits or code updates are pushed to GitHub.
 */
@RestController
@Tag(name = "Repository Webhooks", description = "Inbound webhooks for GitHub push events and commit triggers")
public class GitHubRepositoryWebhookController {

    private static final Logger log = LoggerFactory.getLogger(GitHubRepositoryWebhookController.class);

    private final RepositoryEntityRepository repositoryRepo;
    private final RepositoryScanService scanService;
    private final RepositoryCommitSyncService syncService;
    private final ObjectMapper objectMapper;

    public GitHubRepositoryWebhookController(
            RepositoryEntityRepository repositoryRepo,
            RepositoryScanService scanService,
            RepositoryCommitSyncService syncService,
            ObjectMapper objectMapper) {
        this.repositoryRepo = repositoryRepo;
        this.scanService = scanService;
        this.syncService = syncService;
        this.objectMapper = objectMapper;
    }

    /**
     * Primary GitHub Webhook Receiver:
     * Receives push/commit events from GitHub and triggers an immediate scan on the matched repository.
     */
    @PostMapping(value = "/api/v1/repositories/webhooks/github", consumes = "application/json")
    @Operation(summary = "Receive GitHub push and commit webhooks to trigger immediate scans")
    public ResponseEntity<Map<String, Object>> handleGitHubWebhook(
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "push") String gitHubEvent,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String payload
    ) {
        log.info("Received GitHub webhook event: {}", gitHubEvent);

        if ("ping".equalsIgnoreCase(gitHubEvent)) {
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "SecretVault Webhook Active (Pong)"));
        }

        try {
            JsonNode root = objectMapper.readTree(payload);

            // Extract repository information
            JsonNode repoNode = root.path("repository");
            String repoName = repoNode.path("name").asText("");
            String ownerName = repoNode.path("owner").path("login").asText(
                    repoNode.path("owner").path("name").asText("")
            );
            String cloneUrl = repoNode.path("clone_url").asText(repoNode.path("html_url").asText(""));
            String fullName = repoNode.path("full_name").asText("");

            if (ownerName.isBlank() && fullName.contains("/")) {
                String[] parts = fullName.split("/", 2);
                ownerName = parts[0];
                if (repoName.isBlank()) repoName = parts[1];
            }

            // Extract ref and commit SHA
            String ref = root.path("ref").asText("refs/heads/main");
            String branch = ref.replace("refs/heads/", "");
            String commitSha = root.path("after").asText(
                    root.path("head_commit").path("id").asText("")
            );

            // Find matching repository in SecretVault
            List<RepositoryEntity> matchingRepos = new ArrayList<>();
            if (!ownerName.isBlank() && !repoName.isBlank()) {
                matchingRepos.addAll(repositoryRepo.findByOwnerIgnoreCaseAndNameIgnoreCase(ownerName, repoName));
            }
            if (matchingRepos.isEmpty() && !cloneUrl.isBlank()) {
                matchingRepos.addAll(repositoryRepo.findByCloneUrl(cloneUrl));
            }

            if (matchingRepos.isEmpty()) {
                log.warn("GitHub webhook received for {}/{}, but no matching connected repository was found", ownerName, repoName);
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                        "status", "IGNORED",
                        "message", "Repository not registered in SecretVault: " + ownerName + "/" + repoName
                ));
            }

            int triggeredCount = 0;
            for (RepositoryEntity repo : matchingRepos) {
                if (!commitSha.isBlank()) {
                    repo.setLastCommitSha(commitSha);
                    repositoryRepo.save(repo);
                }

                log.info("GitHub commit detected for {}/{} (branch: {}, commit: {}). Triggering immediate FULL scan.",
                        repo.getOwner(), repo.getName(), branch, commitSha);

                scanService.triggerRepositoryScan(
                        repo.getWorkspaceId(),
                        repo.getId(),
                        ScanType.FULL,
                        branch.isBlank() ? repo.getDefaultBranch() : branch,
                        null
                );
                triggeredCount++;
            }

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "event", gitHubEvent,
                    "scansTriggered", triggeredCount,
                    "commitSha", commitSha,
                    "message", "Scan triggered immediately for commit " + commitSha
            ));
        } catch (Exception e) {
            log.error("Failed to process GitHub webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Dedicated webhook URL for a specific repository:
     * POST /api/v1/workspaces/{workspaceId}/repositories/{repositoryId}/webhook
     */
    @PostMapping("/api/v1/workspaces/{workspaceId}/repositories/{repositoryId}/webhook")
    @Operation(summary = "Trigger immediate scan for a specific repository via dedicated webhook")
    public ResponseEntity<Map<String, Object>> handleRepositorySpecificWebhook(
            @PathVariable UUID workspaceId,
            @PathVariable UUID repositoryId,
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "push") String gitHubEvent,
            @RequestBody(required = false) String payload
    ) {
        RepositoryEntity repo = repositoryRepo.findByIdAndWorkspaceId(repositoryId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Repository not found: " + repositoryId));

        String commitSha = "";
        String branch = repo.getDefaultBranch();

        if (payload != null && !payload.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(payload);
                commitSha = root.path("after").asText(root.path("head_commit").path("id").asText(""));
                String ref = root.path("ref").asText("");
                if (!ref.isBlank()) {
                    branch = ref.replace("refs/heads/", "");
                }
            } catch (Exception ignored) {}
        }

        if (!commitSha.isBlank()) {
            repo.setLastCommitSha(commitSha);
            repositoryRepo.save(repo);
        }

        log.info("Immediate scan triggered via dedicated webhook for repository {}/{} on branch {}",
                repo.getOwner(), repo.getName(), branch);

        scanService.triggerRepositoryScan(
                workspaceId,
                repositoryId,
                ScanType.FULL,
                branch,
                null
        );

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "repositoryId", repositoryId.toString(),
                "scanTriggered", true,
                "commitSha", commitSha,
                "message", "Scan triggered immediately"
        ));
    }

    /**
     * On-demand repository sync & commit check endpoint:
     * Checks if new commits exist on GitHub, updates the SHA, and triggers an immediate scan if changed or requested.
     */
    @PostMapping("/api/v1/workspaces/{workspaceId}/repositories/{repositoryId}/sync")
    @Operation(summary = "Check for new commits on GitHub and trigger an immediate scan")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncAndScanRepository(
            @PathVariable UUID workspaceId,
            @PathVariable UUID repositoryId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        RepositoryEntity repo = repositoryRepo.findByIdAndWorkspaceId(repositoryId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Repository not found: " + repositoryId));

        boolean updated = syncService.checkAndScanIfUpdated(repo, actorId);

        if (!updated) {
            // Force a full scan if no new commit was detected but user requested sync
            scanService.triggerRepositoryScan(
                    workspaceId,
                    repositoryId,
                    ScanType.FULL,
                    repo.getDefaultBranch(),
                    actorId
            );
        }

        return ResponseEntity.ok(ApiResponse.success(
                Map.of(
                        "repositoryId", repositoryId.toString(),
                        "newCommitDetected", updated,
                        "lastCommitSha", repo.getLastCommitSha() != null ? repo.getLastCommitSha() : "HEAD",
                        "scanTriggered", true
                ),
                updated ? "New commits detected! Immediate scan initiated." : "Repository synced and scan initiated."
        ));
    }
}
