package com.secretvault.ai.tool.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.tool.AiTool;
import com.secretvault.ai.tool.AiToolInvocationContext;
import com.secretvault.ai.tool.AiToolResult;
import com.secretvault.sync.entity.DriftRecord;
import com.secretvault.sync.entity.SyncJob;
import com.secretvault.sync.model.DriftStatus;
import com.secretvault.sync.model.SyncJobStatus;
import com.secretvault.sync.repository.DriftRecordRepository;
import com.secretvault.sync.repository.SyncJobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SyncTools {

    @Component
    public static class SyncStatusTool implements AiTool {
        private final SyncJobRepository syncJobRepository;
        private final DriftRecordRepository driftRecordRepository;
        private final ObjectMapper objectMapper;

        public SyncStatusTool(
                @Autowired(required = false) SyncJobRepository syncJobRepository,
                @Autowired(required = false) DriftRecordRepository driftRecordRepository,
                ObjectMapper objectMapper
        ) {
            this.syncJobRepository = syncJobRepository;
            this.driftRecordRepository = driftRecordRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "sync.status";
        }

        @Override
        public String getDescription() {
            return "Evaluates multi-cloud synchronization health, active sync jobs, and drift count across target providers.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                long activeDriftCount = 0;
                if (driftRecordRepository != null) {
                    activeDriftCount = driftRecordRepository.countByWorkspaceIdAndStatus(context.workspaceId(), DriftStatus.OPEN);
                }

                List<SyncJob> recentJobs = Collections.emptyList();
                if (syncJobRepository != null) {
                    recentJobs = syncJobRepository.findByWorkspaceId(context.workspaceId(), PageRequest.of(0, 10)).getContent();
                }

                long failedCount = recentJobs.stream().filter(j -> j.getStatus() == SyncJobStatus.FAILED).count();

                String overallHealth = activeDriftCount == 0 && failedCount == 0 ? "HEALTHY" : (activeDriftCount > 5 || failedCount > 0 ? "DEGRADED" : "DRIFT_DETECTED");

                Map<String, Object> result = Map.of(
                        "workspaceId", context.workspaceId().toString(),
                        "syncHealth", overallHealth,
                        "activeDriftCount", activeDriftCount,
                        "recentJobsCount", recentJobs.size(),
                        "failedJobsCount", failedCount,
                        "recentJobs", recentJobs.stream().map(j -> Map.of(
                                "id", j.getId().toString(),
                                "status", j.getStatus() != null ? j.getStatus().name() : "SUCCESS",
                                "dryRun", j.isDryRun(),
                                "createdAt", j.getCreatedAt() != null ? j.getCreatedAt().toString() : ""
                        )).limit(5).toList()
                );

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(result), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    @Component
    public static class SyncDriftTool implements AiTool {
        private final DriftRecordRepository driftRecordRepository;
        private final ObjectMapper objectMapper;

        public SyncDriftTool(@Autowired(required = false) DriftRecordRepository driftRecordRepository, ObjectMapper objectMapper) {
            this.driftRecordRepository = driftRecordRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "sync.drift";
        }

        @Override
        public String getDescription() {
            return "Lists detected secret configuration and hash value drift between SecretVault and cloud providers.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "limit", Map.of("type", "integer", "description", "Max drift items to retrieve (default: 20)")
                    )
            );
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                if (driftRecordRepository == null) {
                    return AiToolResult.ok(getName(), "{\"activeDrifts\":[]}", System.currentTimeMillis() - start);
                }

                int limit = 20;
                if (arguments.containsKey("limit") && arguments.get("limit") instanceof Number n) {
                    limit = Math.min(50, Math.max(1, n.intValue()));
                }

                List<DriftRecord> drifts = driftRecordRepository.findByWorkspaceIdAndStatus(context.workspaceId(), DriftStatus.OPEN);

                List<Map<String, Object>> safeDrifts = drifts.stream()
                        .limit(limit)
                        .map(d -> Map.<String, Object>of(
                                "id", d.getId().toString(),
                                "driftType", d.getDriftType() != null ? d.getDriftType().name() : "VALUE_MISMATCH",
                                "severity", d.getSeverity() != null ? d.getSeverity().name() : "MEDIUM",
                                "status", d.getStatus() != null ? d.getStatus().name() : "OPEN",
                                "firstDetectedAt", d.getFirstDetectedAt() != null ? d.getFirstDetectedAt().toString() : ""
                        ))
                        .toList();

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(Map.of(
                        "totalActiveDrifts", drifts.size(),
                        "drifts", safeDrifts
                )), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }
}
