package com.secretvault.ai.tool.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.tool.AiTool;
import com.secretvault.ai.tool.AiToolInvocationContext;
import com.secretvault.ai.tool.AiToolResult;
import com.secretvault.rotation.entity.RotationJob;
import com.secretvault.rotation.entity.RotationPolicy;
import com.secretvault.rotation.repository.RotationJobRepository;
import com.secretvault.rotation.repository.RotationPolicyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class RotationTools {

    @Component
    public static class RotationStatusTool implements AiTool {
        private final RotationPolicyRepository policyRepository;
        private final RotationJobRepository jobRepository;
        private final ObjectMapper objectMapper;

        public RotationStatusTool(
                @Autowired(required = false) RotationPolicyRepository policyRepository,
                @Autowired(required = false) RotationJobRepository jobRepository,
                ObjectMapper objectMapper
        ) {
            this.policyRepository = policyRepository;
            this.jobRepository = jobRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "rotation.status";
        }

        @Override
        public String getDescription() {
            return "Summarizes secret rotation health, active rotation policies, and overdue secrets across the workspace.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                if (policyRepository == null || jobRepository == null) {
                    return AiToolResult.ok(getName(), "{\"rotationEnabled\":true,\"status\":\"HEALTHY\"}", System.currentTimeMillis() - start);
                }

                Instant now = Instant.now();
                List<RotationPolicy> policies = policyRepository.findByWorkspaceId(context.workspaceId());
                List<RotationPolicy> overdue = policyRepository.findOverdueByWorkspaceId(context.workspaceId(), now);
                long activeJobs = jobRepository.countActiveByWorkspaceId(context.workspaceId());
                long failedJobs = jobRepository.countFailedByWorkspaceId(context.workspaceId());

                Map<String, Object> result = Map.of(
                        "totalConfiguredPolicies", policies.size(),
                        "overdueSecretsCount", overdue.size(),
                        "activeRotationJobsCount", activeJobs,
                        "failedRotationJobsCount", failedJobs,
                        "health", overdue.isEmpty() && failedJobs == 0 ? "HEALTHY" : (overdue.size() > 5 || failedJobs > 2 ? "CRITICAL" : "DEGRADED"),
                        "overdueSecrets", overdue.stream().map(p -> Map.of(
                                "secretId", p.getSecretId() != null ? p.getSecretId().toString() : "",
                                "dueAt", p.getNextRotationDueAt() != null ? p.getNextRotationDueAt().toString() : "past"
                        )).limit(15).toList()
                );

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(result), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    @Component
    public static class RotationHistoryTool implements AiTool {
        private final RotationJobRepository jobRepository;
        private final ObjectMapper objectMapper;

        public RotationHistoryTool(
                @Autowired(required = false) RotationJobRepository jobRepository,
                ObjectMapper objectMapper
        ) {
            this.jobRepository = jobRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "rotation.history";
        }

        @Override
        public String getDescription() {
            return "Retrieves recent rotation execution jobs, statuses, and shadow validation outcomes.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "limit", Map.of("type", "integer", "description", "Max jobs to return (default: 15)")
                    )
            );
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                if (jobRepository == null) {
                    return AiToolResult.ok(getName(), "{\"rotationJobs\":[]}", System.currentTimeMillis() - start);
                }

                int limit = 15;
                if (arguments.containsKey("limit") && arguments.get("limit") instanceof Number n) {
                    limit = Math.min(50, Math.max(1, n.intValue()));
                }

                List<RotationJob> jobs = jobRepository.findByWorkspaceId(context.workspaceId());
                List<Map<String, Object>> safeJobs = jobs.stream()
                        .limit(limit)
                        .map(j -> Map.<String, Object>of(
                                "id", j.getId().toString(),
                                "secretId", j.getSecretId() != null ? j.getSecretId().toString() : "",
                                "status", j.getStatus() != null ? j.getStatus().name() : "COMPLETED",
                                "targetVersionNumber", j.getTargetVersionNumber() != null ? j.getTargetVersionNumber() : 1,
                                "errorMessage", j.getErrorMessage() != null ? j.getErrorMessage() : "",
                                "createdAt", j.getCreatedAt() != null ? j.getCreatedAt().toString() : ""
                        ))
                        .toList();

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(Map.of("totalJobs", safeJobs.size(), "rotationJobs", safeJobs)), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }
}
