package com.secretvault.ai.tool.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.tool.AiTool;
import com.secretvault.ai.tool.AiToolInvocationContext;
import com.secretvault.ai.tool.AiToolResult;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class EnvironmentTools {

    @Component
    public static class EnvironmentListTool implements AiTool {
        private final ProjectRepository projectRepository;
        private final EnvironmentRepository environmentRepository;
        private final ObjectMapper objectMapper;

        public EnvironmentListTool(ProjectRepository projectRepository, EnvironmentRepository environmentRepository, ObjectMapper objectMapper) {
            this.projectRepository = projectRepository;
            this.environmentRepository = environmentRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "environment.list";
        }

        @Override
        public String getDescription() {
            return "Lists all environments (DEV, STAGING, PROD) for a given project or across authorized workspace projects.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "projectId", Map.of("type", "string", "description", "Optional Project UUID to filter environments")
                    )
            );
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                List<Project> wsProjects = projectRepository.findByWorkspaceId(context.workspaceId());
                Set<UUID> authorizedProjectIds = new HashSet<>();
                for (Project p : wsProjects) {
                    authorizedProjectIds.add(p.getId());
                }

                List<Map<String, Object>> safeEnvList = new ArrayList<>();

                if (arguments.containsKey("projectId") && arguments.get("projectId") != null) {
                    try {
                        UUID pId = UUID.fromString(arguments.get("projectId").toString());
                        if (authorizedProjectIds.contains(pId)) {
                            List<Environment> envs = environmentRepository.findByProjectId(pId);
                            for (Environment e : envs) {
                                safeEnvList.add(toMap(e));
                            }
                        } else {
                            return AiToolResult.error(getName(), "Access Denied: Project not found in authorized workspace.", System.currentTimeMillis() - start);
                        }
                    } catch (IllegalArgumentException e) {
                        return AiToolResult.error(getName(), "Invalid projectId UUID.", System.currentTimeMillis() - start);
                    }
                } else {
                    for (UUID pId : authorizedProjectIds) {
                        List<Environment> envs = environmentRepository.findByProjectId(pId);
                        for (Environment e : envs) {
                            safeEnvList.add(toMap(e));
                        }
                    }
                }

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(Map.of("totalEnvironments", safeEnvList.size(), "environments", safeEnvList)), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }

        private Map<String, Object> toMap(Environment e) {
            return Map.of(
                    "id", e.getId().toString(),
                    "projectId", e.getProjectId().toString(),
                    "name", e.getName(),
                    "slug", e.getSlug(),
                    "envType", e.getEnvType() != null ? e.getEnvType().name() : "DEV",
                    "status", e.getStatus() != null ? e.getStatus().name() : "ACTIVE"
            );
        }
    }

    @Component
    public static class EnvironmentGetTool implements AiTool {
        private final ProjectRepository projectRepository;
        private final EnvironmentRepository environmentRepository;
        private final ObjectMapper objectMapper;

        public EnvironmentGetTool(ProjectRepository projectRepository, EnvironmentRepository environmentRepository, ObjectMapper objectMapper) {
            this.projectRepository = projectRepository;
            this.environmentRepository = environmentRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "environment.get";
        }

        @Override
        public String getDescription() {
            return "Retrieves detailed configuration and metadata for a specific environment.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "environmentId", Map.of("type", "string", "description", "Environment UUID"),
                            "slug", Map.of("type", "string", "description", "Environment slug (e.g., prod, staging)")
                    )
            );
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                List<Project> wsProjects = projectRepository.findByWorkspaceId(context.workspaceId());
                Set<UUID> authorizedProjectIds = new HashSet<>();
                for (Project p : wsProjects) {
                    authorizedProjectIds.add(p.getId());
                }

                Optional<Environment> envOpt = Optional.empty();

                if (arguments.containsKey("environmentId") && arguments.get("environmentId") != null) {
                    try {
                        UUID envId = UUID.fromString(arguments.get("environmentId").toString());
                        Optional<Environment> found = environmentRepository.findById(envId);
                        if (found.isPresent() && authorizedProjectIds.contains(found.get().getProjectId())) {
                            envOpt = found;
                        }
                    } catch (IllegalArgumentException ignored) {}
                }

                if (envOpt.isEmpty() && arguments.containsKey("slug") && arguments.get("slug") != null) {
                    String slug = arguments.get("slug").toString().trim();
                    for (UUID pId : authorizedProjectIds) {
                        Optional<Environment> found = environmentRepository.findByProjectIdAndSlug(pId, slug);
                        if (found.isPresent()) {
                            envOpt = found;
                            break;
                        }
                    }
                }

                if (envOpt.isEmpty()) {
                    return AiToolResult.error(getName(), "Environment not found in authorized workspace projects.", System.currentTimeMillis() - start);
                }

                Environment e = envOpt.get();
                Map<String, Object> safeDto = Map.of(
                        "id", e.getId().toString(),
                        "projectId", e.getProjectId().toString(),
                        "name", e.getName(),
                        "slug", e.getSlug(),
                        "envType", e.getEnvType() != null ? e.getEnvType().name() : "DEV",
                        "status", e.getStatus() != null ? e.getStatus().name() : "ACTIVE"
                );

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(safeDto), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }
}
