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
    private org.springframework.core.env.Environment env;

    // Delivery ID deduplication cache (bounded to 5,000 entries - in-memory optimization only)
    private final Set<String> processedDeliveries = Collections.synchronizedSet(
            Collections.newSetFromMap(new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > 5000;
                }
            })
    );

    @org.springframework.beans.factory.annotation.Autowired
    public GitHubRepositoryWebhookController(
            RepositoryEntityRepository repositoryRepo,
            RepositoryScanService scanService,
            RepositoryCommitSyncService syncService,
            ObjectMapper objectMapper,
            org.springframework.core.env.Environment env) {
        this.repositoryRepo = repositoryRepo;
        this.scanService = scanService;
        this.syncService = syncService;
        this.objectMapper = objectMapper;
        this.env = env;
    }

    public GitHubRepositoryWebhookController(
            RepositoryEntityRepository repositoryRepo,
            RepositoryScanService scanService,
            RepositoryCommitSyncService syncService,
            ObjectMapper objectMapper) {
        this(repositoryRepo, scanService, syncService, objectMapper, null);
    }

    public void setEnvironment(org.springframework.core.env.Environment env) {
        this.env = env;
    }

    private boolean isProductionEnvironment() {
        if (env != null && (env.acceptsProfiles(org.springframework.core.env.Profiles.of("prod", "production")))) {
            return true;
        }
        String appEnv = System.getenv("APP_ENV");
        if (appEnv != null && (appEnv.equalsIgnoreCase("prod") || appEnv.equalsIgnoreCase("production"))) {
            return true;
        }
        String springProfiles = System.getenv("SPRING_PROFILES_ACTIVE");
        return springProfiles != null && (springProfiles.contains("prod") || springProfiles.contains("production"));
    }

    public ResponseEntity<Map<String, Object>> handleGitHubWebhook(
            String gitHubEvent,
            String signature,
            String payload
    ) {
        return handleGitHubWebhook(gitHubEvent, null, signature, payload);
    }

    /**
     * Primary GitHub Webhook Receiver:
     * Receives push/commit events from GitHub, validates signature and delivery, and triggers an asynchronous scan.
     */
    @PostMapping(value = "/api/v1/repositories/webhooks/github", consumes = "application/json")
    @Operation(summary = "Receive GitHub push and commit webhooks to trigger immediate scans")
    public ResponseEntity<Map<String, Object>> handleGitHubWebhook(
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "push") String gitHubEvent,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String payload
    ) {
        log.info("Received GitHub webhook event: {}, delivery: {}", gitHubEvent, deliveryId);

        // Deduplicate repeated webhook deliveries (fast in-memory optimization only)
        if (deliveryId != null && !deliveryId.isBlank()) {
            if (processedDeliveries.contains(deliveryId)) {
                log.info("Duplicate GitHub webhook delivery ignored (in-memory cache): {}", deliveryId);
                return ResponseEntity.ok(Map.of(
                        "status", "DUPLICATE",
                        "deliveryId", deliveryId,
                        "message", "Webhook delivery already processed"
                ));
            }
        }

        // HMAC-SHA256 signature verification
        String webhookSecret = System.getenv("GITHUB_WEBHOOK_SECRET");
        boolean isProd = isProductionEnvironment();

        if (isProd) {
            // Production MUST fail closed if secret is missing or signature is invalid
            if (webhookSecret == null || webhookSecret.isBlank()) {
                log.error("Production security alert: GITHUB_WEBHOOK_SECRET is not configured. Failing closed.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                        "status", "CONFIGURATION_ERROR",
                        "error", "Webhook signature verification misconfigured in production"
                ));
            }
            if (signature == null || signature.isBlank()) {
                log.warn("Missing required X-Hub-Signature-256 header in production request for delivery {}", deliveryId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "status", "UNAUTHORIZED",
                        "error", "Missing required X-Hub-Signature-256 header"
                ));
            }
            if (!isValidHmacSignature(payload, signature, webhookSecret)) {
                log.warn("Invalid GitHub webhook HMAC-SHA256 signature in production for delivery {}", deliveryId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "status", "UNAUTHORIZED",
                        "error", "Invalid webhook HMAC signature"
                ));
            }
        } else {
            // Non-production (dev/test): Validate if secret or signature is configured
            if (webhookSecret != null && !webhookSecret.isBlank() && signature != null && !signature.isBlank()) {
                if (!isValidHmacSignature(payload, signature, webhookSecret)) {
                    log.warn("Invalid GitHub webhook HMAC-SHA256 signature for delivery {}", deliveryId);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                            "status", "UNAUTHORIZED",
                            "error", "Invalid webhook HMAC signature"
                    ));
                }
            } else if (webhookSecret != null && !webhookSecret.isBlank() && (signature == null || signature.isBlank())) {
                log.warn("Missing required X-Hub-Signature-256 header for delivery {}", deliveryId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "status", "UNAUTHORIZED",
                        "error", "Missing required X-Hub-Signature-256 header"
                ));
            }
        }

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
            boolean allDuplicates = true;
            boolean anyActiveRepo = false;

            for (RepositoryEntity repo : matchingRepos) {
                // Check if monitoring is paused or disabled
                if (repo.getStatus() == com.secretvault.repository.model.RepositoryStatus.DISABLED) {
                    log.info("Skipping scan for repository {}/{}: monitoring is DISABLED", repo.getOwner(), repo.getName());
                    continue;
                }

                anyActiveRepo = true;

                // Authoritative database-level deduplication check
                if (deliveryId != null && !deliveryId.isBlank() && scanService.isDeliveryAlreadyEnqueued(repo.getId(), deliveryId)) {
                    log.info("Duplicate delivery {} for repository {}/{} detected in database. Skipping duplicate enqueue.",
                            deliveryId, repo.getOwner(), repo.getName());
                    continue;
                }

                allDuplicates = false;

                if (!commitSha.isBlank()) {
                    repo.setLastCommitSha(commitSha);
                    repositoryRepo.save(repo);
                }

                log.info("GitHub commit detected for {}/{} (branch: {}, commit: {}). Durably enqueuing FULL scan.",
                        repo.getOwner(), repo.getName(), branch, commitSha);

                final String scanBranch = branch.isBlank() ? repo.getDefaultBranch() : branch;
                scanService.enqueueRepositoryScan(
                        repo.getWorkspaceId(),
                        repo.getId(),
                        ScanType.FULL,
                        scanBranch,
                        deliveryId,
                        null
                );

                triggeredCount++;
            }

            if (anyActiveRepo && allDuplicates && deliveryId != null && !deliveryId.isBlank()) {
                processedDeliveries.add(deliveryId);
                return ResponseEntity.ok(Map.of(
                        "status", "DUPLICATE",
                        "deliveryId", deliveryId,
                        "message", "Webhook delivery already processed"
                ));
            }

            if (deliveryId != null && !deliveryId.isBlank()) {
                processedDeliveries.add(deliveryId);
            }

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "event", gitHubEvent,
                    "scansEnqueued", triggeredCount,
                    "scansTriggered", triggeredCount,
                    "deliveryId", deliveryId != null ? deliveryId : "",
                    "commitSha", commitSha,
                    "message", "Scan durably queued for commit " + commitSha
            ));
        } catch (Exception e) {
            if (deliveryId != null) {
                processedDeliveries.remove(deliveryId);
            }
            String sanitizedError = com.secretvault.events.dispatcher.EventDispatcher.sanitizeErrorMessage(e.getMessage());
            log.error("Failed to process GitHub webhook delivery {}: {}", deliveryId, sanitizedError);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "ERROR",
                    "deliveryId", deliveryId != null ? deliveryId : "",
                    "error", sanitizedError != null ? sanitizedError : "Webhook processing error"
            ));
        }
    }

    private boolean isValidHmacSignature(String payload, String signature, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("sha256=");
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            String expected = sb.toString();
            return java.security.MessageDigest.isEqual(
                    expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    signature.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Error computing HMAC signature: {}", e.getMessage());
            return false;
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
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
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

        if (deliveryId != null && !deliveryId.isBlank() && scanService.isDeliveryAlreadyEnqueued(repositoryId, deliveryId)) {
            log.info("Duplicate delivery {} for repository {} detected via dedicated webhook", deliveryId, repositoryId);
            return ResponseEntity.ok(Map.of(
                    "status", "DUPLICATE",
                    "repositoryId", repositoryId.toString(),
                    "deliveryId", deliveryId,
                    "message", "Webhook delivery already processed"
            ));
        }

        log.info("Durable scan enqueued via dedicated webhook for repository {}/{} on branch {}",
                repo.getOwner(), repo.getName(), branch);

        scanService.enqueueRepositoryScan(
                workspaceId,
                repositoryId,
                ScanType.FULL,
                branch,
                deliveryId,
                null
        );

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "repositoryId", repositoryId.toString(),
                "scanQueued", true,
                "scanTriggered", true,
                "deliveryId", deliveryId != null ? deliveryId : "",
                "commitSha", commitSha,
                "message", "Scan durably queued"
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
