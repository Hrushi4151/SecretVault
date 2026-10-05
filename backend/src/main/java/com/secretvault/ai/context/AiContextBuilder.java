package com.secretvault.ai.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.domain.model.AiIntentType;
import com.secretvault.ai.security.AiContextSanitizer;
import com.secretvault.security.finding.entity.SecurityFinding;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.security.finding.model.FindingStatus;
import com.secretvault.security.finding.repository.SecurityFindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Assembly layer for gathering typed, bounded, zero-plaintext metadata context
 * for AI Copilot reasoning and RCA.
 */
@Component
public class AiContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(AiContextBuilder.class);
    private static final int MAX_FINDINGS_IN_CONTEXT = 5;
    private static final int MAX_HINT_LENGTH = 1000;

    private final SecurityFindingRepository findingRepository;
    private final AiContextSanitizer sanitizer;
    private final ObjectMapper objectMapper;

    public AiContextBuilder(
            @Autowired(required = false) SecurityFindingRepository findingRepository,
            AiContextSanitizer sanitizer,
            ObjectMapper objectMapper
    ) {
        this.findingRepository = findingRepository;
        this.sanitizer = sanitizer;
        this.objectMapper = objectMapper;
    }

    public AiSafeContext buildSafeContext(
            UUID workspaceId,
            AiIntentType intent,
            String targetType,
            String targetId,
            String contextHint
    ) {
        long criticalCount = 0;
        long highCount = 0;
        long mediumCount = 0;
        long totalOpen = 0;
        List<String> topFindings = new ArrayList<>();
        String targetSummary = null;

        if (findingRepository != null && workspaceId != null) {
            try {
                List<FindingStatus> openStatuses = List.of(FindingStatus.OPEN, FindingStatus.ACKNOWLEDGED);
                criticalCount = findingRepository.countByWorkspaceIdAndSeverityAndStatusIn(workspaceId, FindingSeverity.CRITICAL, openStatuses);
                highCount = findingRepository.countByWorkspaceIdAndSeverityAndStatusIn(workspaceId, FindingSeverity.HIGH, openStatuses);
                mediumCount = findingRepository.countByWorkspaceIdAndSeverityAndStatusIn(workspaceId, FindingSeverity.MEDIUM, openStatuses);
                totalOpen = findingRepository.countByWorkspaceIdAndStatusIn(workspaceId, openStatuses);

                List<SecurityFinding> findingsList = findingRepository.findByWorkspaceIdAndStatusIn(workspaceId, openStatuses);
                if (findingsList != null) {
                    findingsList.stream()
                            .limit(MAX_FINDINGS_IN_CONTEXT)
                            .forEach(f -> {
                                String safeTitle = sanitizer.sanitizeText(f.getTitle());
                                String safeDesc = sanitizer.sanitizeText(f.getSafeDescription());
                                topFindings.add(String.format("[%s] %s: %s", f.getSeverity(), safeTitle, safeDesc));
                            });
                }

                // If target is a finding ID, lookup specifically
                if ("FINDING".equalsIgnoreCase(targetType) && targetId != null) {
                    try {
                        UUID findingUuid = UUID.fromString(targetId.trim());
                        findingRepository.findByIdAndWorkspaceId(findingUuid, workspaceId).ifPresent(f -> {
                            // Target summary metadata
                        });
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to collect security finding context for workspace {}: {}", workspaceId, e.getMessage());
            }
        }

        String sanitizedHint = "";
        if (contextHint != null && !contextHint.isBlank()) {
            sanitizedHint = sanitizer.sanitizeText(contextHint);
            if (sanitizedHint.length() > MAX_HINT_LENGTH) {
                sanitizedHint = sanitizedHint.substring(0, MAX_HINT_LENGTH) + "... [truncated]";
            }
        }

        String safeTargetType = targetType != null ? sanitizer.sanitizeText(targetType.trim().toUpperCase()) : null;
        String safeTargetId = targetId != null ? sanitizer.sanitizeText(targetId.trim()) : null;

        AiSafeContext context = new AiSafeContext(
                workspaceId,
                intent,
                criticalCount,
                highCount,
                mediumCount,
                totalOpen,
                topFindings,
                safeTargetType,
                safeTargetId,
                targetSummary,
                sanitizedHint
        );

        // Security Invariant Assertion
        try {
            String serialized = objectMapper.writeValueAsString(context);
            sanitizer.assertZeroPlaintext(serialized);
        } catch (Exception e) {
            if (e instanceof SecurityException se) {
                throw se;
            }
        }

        return context;
    }

    public String serializeContext(AiSafeContext context) {
        if (context == null) return "{}";
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            return "{\"workspaceId\":\"" + context.workspaceId() + "\",\"intent\":\"" + context.intent() + "\"}";
        }
    }
}
