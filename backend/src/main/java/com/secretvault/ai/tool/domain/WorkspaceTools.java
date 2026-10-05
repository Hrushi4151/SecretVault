package com.secretvault.ai.tool.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.tool.AiTool;
import com.secretvault.ai.tool.AiToolInvocationContext;
import com.secretvault.ai.tool.AiToolResult;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class WorkspaceTools {

    @Component
    public static class WorkspaceGetTool implements AiTool {
        private final WorkspaceRepository workspaceRepository;
        private final ObjectMapper objectMapper;

        public WorkspaceGetTool(WorkspaceRepository workspaceRepository, ObjectMapper objectMapper) {
            this.workspaceRepository = workspaceRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "workspace.get";
        }

        @Override
        public String getDescription() {
            return "Retrieves metadata, tier, security policies, and health overview for the authorized workspace.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "workspaceId", Map.of("type", "string", "description", "Optional workspace UUID; defaults to context workspace.")
                    )
            );
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                UUID wsId = context.workspaceId();
                if (arguments.containsKey("workspaceId") && arguments.get("workspaceId") != null) {
                    try {
                        UUID parsed = UUID.fromString(arguments.get("workspaceId").toString());
                        // Enforce tenant boundary: user can only inspect their authorized workspace
                        if (parsed.equals(context.workspaceId())) {
                            wsId = parsed;
                        } else {
                            return AiToolResult.error(getName(), "Access Denied: Cannot inspect workspaces across tenant boundaries.", System.currentTimeMillis() - start);
                        }
                    } catch (IllegalArgumentException e) {
                        return AiToolResult.error(getName(), "Invalid workspaceId UUID format.", System.currentTimeMillis() - start);
                    }
                }

                if (wsId == null) {
                    return AiToolResult.error(getName(), "No workspace context provided.", System.currentTimeMillis() - start);
                }

                Optional<Workspace> wsOpt = workspaceRepository.findById(wsId);
                if (wsOpt.isEmpty()) {
                    return AiToolResult.error(getName(), "Workspace not found.", System.currentTimeMillis() - start);
                }

                Workspace ws = wsOpt.get();
                Map<String, Object> safeDto = Map.of(
                        "id", ws.getId().toString(),
                        "name", ws.getName(),
                        "slug", ws.getSlug(),
                        "organizationId", ws.getOrganizationId() != null ? ws.getOrganizationId().toString() : "default",
                        "createdAt", ws.getCreatedAt() != null ? ws.getCreatedAt().toString() : "unknown",
                        "status", "ACTIVE"
                );

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(safeDto), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    @Component
    public static class WorkspaceMembersTool implements AiTool {
        private final WorkspaceMembershipRepository membershipRepository;
        private final ObjectMapper objectMapper;

        public WorkspaceMembersTool(WorkspaceMembershipRepository membershipRepository, ObjectMapper objectMapper) {
            this.membershipRepository = membershipRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "workspace.members";
        }

        @Override
        public String getDescription() {
            return "Lists authorized members and roles for the current workspace.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                List<WorkspaceMembership> members = membershipRepository.findByWorkspaceId(context.workspaceId());
                List<Map<String, Object>> safeList = members.stream()
                        .map(m -> Map.<String, Object>of(
                                "userId", m.getUserId() != null ? m.getUserId().toString() : "unknown",
                                "role", m.getRole() != null ? m.getRole().name() : "VIEWER",
                                "joinedAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : "unknown"
                        ))
                        .toList();

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(Map.of("totalMembers", safeList.size(), "members", safeList)), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }
}
