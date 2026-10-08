package com.secretvault.repository.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.repository.entity.RepositoryEntity;
import com.secretvault.repository.model.RepositoryStatus;
import com.secretvault.repository.model.ScanType;
import com.secretvault.repository.repository.RepositoryEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Service that monitors connected repositories for new commits or code updates.
 * When a new commit SHA or change is detected, it immediately triggers a FULL secret leak scan.
 */
@Service
public class RepositoryCommitSyncService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryCommitSyncService.class);

    private final RepositoryEntityRepository repositoryRepo;
    private final RepositoryScanService scanService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public RepositoryCommitSyncService(
            RepositoryEntityRepository repositoryRepo,
            @Lazy RepositoryScanService scanService,
            ObjectMapper objectMapper) {
        this.repositoryRepo = repositoryRepo;
        this.scanService = scanService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Periodic background check (every 60 seconds) that checks if any connected repository
     * has new commits pushed to GitHub/remote and triggers an immediate scan when updated.
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 15000)
    public void monitorActiveRepositoriesForNewCommits() {
        try {
            List<RepositoryEntity> activeRepos = repositoryRepo.findByStatus(RepositoryStatus.ACTIVE);
            for (RepositoryEntity repo : activeRepos) {
                try {
                    checkAndScanIfUpdated(repo, null);
                } catch (Exception e) {
                    log.debug("Commit check skipped for {}/{}: {}", repo.getOwner(), repo.getName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Error during scheduled repository commit monitor: {}", e.getMessage());
        }
    }

    /**
     * Synchronizes a specific repository and triggers a scan if new commits were pushed or if requested.
     */
    @Transactional
    public boolean syncRepository(UUID workspaceId, UUID repositoryId, UUID actorId) {
        RepositoryEntity repo = repositoryRepo.findByIdAndWorkspaceId(repositoryId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        return checkAndScanIfUpdated(repo, actorId);
    }

    /**
     * Checks if the remote repository has a newer commit SHA than what is recorded locally.
     * If updated (or if lastCommitSha is null), updates the SHA and triggers an immediate FULL scan.
     */
    @Transactional
    public boolean checkAndScanIfUpdated(RepositoryEntity repo, UUID actorId) {
        String remoteCommitSha = fetchLatestRemoteCommitSha(repo);
        if (remoteCommitSha == null || remoteCommitSha.isBlank()) {
            return false;
        }

        String localSha = repo.getLastCommitSha();
        boolean isNewCommit = (localSha == null || !localSha.equalsIgnoreCase(remoteCommitSha));

        if (isNewCommit) {
            log.info("New commit detected on repository {}/{} (new: {}, previous: {}). Triggering immediate FULL scan.",
                    repo.getOwner(), repo.getName(), remoteCommitSha, localSha);

            repo.setLastCommitSha(remoteCommitSha);
            repositoryRepo.save(repo);

            if (scanService != null) {
                scanService.triggerRepositoryScan(
                        repo.getWorkspaceId(),
                        repo.getId(),
                        ScanType.FULL,
                        repo.getDefaultBranch(),
                        actorId
                );
            }
            return true;
        }

        return false;
    }

    /**
     * Fetches the latest commit SHA for the repository's default branch.
     */
    public String fetchLatestRemoteCommitSha(RepositoryEntity repo) {
        if (repo.getOwner() == null || repo.getName() == null) {
            return null;
        }

        try {
            String branch = (repo.getDefaultBranch() != null && !repo.getDefaultBranch().isBlank())
                    ? repo.getDefaultBranch().trim()
                    : "main";

            String apiUrl = "https://api.github.com/repos/" + repo.getOwner() + "/" + repo.getName() + "/commits/" + branch;

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "SecretVault-CommitMonitor/1.0")
                    .GET()
                    .timeout(Duration.ofSeconds(15));

            String token = System.getenv("GITHUB_TOKEN");
            if (token == null || token.isBlank()) {
                token = System.getProperty("GITHUB_TOKEN");
            }
            if (token != null && !token.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + token.trim());
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("sha")) {
                    return root.get("sha").asText();
                }
            } else if (response.statusCode() == 404 && !"master".equalsIgnoreCase(branch)) {
                // Retry with master branch if main was not found
                String masterUrl = "https://api.github.com/repos/" + repo.getOwner() + "/" + repo.getName() + "/commits/master";
                HttpRequest.Builder masterReq = HttpRequest.newBuilder()
                        .uri(URI.create(masterUrl))
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "SecretVault-CommitMonitor/1.0")
                        .GET()
                        .timeout(Duration.ofSeconds(15));

                if (token != null && !token.isBlank()) {
                    masterReq.header("Authorization", "Bearer " + token.trim());
                }

                HttpResponse<String> masterResp = httpClient.send(masterReq.build(), HttpResponse.BodyHandlers.ofString());
                if (masterResp.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(masterResp.body());
                    if (root.has("sha")) {
                        return root.get("sha").asText();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to query remote commit for {}/{}: {}", repo.getOwner(), repo.getName(), e.getMessage());
        }

        return null;
    }
}
