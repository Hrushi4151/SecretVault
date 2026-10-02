package com.secretvault.security.scheduler;

import com.secretvault.security.engine.SecurityIntelligenceEngine;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Background scheduler managing periodic and asynchronous Security Intelligence analysis.
 * Features per-workspace concurrency guards preventing duplicate overlapping scans.
 */
@Component
public class SecurityIntelligenceScheduler {

    private static final Logger log = LoggerFactory.getLogger(SecurityIntelligenceScheduler.class);

    private final SecurityIntelligenceEngine intelligenceEngine;
    private final WorkspaceRepository workspaceRepository;
    private final Set<UUID> runningWorkspaceScans = ConcurrentHashMap.newKeySet();

    public SecurityIntelligenceScheduler(
            SecurityIntelligenceEngine intelligenceEngine,
            WorkspaceRepository workspaceRepository
    ) {
        this.intelligenceEngine = intelligenceEngine;
        this.workspaceRepository = workspaceRepository;
    }

    /**
     * Periodic scheduled scan executing every hour (default: 0 0 * * * *).
     */
    @Scheduled(cron = "${secretvault.security.analysis-cron:0 0 * * * *}")
    public void runScheduledSecurityAnalysis() {
        log.info("Starting scheduled background security intelligence analysis sweep...");
        List<Workspace> workspaces = workspaceRepository.findAll();

        int completed = 0;
        int skipped = 0;

        for (Workspace workspace : workspaces) {
            UUID wsId = workspace.getId();
            if (!runningWorkspaceScans.add(wsId)) {
                log.debug("Skipping scheduled scan for workspace [{}] - scan already in progress", wsId);
                skipped++;
                continue;
            }

            try {
                intelligenceEngine.analyzeWorkspace(wsId, null);
                completed++;
            } catch (Exception e) {
                log.error("Scheduled security analysis failed for workspace [{}]: {}", wsId, e.getMessage(), e);
            } finally {
                runningWorkspaceScans.remove(wsId);
            }
        }

        log.info("Scheduled security analysis sweep completed: {} workspaces analyzed, {} skipped (in progress)",
                completed, skipped);
    }

    /**
     * Executes non-blocking on-demand asynchronous security analysis for a specific workspace.
     */
    @Async
    public CompletableFuture<SecurityIntelligenceEngine.SecurityAnalysisResult> triggerAsyncWorkspaceAnalysis(
            UUID workspaceId,
            UUID triggeredByUserId
    ) {
        if (!runningWorkspaceScans.add(workspaceId)) {
            log.warn("Async security analysis request rejected for workspace [{}] - scan already active", workspaceId);
            throw new IllegalStateException("Security analysis is already in progress for this workspace");
        }

        try {
            SecurityIntelligenceEngine.SecurityAnalysisResult result = intelligenceEngine.analyzeWorkspace(
                    workspaceId, triggeredByUserId
            );
            return CompletableFuture.completedFuture(result);
        } finally {
            runningWorkspaceScans.remove(workspaceId);
        }
    }

    public boolean isScanInProgress(UUID workspaceId) {
        return runningWorkspaceScans.contains(workspaceId);
    }
}
