package com.secretvault.ai.tool.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.tool.AiTool;
import com.secretvault.ai.tool.AiToolInvocationContext;
import com.secretvault.ai.tool.AiToolResult;
import com.secretvault.audit.entity.AuditLog;
import com.secretvault.audit.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DeploymentTools {

    @Component
    public static class DeploymentListTool implements AiTool {
        private final AuditLogRepository auditLogRepository;
        private final ObjectMapper objectMapper;

        public DeploymentListTool(@Autowired(required = false) AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
            this.auditLogRepository = auditLogRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "deployment.list";
        }

        @Override
        public String getDescription() {
            return "Lists recent deployment and sync release events within the authorized workspace.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "limit", Map.of("type", "integer", "description", "Max deployments to return (default: 15)")
                    )
            );
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                if (auditLogRepository == null) {
                    return AiToolResult.ok(getName(), "{\"deployments\":[]}", System.currentTimeMillis() - start);
                }

                int limit = 15;
                if (arguments.containsKey("limit") && arguments.get("limit") instanceof Number n) {
                    limit = Math.min(50, Math.max(1, n.intValue()));
                }

                List<AuditLog> logs = auditLogRepository.findByWorkspaceIdOrderByCreatedAtDesc(context.workspaceId());
                List<Map<String, Object>> deployments = logs.stream()
                        .filter(l -> l.getAction() != null && (l.getAction().name().contains("DEPLOY") || l.getAction().name().contains("SYNC") || l.getAction().name().contains("RELEASE") || l.getAction().name().contains("PROMOTION")))
                        .limit(limit)
                        .map(l -> Map.<String, Object>of(
                                "id", l.getId().toString(),
                                "action", l.getAction().name(),
                                "outcome", l.getOutcome() != null ? l.getOutcome() : "SUCCESS",
                                "actorId", l.getActorId() != null ? l.getActorId().toString() : "SYSTEM",
                                "resourceType", l.getResourceType() != null ? l.getResourceType() : "DEPLOYMENT",
                                "timestamp", l.getCreatedAt() != null ? l.getCreatedAt().toString() : ""
                        ))
                        .toList();

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(Map.of("totalDeployments", deployments.size(), "deployments", deployments)), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }
}
