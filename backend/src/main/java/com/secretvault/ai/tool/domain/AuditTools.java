package com.secretvault.ai.tool.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.tool.AiTool;
import com.secretvault.ai.tool.AiToolInvocationContext;
import com.secretvault.ai.tool.AiToolResult;
import com.secretvault.audit.entity.AuditLog;
import com.secretvault.audit.repository.AuditLogRepository;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class AuditTools {

    @Component
    public static class AuditSearchTool implements AiTool {
        private final AuditLogRepository auditLogRepository;
        private final ObjectMapper objectMapper;

        public AuditSearchTool(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
            this.auditLogRepository = auditLogRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "audit.search";
        }

        @Override
        public String getDescription() {
            return "Searches immutable audit logs within the authorized workspace for specific actions, resources, or actor events.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "action", Map.of("type", "string", "description", "Optional action filter (e.g. SECRET_READ, ROTATION_JOB_SUCCESS)"),
                            "resourceType", Map.of("type", "string", "description", "Optional resourceType filter (e.g. SECRET, ENVIRONMENT)"),
                            "limit", Map.of("type", "integer", "description", "Max entries (default: 20)")
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

                String actionFilter = arguments.containsKey("action") && arguments.get("action") != null ? arguments.get("action").toString().trim().toUpperCase() : null;
                String typeFilter = arguments.containsKey("resourceType") && arguments.get("resourceType") != null ? arguments.get("resourceType").toString().trim().toUpperCase() : null;

                List<AuditLog> logs = auditLogRepository.findByWorkspaceIdOrderByCreatedAtDesc(context.workspaceId());

                List<Map<String, Object>> safeLogs = logs.stream()
                        .filter(l -> actionFilter == null || (l.getAction() != null && l.getAction().name().contains(actionFilter)))
                        .filter(l -> typeFilter == null || (l.getResourceType() != null && l.getResourceType().equalsIgnoreCase(typeFilter)))
                        .limit(limit)
                        .map(l -> Map.<String, Object>of(
                                "id", l.getId().toString(),
                                "action", l.getAction() != null ? l.getAction().name() : "UNKNOWN",
                                "resourceType", l.getResourceType() != null ? l.getResourceType() : "RESOURCE",
                                "resourceId", l.getResourceId() != null ? l.getResourceId().toString() : "",
                                "actorId", l.getActorId() != null ? l.getActorId().toString() : "SYSTEM",
                                "outcome", l.getOutcome() != null ? l.getOutcome() : "SUCCESS",
                                "timestamp", l.getCreatedAt() != null ? l.getCreatedAt().toString() : ""
                        ))
                        .toList();

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(Map.of("totalMatched", safeLogs.size(), "auditLogs", safeLogs)), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    @Component
    public static class AuditTimelineTool implements AiTool {
        private final AuditLogRepository auditLogRepository;
        private final ObjectMapper objectMapper;

        public AuditTimelineTool(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
            this.auditLogRepository = auditLogRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "audit.timeline";
        }

        @Override
        public String getDescription() {
            return "Builds a chronological audit timeline of changes and operations performed across the workspace or on a specific resource.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "resourceId", Map.of("type", "string", "description", "Optional resource UUID to filter timeline"),
                            "resourceType", Map.of("type", "string", "description", "Optional resource type (e.g. SECRET, DEPLOYMENT)")
                    )
            );
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                List<AuditLog> logs;

                if (arguments.containsKey("resourceId") && arguments.get("resourceId") != null && arguments.containsKey("resourceType") && arguments.get("resourceType") != null) {
                    try {
                        UUID rId = UUID.fromString(arguments.get("resourceId").toString());
                        String rType = arguments.get("resourceType").toString().toUpperCase();
                        logs = auditLogRepository.findByResourceTypeAndResourceIdOrderByCreatedAtDesc(rType, rId);
                        // Filter by workspace boundary
                        logs = logs.stream().filter(l -> context.workspaceId().equals(l.getWorkspaceId())).toList();
                    } catch (IllegalArgumentException e) {
                        return AiToolResult.error(getName(), "Invalid resourceId UUID format.", System.currentTimeMillis() - start);
                    }
                } else {
                    logs = auditLogRepository.findByWorkspaceIdOrderByCreatedAtDesc(context.workspaceId());
                }

                List<Map<String, Object>> timeline = logs.stream()
                        .limit(30)
                        .map(l -> Map.<String, Object>of(
                                "timestamp", l.getCreatedAt() != null ? l.getCreatedAt().toString() : "",
                                "action", l.getAction() != null ? l.getAction().name() : "UNKNOWN",
                                "actor", l.getActorId() != null ? l.getActorId().toString() : "SYSTEM",
                                "target", (l.getResourceType() != null ? l.getResourceType() : "RESOURCE") + ":" + (l.getResourceId() != null ? l.getResourceId().toString() : ""),
                                "outcome", l.getOutcome() != null ? l.getOutcome() : "SUCCESS"
                        ))
                        .toList();

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(Map.of("timelineEventsCount", timeline.size(), "timeline", timeline)), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }
}
