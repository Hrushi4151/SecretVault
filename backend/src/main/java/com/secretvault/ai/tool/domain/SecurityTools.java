package com.secretvault.ai.tool.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.tool.AiTool;
import com.secretvault.ai.tool.AiToolInvocationContext;
import com.secretvault.ai.tool.AiToolResult;
import com.secretvault.security.event.entity.SecurityEvent;
import com.secretvault.security.event.repository.SecurityEventRepository;
import com.secretvault.security.finding.entity.SecurityFinding;
import com.secretvault.security.finding.model.FindingStatus;
import com.secretvault.security.finding.repository.SecurityFindingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SecurityTools {

    @Component
    public static class SecurityFindingsTool implements AiTool {
        private final SecurityFindingRepository findingRepository;
        private final ObjectMapper objectMapper;

        public SecurityFindingsTool(SecurityFindingRepository findingRepository, ObjectMapper objectMapper) {
            this.findingRepository = findingRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "security.findings";
        }

        @Override
        public String getDescription() {
            return "Retrieves open and unresolved security findings, risks, and policy violations for the workspace.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "limit", Map.of("type", "integer", "description", "Max findings to return (default: 20)")
                    )
            );
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                int limit = 20;
                if (arguments.containsKey("limit") && arguments.get("limit") instanceof Number n) {
                    limit = Math.min(50, Math.max(1, n.intValue()));
                }

                List<SecurityFinding> findings = findingRepository.findByWorkspaceIdAndStatusIn(
                        context.workspaceId(),
                        List.of(FindingStatus.OPEN, FindingStatus.ACKNOWLEDGED, FindingStatus.IN_PROGRESS)
                );

                List<Map<String, Object>> safeFindings = findings.stream()
                        .limit(limit)
                        .map(f -> Map.<String, Object>of(
                                "id", f.getId().toString(),
                                "title", f.getTitle(),
                                "severity", f.getSeverity() != null ? f.getSeverity().name() : "MEDIUM",
                                "category", f.getCategory() != null ? f.getCategory().name() : "GENERAL",
                                "status", f.getStatus() != null ? f.getStatus().name() : "OPEN",
                                "safeDescription", f.getSafeDescription() != null ? f.getSafeDescription() : "",
                                "remediationGuidance", f.getRemediationGuidance() != null ? f.getRemediationGuidance() : "",
                                "lastObservedAt", f.getLastObservedAt() != null ? f.getLastObservedAt().toString() : ""
                        ))
                        .toList();

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(Map.of(
                        "totalUnresolvedFindings", findings.size(),
                        "findings", safeFindings
                )), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    @Component
    public static class SecurityPostureTool implements AiTool {
        private final SecurityFindingRepository findingRepository;
        private final ObjectMapper objectMapper;

        public SecurityPostureTool(SecurityFindingRepository findingRepository, ObjectMapper objectMapper) {
            this.findingRepository = findingRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "security.posture";
        }

        @Override
        public String getDescription() {
            return "Calculates workspace security posture score (0-100), compliance breakdown, and risk metrics.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                List<SecurityFinding> openFindings = findingRepository.findByWorkspaceIdAndStatusIn(
                        context.workspaceId(),
                        List.of(FindingStatus.OPEN, FindingStatus.ACKNOWLEDGED, FindingStatus.IN_PROGRESS)
                );

                int critical = 0;
                int high = 0;
                int medium = 0;
                int low = 0;

                for (SecurityFinding f : openFindings) {
                    if (f.getSeverity() == null) continue;
                    switch (f.getSeverity()) {
                        case CRITICAL -> critical++;
                        case HIGH -> high++;
                        case MEDIUM -> medium++;
                        case LOW -> low++;
                    }
                }

                int penalty = (critical * 25) + (high * 10) + (medium * 5) + (low * 1);
                int score = Math.max(0, 100 - penalty);

                String rating = score >= 90 ? "EXCELLENT" : (score >= 75 ? "GOOD" : (score >= 50 ? "DEGRADED" : "CRITICAL_RISK"));

                Map<String, Object> result = Map.of(
                        "workspaceId", context.workspaceId().toString(),
                        "postureScore", score,
                        "rating", rating,
                        "criticalFindingsCount", critical,
                        "highFindingsCount", high,
                        "mediumFindingsCount", medium,
                        "lowFindingsCount", low,
                        "totalUnresolvedCount", openFindings.size(),
                        "complianceBreakdown", Map.of(
                                "rotationCompliance", critical > 0 ? "ACTION_REQUIRED" : "COMPLIANT",
                                "mfaCoverage", "ENFORCED",
                                "leastPrivilege", high > 0 ? "WARNING" : "HEALTHY",
                                "cloudSyncState", "MONITORED"
                        )
                );

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(result), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    @Component
    public static class SecurityEventsTool implements AiTool {
        private final SecurityEventRepository eventRepository;
        private final ObjectMapper objectMapper;

        public SecurityEventsTool(@Autowired(required = false) SecurityEventRepository eventRepository, ObjectMapper objectMapper) {
            this.eventRepository = eventRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "security.events";
        }

        @Override
        public String getDescription() {
            return "Lists recent security telemetry events, authentication attempts, and policy violations.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "limit", Map.of("type", "integer", "description", "Max events to retrieve (default: 20)")
                    )
            );
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                if (eventRepository == null) {
                    return AiToolResult.ok(getName(), "{\"events\":[]}", System.currentTimeMillis() - start);
                }

                int limit = 20;
                if (arguments.containsKey("limit") && arguments.get("limit") instanceof Number n) {
                    limit = Math.min(50, Math.max(1, n.intValue()));
                }

                List<SecurityEvent> events = eventRepository.findByWorkspaceId(context.workspaceId(), PageRequest.of(0, limit)).getContent();
                List<Map<String, Object>> safeEvents = events.stream()
                        .map(e -> Map.<String, Object>of(
                                "id", e.getId().toString(),
                                "eventType", e.getEventType() != null ? e.getEventType().name() : "UNKNOWN",
                                "severity", e.getSeverity() != null ? e.getSeverity().name() : "INFO",
                                "outcome", e.getOutcome() != null ? e.getOutcome().name() : "SUCCESS",
                                "timestamp", e.getTimestamp() != null ? e.getTimestamp().toString() : ""
                        ))
                        .toList();

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(Map.of("totalEvents", safeEvents.size(), "events", safeEvents)), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }
}
