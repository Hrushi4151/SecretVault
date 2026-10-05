package com.secretvault.ai.tool.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.tool.AiTool;
import com.secretvault.ai.tool.AiToolInvocationContext;
import com.secretvault.ai.tool.AiToolResult;
import com.secretvault.audit.entity.AuditLog;
import com.secretvault.audit.repository.AuditLogRepository;
import com.secretvault.security.event.entity.SecurityEvent;
import com.secretvault.security.event.repository.SecurityEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class IncidentTools {

    @Component
    public static class IncidentTimelineTool implements AiTool {
        private final AuditLogRepository auditLogRepository;
        private final SecurityEventRepository eventRepository;
        private final ObjectMapper objectMapper;

        public IncidentTimelineTool(
                @Autowired(required = false) AuditLogRepository auditLogRepository,
                @Autowired(required = false) SecurityEventRepository eventRepository,
                ObjectMapper objectMapper
        ) {
            this.auditLogRepository = auditLogRepository;
            this.eventRepository = eventRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "incident.timeline";
        }

        @Override
        public String getDescription() {
            return "Correlates audit trails, security events, sync errors, and rotation anomalies to build an incident investigation timeline.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "targetType", Map.of("type", "string", "description", "Resource type under investigation (e.g. ENVIRONMENT, SECRET, SYNC)"),
                            "targetId", Map.of("type", "string", "description", "Optional target ID or name")
                    )
            );
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                List<Map<String, Object>> correlatedTimeline = new ArrayList<>();

                if (auditLogRepository != null) {
                    List<AuditLog> auditLogs = auditLogRepository.findByWorkspaceIdOrderByCreatedAtDesc(context.workspaceId());
                    for (AuditLog l : auditLogs.stream().limit(20).toList()) {
                        correlatedTimeline.add(Map.of(
                                "source", "AUDIT",
                                "timestamp", l.getCreatedAt() != null ? l.getCreatedAt().toString() : "",
                                "action", l.getAction() != null ? l.getAction().name() : "UNKNOWN",
                                "status", l.getOutcome() != null ? l.getOutcome() : "SUCCESS",
                                "details", (l.getResourceType() != null ? l.getResourceType() : "RESOURCE") + ":" + (l.getResourceId() != null ? l.getResourceId().toString() : "")
                        ));
                    }
                }

                if (eventRepository != null) {
                    List<SecurityEvent> events = eventRepository.findByWorkspaceId(context.workspaceId(), PageRequest.of(0, 10)).getContent();
                    for (SecurityEvent e : events) {
                        correlatedTimeline.add(Map.of(
                                "source", "SECURITY_EVENT",
                                "timestamp", e.getTimestamp() != null ? e.getTimestamp().toString() : "",
                                "action", e.getEventType() != null ? e.getEventType().name() : "EVENT",
                                "status", e.getOutcome() != null ? e.getOutcome().name() : "INFO",
                                "details", "Severity: " + (e.getSeverity() != null ? e.getSeverity().name() : "INFO")
                        ));
                    }
                }

                correlatedTimeline.sort((a, b) -> Objects.toString(b.get("timestamp"), "").compareTo(Objects.toString(a.get("timestamp"), "")));

                Map<String, Object> result = Map.of(
                        "workspaceId", context.workspaceId().toString(),
                        "correlatedEventsCount", correlatedTimeline.size(),
                        "events", correlatedTimeline.stream().limit(25).toList(),
                        "investigationStatus", correlatedTimeline.isEmpty() ? "NO_INCIDENT_ACTIVITY" : "ACTIVE_TIMELINE_ASSEMBLED"
                );

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(result), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }
}
