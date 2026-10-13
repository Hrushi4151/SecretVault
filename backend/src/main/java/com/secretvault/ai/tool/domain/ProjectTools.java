package com.secretvault.ai.tool.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.tool.AiTool;
import com.secretvault.ai.tool.AiToolInvocationContext;
import com.secretvault.ai.tool.AiToolResult;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ProjectTools {

    @Component
    public static class ProjectListTool implements AiTool {
        private final ProjectRepository projectRepository;
        private final ObjectMapper objectMapper;

        public ProjectListTool(ProjectRepository projectRepository, ObjectMapper objectMapper) {
            this.projectRepository = projectRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "project.list";
        }

        @Override
        public String getDescription() {
            return "Lists all projects in the current authorized workspace with safe metadata.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                List<Project> projects = projectRepository.findByWorkspaceId(context.workspaceId());
                List<Map<String, Object>> safeList = projects.stream()
                        .map(p -> Map.<String, Object>of(
                                "id", p.getId().toString(),
                                "name", p.getName(),
                                "slug", p.getSlug(),
                                "description", p.getDescription() != null ? p.getDescription() : "",
                                "status", p.getStatus() != null ? p.getStatus().name() : "ACTIVE",
                                "createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : ""
                        ))
                        .toList();

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(Map.of("totalProjects", safeList.size(), "projects", safeList)), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    @Component
    public static class ProjectGetTool implements AiTool {
        private final ProjectRepository projectRepository;
        private final ObjectMapper objectMapper;

        public ProjectGetTool(ProjectRepository projectRepository, ObjectMapper objectMapper) {
            this.projectRepository = projectRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "project.get";
        }

        @Override
        public String getDescription() {
            return "Retrieves detailed metadata for a project by ID or slug in the authorized workspace.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "projectId", Map.of("type", "string", "description", "The UUID of the project"),
                            "slug", Map.of("type", "string", "description", "The slug of the project (e.g., payments)")
                    )
            );
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                Optional<Project> projectOpt = Optional.empty();

                if (arguments.containsKey("projectId") && arguments.get("projectId") != null) {
                    try {
                        UUID pId = UUID.fromString(arguments.get("projectId").toString());
                        projectOpt = projectRepository.findByIdAndWorkspaceId(pId, context.workspaceId());
                    } catch (IllegalArgumentException ignored) {}
                }

                if (projectOpt.isEmpty() && arguments.containsKey("slug") && arguments.get("slug") != null) {
                    String slug = arguments.get("slug").toString().trim();
                    projectOpt = projectRepository.findByWorkspaceIdAndSlug(context.workspaceId(), slug);
                }

                if (projectOpt.isEmpty()) {
                    // Fallback to name search across workspace projects
                    List<Project> projects = projectRepository.findByWorkspaceId(context.workspaceId());
                    String searchKey = arguments.containsKey("slug") ? arguments.get("slug").toString() : (arguments.containsKey("projectId") ? arguments.get("projectId").toString() : "");
                    projectOpt = projects.stream()
                            .filter(p -> p.getName().equalsIgnoreCase(searchKey) || p.getSlug().equalsIgnoreCase(searchKey))
                            .findFirst();
                }

                if (projectOpt.isEmpty()) {
                    return AiToolResult.error(getName(), "Project not found in authorized workspace.", System.currentTimeMillis() - start);
                }

                Project p = projectOpt.get();
                Map<String, Object> safeDto = Map.of(
                        "id", p.getId().toString(),
                        "name", p.getName(),
                        "slug", p.getSlug(),
                        "description", p.getDescription() != null ? p.getDescription() : "",
                        "status", p.getStatus() != null ? p.getStatus().name() : "ACTIVE",
                        "createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : ""
                );

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(safeDto), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }
}
