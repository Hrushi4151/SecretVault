package com.secretvault.ai.tool.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.jit.entity.JitAccessRequest;
import com.secretvault.access.jit.repository.JitAccessRequestRepository;
import com.secretvault.ai.tool.AiTool;
import com.secretvault.ai.tool.AiToolInvocationContext;
import com.secretvault.ai.tool.AiToolResult;
import com.secretvault.audit.entity.AuditLog;
import com.secretvault.audit.repository.AuditLogRepository;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.security.finding.entity.SecurityFinding;
import com.secretvault.security.finding.model.FindingStatus;
import com.secretvault.security.finding.repository.SecurityFindingRepository;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.invitation.dto.UserInvitationItemResponse;
import com.secretvault.workspace.invitation.dto.UserInvitationsListResponse;
import com.secretvault.workspace.invitation.service.WorkspaceInvitationService;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Domain AI tools for authenticated user-state intelligence, pending invitations,
 * notifications, user activity, and access boundaries.
 */
@Component
public class UserTools {

    @Component
    public static class UserGetCurrentTool implements AiTool {
        private final UserRepository userRepository;
        private final WorkspaceMembershipRepository membershipRepository;
        private final WorkspaceRepository workspaceRepository;
        private final ObjectMapper objectMapper;

        public UserGetCurrentTool(
                UserRepository userRepository,
                WorkspaceMembershipRepository membershipRepository,
                WorkspaceRepository workspaceRepository,
                ObjectMapper objectMapper
        ) {
            this.userRepository = userRepository;
            this.membershipRepository = membershipRepository;
            this.workspaceRepository = workspaceRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "user.getCurrent";
        }

        @Override
        public String getDescription() {
            return "Retrieves the currently authenticated user's identity, email, full name, active workspace role, and authorized memberships.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                if (context.userId() == null) {
                    return AiToolResult.error(getName(), "No authenticated user in context.", System.currentTimeMillis() - start);
                }

                Optional<User> userOpt = userRepository.findById(context.userId());
                if (userOpt.isEmpty()) {
                    return AiToolResult.error(getName(), "User record not found.", System.currentTimeMillis() - start);
                }

                User user = userOpt.get();
                List<WorkspaceMembership> memberships = membershipRepository.findByUserId(user.getId());

                List<Map<String, Object>> membershipSummaries = new ArrayList<>();
                for (WorkspaceMembership m : memberships) {
                    Optional<Workspace> ws = workspaceRepository.findById(m.getWorkspaceId());
                    membershipSummaries.add(Map.of(
                            "workspaceId", m.getWorkspaceId().toString(),
                            "workspaceName", ws.map(Workspace::getName).orElse("Workspace"),
                            "role", m.getRole() != null ? m.getRole().name() : "VIEWER",
                            "isActiveWorkspace", m.getWorkspaceId().equals(context.workspaceId())
                    ));
                }

                Map<String, Object> response = Map.of(
                        "userId", user.getId().toString(),
                        "email", user.getEmail(),
                        "fullName", user.getFullName() != null ? user.getFullName() : "",
                        "standingRole", context.userRole() != null ? context.userRole() : "DEVELOPER",
                        "activeWorkspaceId", context.workspaceId() != null ? context.workspaceId().toString() : "",
                        "isMfaEnabled", user.isMfaEnabled(),
                        "totalMemberships", memberships.size(),
                        "memberships", membershipSummaries
                );

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(response), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    @Component
    public static class UserInvitationsTool implements AiTool {
        private final WorkspaceInvitationService invitationService;
        private final ObjectMapper objectMapper;

        public UserInvitationsTool(WorkspaceInvitationService invitationService, ObjectMapper objectMapper) {
            this.invitationService = invitationService;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "user.invitations";
        }

        @Override
        public String getDescription() {
            return "Retrieves all real active pending workspace invitations issued to the authenticated user's registered email.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                if (context.userId() == null) {
                    return AiToolResult.error(getName(), "No authenticated user in context.", System.currentTimeMillis() - start);
                }

                UserInvitationsListResponse invitationsList = invitationService.getMyPendingInvitations(context.userId());
                List<UserInvitationItemResponse> items = invitationsList != null && invitationsList.items() != null
                        ? invitationsList.items()
                        : List.of();

                List<Map<String, Object>> safeItems = items.stream().map(inv -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("invitationId", inv.id().toString());
                    map.put("workspaceId", inv.workspaceId().toString());
                    map.put("workspaceName", inv.workspaceName());
                    map.put("role", inv.role() != null ? inv.role().name() : "MEMBER");
                    map.put("status", inv.status() != null ? inv.status().name() : "PENDING");
                    map.put("expiresAt", inv.expiresAt() != null ? inv.expiresAt().toString() : "");
                    map.put("createdAt", inv.createdAt() != null ? inv.createdAt().toString() : "");
                    map.put("route", "/workspaces/invitations");
                    map.put("navigationAction", "OPEN_INVITATION");
                    if (inv.invitedBy() != null) {
                        map.put("invitedByName", inv.invitedBy().name());
                        map.put("invitedByEmail", inv.invitedBy().email());
                    } else {
                        map.put("invitedByName", "Administrator");
                    }
                    return map;
                }).toList();

                Map<String, Object> result = Map.of(
                        "totalPendingInvitations", safeItems.size(),
                        "invitations", safeItems
                );

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(result), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    @Component
    public static class InvitationListTool implements AiTool {
        private final UserInvitationsTool userInvitationsTool;

        public InvitationListTool(UserInvitationsTool userInvitationsTool) {
            this.userInvitationsTool = userInvitationsTool;
        }

        @Override
        public String getName() {
            return "invitation.list";
        }

        @Override
        public String getDescription() {
            return "Lists pending workspace invitations for the authenticated user.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return userInvitationsTool.getParameterSchema();
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            return userInvitationsTool.execute(context, arguments);
        }
    }

    @Component
    public static class UserNotificationsTool implements AiTool {
        private final WorkspaceInvitationService invitationService;
        private final SecurityFindingRepository findingRepository;
        private final ObjectMapper objectMapper;

        public UserNotificationsTool(
                WorkspaceInvitationService invitationService,
                SecurityFindingRepository findingRepository,
                ObjectMapper objectMapper
        ) {
            this.invitationService = invitationService;
            this.findingRepository = findingRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "user.notifications";
        }

        @Override
        public String getDescription() {
            return "Lists unread notifications, pending workspace invitations, and urgent security notifications for the user.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                UserInvitationsListResponse invites = invitationService.getMyPendingInvitations(context.userId());
                int inviteCount = (invites != null && invites.items() != null) ? invites.items().size() : 0;

                long openFindingsCount = 0;
                if (context.workspaceId() != null) {
                    openFindingsCount = findingRepository.countByWorkspaceIdAndStatusIn(
                            context.workspaceId(),
                            List.of(FindingStatus.OPEN, FindingStatus.ACKNOWLEDGED, FindingStatus.IN_PROGRESS)
                    );
                }

                List<Map<String, Object>> alerts = new ArrayList<>();
                if (inviteCount > 0) {
                    alerts.add(Map.of(
                            "type", "INVITATION",
                            "severity", "INFO",
                            "title", "Pending Workspace Invitations",
                            "message", "You have " + inviteCount + " pending workspace invitation(s) awaiting response.",
                            "route", "/workspaces/invitations"
                    ));
                }

                if (openFindingsCount > 0) {
                    alerts.add(Map.of(
                            "type", "SECURITY",
                            "severity", "HIGH",
                            "title", "Open Security Findings",
                            "message", openFindingsCount + " active security finding(s) detected in current workspace.",
                            "route", "/repo-security"
                    ));
                }

                Map<String, Object> result = Map.of(
                        "totalNotifications", alerts.size(),
                        "pendingInvitationsCount", inviteCount,
                        "openSecurityFindingsCount", openFindingsCount,
                        "notifications", alerts
                );

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(result), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    @Component
    public static class UserActivityTool implements AiTool {
        private final AuditLogRepository auditLogRepository;
        private final ObjectMapper objectMapper;

        public UserActivityTool(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
            this.auditLogRepository = auditLogRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "user.activity";
        }

        @Override
        public String getDescription() {
            return "Retrieves recent security actions, logins, and audit events performed by the authenticated user.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "limit", Map.of("type", "integer", "description", "Maximum audit events to return (default 10)")
                    )
            );
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                if (context.userId() == null) {
                    return AiToolResult.error(getName(), "No authenticated user in context.", System.currentTimeMillis() - start);
                }

                int limit = 10;
                if (arguments.containsKey("limit") && arguments.get("limit") instanceof Number n) {
                    limit = Math.min(25, Math.max(1, n.intValue()));
                }

                List<AuditLog> userLogs = auditLogRepository.findByActorIdOrderByCreatedAtDesc(context.userId());
                List<Map<String, Object>> activityList = userLogs.stream()
                        .limit(limit)
                        .map(log -> Map.<String, Object>of(
                                "id", log.getId().toString(),
                                "action", log.getAction() != null ? log.getAction().name() : "UNKNOWN",
                                "resourceType", log.getResourceType() != null ? log.getResourceType() : "SYSTEM",
                                "resourceId", log.getResourceId() != null ? log.getResourceId().toString() : "",
                                "outcome", log.getOutcome() != null ? log.getOutcome() : "SUCCESS",
                                "createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toString() : ""
                        ))
                        .toList();

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(Map.of("totalEvents", activityList.size(), "activities", activityList)), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    @Component
    public static class UserJitRequestsTool implements AiTool {
        private final JitAccessRequestRepository jitRepository;
        private final ObjectMapper objectMapper;

        public UserJitRequestsTool(JitAccessRequestRepository jitRepository, ObjectMapper objectMapper) {
            this.jitRepository = jitRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "user.jit";
        }

        @Override
        public String getDescription() {
            return "Retrieves active, pending, and past Just-In-Time access elevation requests submitted by the current user.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                if (context.workspaceId() == null || context.userId() == null) {
                    return AiToolResult.error(getName(), "Missing workspace or user context.", System.currentTimeMillis() - start);
                }

                List<JitAccessRequest> userRequests = jitRepository.findByWorkspaceIdAndUserId(context.workspaceId(), context.userId());
                List<Map<String, Object>> safeList = userRequests.stream()
                        .limit(20)
                        .map(r -> Map.<String, Object>of(
                                "id", r.getId().toString(),
                                "requestedPermission", r.getRequestedPermission() != null ? r.getRequestedPermission().name() : "",
                                "environmentId", r.getEnvironmentId() != null ? r.getEnvironmentId().toString() : "ALL",
                                "status", r.getStatus() != null ? r.getStatus().name() : "PENDING",
                                "reason", r.getReason() != null ? r.getReason() : "",
                                "expiresAt", r.getExpiresAt() != null ? r.getExpiresAt().toString() : "",
                                "createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : ""
                        ))
                        .toList();

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(Map.of("totalRequests", safeList.size(), "requests", safeList)), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    @Component
    public static class UserSecurityAlertsTool implements AiTool {
        private final SecurityFindingRepository findingRepository;
        private final ObjectMapper objectMapper;

        public UserSecurityAlertsTool(SecurityFindingRepository findingRepository, ObjectMapper objectMapper) {
            this.findingRepository = findingRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "user.securityAlerts";
        }

        @Override
        public String getDescription() {
            return "Retrieves active security alerts, overdue rotations, and unread security findings in the authorized workspace.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                if (context.workspaceId() == null) {
                    return AiToolResult.error(getName(), "Missing workspace context.", System.currentTimeMillis() - start);
                }

                List<SecurityFinding> findings = findingRepository.findByWorkspaceIdAndStatusIn(
                        context.workspaceId(),
                        List.of(FindingStatus.OPEN, FindingStatus.ACKNOWLEDGED, FindingStatus.IN_PROGRESS)
                );

                List<Map<String, Object>> safeList = findings.stream()
                        .limit(15)
                        .map(f -> Map.<String, Object>of(
                                "id", f.getId().toString(),
                                "title", f.getTitle(),
                                "severity", f.getSeverity() != null ? f.getSeverity().name() : "HIGH",
                                "category", f.getCategory() != null ? f.getCategory().name() : "SECURITY",
                                "status", f.getStatus() != null ? f.getStatus().name() : "OPEN",
                                "safeDescription", f.getSafeDescription() != null ? f.getSafeDescription() : "",
                                "route", "/repo-security?findingId=" + f.getId()
                        ))
                        .toList();

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(Map.of("totalAlerts", safeList.size(), "alerts", safeList)), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }
}
