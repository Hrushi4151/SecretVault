package com.secretvault.ai.tool.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.tool.AiTool;
import com.secretvault.ai.tool.AiToolInvocationContext;
import com.secretvault.ai.tool.AiToolResult;
import com.secretvault.repository.entity.RepositoryEntity;
import com.secretvault.repository.entity.RepositoryScan;
import com.secretvault.repository.entity.SecretFinding;
import com.secretvault.repository.model.RepoFindingStatus;
import com.secretvault.repository.repository.RepositoryEntityRepository;
import com.secretvault.repository.repository.RepositoryScanRepository;
import com.secretvault.repository.repository.SecretFindingRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class RepoSecurityTools {

    @Component
    public static class RepoListTool implements AiTool {
        private final RepositoryEntityRepository repoRepository;
        private final ObjectMapper objectMapper;

        public RepoListTool(RepositoryEntityRepository repoRepository, ObjectMapper objectMapper) {
            this.repoRepository = repoRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "repo.list";
        }

        @Override
        public String getDescription() {
            return "Lists connected code repositories in the active workspace and their secret scan status.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "limit", Map.of("type", "integer", "description", "Max repositories to return (default: 20)")
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

                var repos = repoRepository.findByWorkspaceId(
                        context.workspaceId(), PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
                ).getContent();

                List<Map<String, Object>> items = new ArrayList<>();
                for (RepositoryEntity r : repos) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", r.getId());
                    map.put("name", r.getName());
                    map.put("owner", r.getOwner());
                    map.put("provider", r.getProvider().name());
                    map.put("visibility", r.getVisibility().name());
                    map.put("defaultBranch", r.getDefaultBranch());
                    map.put("lastScanAt", r.getLastScanAt() != null ? r.getLastScanAt().toString() : "Never");
                    map.put("route", "/repo-security");
                    items.add(map);
                }

                String json = objectMapper.writeValueAsString(Map.of(
                        "count", items.size(),
                        "repositories", items
                ));

                return AiToolResult.ok(getName(), json, System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    @Component
    public static class RepoFindingsTool implements AiTool {
        private final SecretFindingRepository findingRepository;
        private final ObjectMapper objectMapper;

        public RepoFindingsTool(SecretFindingRepository findingRepository, ObjectMapper objectMapper) {
            this.findingRepository = findingRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "repo.findings";
        }

        @Override
        public String getDescription() {
            return "Lists exposed secret findings in repositories with masked previews, line numbers, and remediation paths (Zero Plaintext).";
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

                List<RepoFindingStatus> activeStatuses = List.of(
                        RepoFindingStatus.DETECTED, RepoFindingStatus.CONFIRMED, RepoFindingStatus.REOPENED, RepoFindingStatus.REMEDIATION_PENDING
                );

                List<SecretFinding> findings = findingRepository.findByWorkspaceIdAndStatusIn(
                        context.workspaceId(), activeStatuses
                );

                List<Map<String, Object>> items = new ArrayList<>();
                for (SecretFinding f : findings.stream().limit(limit).toList()) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", f.getId());
                    map.put("secretType", f.getSecretType().name());
                    map.put("detector", f.getDetectorType());
                    map.put("severity", f.getSeverity().name());
                    map.put("confidence", f.getConfidence().name());
                    map.put("filePath", f.getFilePath());
                    map.put("lineNumber", f.getLineNumber() != null ? f.getLineNumber() : 1);
                    map.put("maskedValue", f.getMaskedValue());
                    map.put("status", f.getStatus().name());
                    map.put("matchedSecretId", f.getMatchedSecretId() != null ? f.getMatchedSecretId().toString() : "None");
                    map.put("route", "/repo-security?findingId=" + f.getId());
                    items.add(map);
                }

                String json = objectMapper.writeValueAsString(Map.of(
                        "count", items.size(),
                        "findings", items
                ));

                return AiToolResult.ok(getName(), json, System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    @Component
    public static class RepoScansTool implements AiTool {
        private final RepositoryScanRepository scanRepository;
        private final ObjectMapper objectMapper;

        public RepoScansTool(RepositoryScanRepository scanRepository, ObjectMapper objectMapper) {
            this.scanRepository = scanRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "repo.scans";
        }

        @Override
        public String getDescription() {
            return "Lists recent repository leak scans, progress, and finding statistics for the workspace.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "limit", Map.of("type", "integer", "description", "Max scans to return (default: 10)")
                    )
            );
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                int limit = 10;
                if (arguments.containsKey("limit") && arguments.get("limit") instanceof Number n) {
                    limit = Math.min(30, Math.max(1, n.intValue()));
                }

                var scans = scanRepository.findByWorkspaceId(
                        context.workspaceId(), PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
                ).getContent();

                List<Map<String, Object>> items = new ArrayList<>();
                for (RepositoryScan s : scans) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", s.getId());
                    map.put("repositoryId", s.getRepositoryId() != null ? s.getRepositoryId().toString() : "local");
                    map.put("scanType", s.getScanType().name());
                    map.put("status", s.getStatus().name());
                    map.put("filesScanned", s.getFilesScanned());
                    map.put("findingsCount", s.getFindingsCount());
                    map.put("highRiskCount", s.getHighRiskCount());
                    map.put("completedAt", s.getCompletedAt() != null ? s.getCompletedAt().toString() : "In Progress");
                    map.put("route", "/repo-security");
                    items.add(map);
                }

                String json = objectMapper.writeValueAsString(Map.of(
                        "count", items.size(),
                        "scans", items
                ));

                return AiToolResult.ok(getName(), json, System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }
}
