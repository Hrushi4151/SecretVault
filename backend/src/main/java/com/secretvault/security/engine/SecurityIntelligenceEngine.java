package com.secretvault.security.engine;

import com.secretvault.security.event.model.SecurityEventOutcome;
import com.secretvault.security.event.model.SecurityEventSeverity;
import com.secretvault.security.event.model.SecurityEventType;
import com.secretvault.security.event.service.SecurityEventService;
import com.secretvault.security.finding.dto.SecurityFindingDraft;
import com.secretvault.security.finding.entity.SecurityFinding;
import com.secretvault.security.finding.service.SecurityFindingService;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SecurityIntelligenceEngine {

    private static final Logger log = LoggerFactory.getLogger(SecurityIntelligenceEngine.class);

    private final List<SecurityDetectionRule> detectionRules;
    private final SecurityFindingService findingService;
    private final WorkspaceRepository workspaceRepository;
    private final SecurityEventService eventService;

    public SecurityIntelligenceEngine(
            List<SecurityDetectionRule> detectionRules,
            SecurityFindingService findingService,
            WorkspaceRepository workspaceRepository,
            SecurityEventService eventService
    ) {
        this.detectionRules = detectionRules != null ? detectionRules : List.of();
        this.findingService = findingService;
        this.workspaceRepository = workspaceRepository;
        this.eventService = eventService;
    }

    /**
     * Executes full deterministic security intelligence scan across a workspace.
     */
    @Transactional
    public SecurityAnalysisResult analyzeWorkspace(UUID workspaceId, UUID triggeredByUserId) {
        long startMs = System.currentTimeMillis();
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        Instant evaluationTime = Instant.now();
        List<SecurityFindingDraft> drafts = new ArrayList<>();

        for (SecurityDetectionRule rule : detectionRules) {
            try {
                List<SecurityFindingDraft> ruleFindings = rule.evaluate(workspace, evaluationTime);
                if (ruleFindings != null) {
                    drafts.addAll(ruleFindings);
                }
            } catch (Exception e) {
                log.error("Security detection rule [{}] failed for workspace [{}]: {}",
                        rule.getRuleName(), workspaceId, e.getMessage(), e);
            }
        }

        int upsertedCount = 0;
        for (SecurityFindingDraft draft : drafts) {
            findingService.upsertFinding(draft);
            upsertedCount++;
        }

        long durationMs = System.currentTimeMillis() - startMs;
        log.info("Completed security analysis for workspace [{}] in {}ms: {} rules executed, {} findings evaluated/upserted",
                workspaceId, durationMs, detectionRules.size(), upsertedCount);

        eventService.recordEvent(
                workspaceId,
                null,
                null,
                triggeredByUserId,
                SecurityEventType.SECURITY_ANALYSIS_EXECUTED,
                SecurityEventSeverity.INFO,
                SecurityEventOutcome.SUCCESS,
                "SECURITY_ENGINE",
                null, null, null,
                Map.of(
                        "rulesExecuted", detectionRules.size(),
                        "findingsEvaluated", drafts.size(),
                        "durationMs", durationMs
                )
        );

        return new SecurityAnalysisResult(
                workspaceId,
                detectionRules.size(),
                drafts.size(),
                durationMs,
                evaluationTime
        );
    }

    public record SecurityAnalysisResult(
            UUID workspaceId,
            int rulesExecuted,
            int findingsEvaluated,
            long durationMs,
            Instant evaluatedAt
    ) {}
}
