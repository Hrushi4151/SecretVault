package com.secretvault.ai.tool.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.jit.entity.JitAccessRequest;
import com.secretvault.access.jit.repository.JitAccessRequestRepository;
import com.secretvault.ai.tool.AiTool;
import com.secretvault.ai.tool.AiToolInvocationContext;
import com.secretvault.ai.tool.AiToolResult;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class AccessTools {

    @Component
    public static class AccessUsersTool implements AiTool {
        private final WorkspaceMembershipRepository membershipRepository;
        private final ObjectMapper objectMapper;

        public AccessUsersTool(WorkspaceMembershipRepository membershipRepository, ObjectMapper objectMapper) {
            this.membershipRepository = membershipRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "access.users";
        }

        @Override
        public String getDescription() {
            return "Lists users, assigned standing roles, and access boundaries in the authorized workspace.";
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
                List<Map<String, Object>> userList = members.stream()
                        .map(m -> Map.<String, Object>of(
                                "userId", m.getUserId() != null ? m.getUserId().toString() : "unknown",
                                "role", m.getRole() != null ? m.getRole().name() : "VIEWER",
                                "joinedAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : ""
                        ))
                        .toList();

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(Map.of("totalUsers", userList.size(), "users", userList)), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    @Component
    public static class AccessRolesTool implements AiTool {
        private final ObjectMapper objectMapper;

        public AccessRolesTool(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "access.roles";
        }

        @Override
        public String getDescription() {
            return "Explains SecretVault standard RBAC roles, permission hierarchies, and standing vs dynamic privileges.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                Map<String, Object> rolesExplanation = Map.of(
                        "roles", List.of(
                                Map.of(
                                        "name", "OWNER",
                                        "description", "Full tenant administrative control across all projects and environments, member invites, billing, and KMS management."
                                ),
                                Map.of(
                                        "name", "ADMIN",
                                        "description", "Workspace administration, project/environment creation, provider synchronization, rotation scheduling, and security policy management."
                                ),
                                Map.of(
                                        "name", "DEVELOPER",
                                        "description", "Read/write access to non-production environments (DEV, STAGING), safe metadata read-only access to PROD, JIT escalation requests."
                                ),
                                Map.of(
                                        "name", "VIEWER",
                                        "description", "Read-only access to safe metadata, audit logs, and security posture across non-restricted environments."
                                )
                        )
                );

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(rolesExplanation), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    @Component
    public static class AccessJitTool implements AiTool {
        private final JitAccessRequestRepository jitRepository;
        private final ObjectMapper objectMapper;

        public AccessJitTool(JitAccessRequestRepository jitRepository, ObjectMapper objectMapper) {
            this.jitRepository = jitRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "access.jit";
        }

        @Override
        public String getDescription() {
            return "Lists active, pending, and historical Just-In-Time (JIT) access escalation requests and approvals.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "status", Map.of("type", "string", "description", "Optional status filter (PENDING, APPROVED, REJECTED, EXPIRED, REVOKED)")
                    )
            );
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                List<JitAccessRequest> requests = jitRepository.findByWorkspaceId(context.workspaceId());
                String statusFilter = arguments.containsKey("status") && arguments.get("status") != null ? arguments.get("status").toString().trim().toUpperCase() : null;

                List<Map<String, Object>> safeList = requests.stream()
                        .filter(r -> statusFilter == null || (r.getStatus() != null && r.getStatus().name().equalsIgnoreCase(statusFilter)))
                        .limit(25)
                        .map(r -> Map.<String, Object>of(
                                "id", r.getId().toString(),
                                "userId", r.getUserId().toString(),
                                "requestedPermission", r.getRequestedPermission() != null ? r.getRequestedPermission().name() : "",
                                "environmentId", r.getEnvironmentId() != null ? r.getEnvironmentId().toString() : "ALL",
                                "status", r.getStatus() != null ? r.getStatus().name() : "PENDING",
                                "reason", r.getReason() != null ? r.getReason() : "",
                                "expiresAt", r.getExpiresAt() != null ? r.getExpiresAt().toString() : "",
                                "createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : ""
                        ))
                        .toList();

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(Map.of("totalJitRequests", safeList.size(), "requests", safeList)), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }
}
