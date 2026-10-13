package com.secretvault.ai.tool.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.ai.tool.AiTool;
import com.secretvault.ai.tool.AiToolInvocationContext;
import com.secretvault.ai.tool.AiToolResult;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.rotation.entity.SecretDependency;
import com.secretvault.rotation.repository.SecretDependencyRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SecretMetadataTools {

    @Component
    public static class SecretListMetadataTool implements AiTool {
        private final ProjectRepository projectRepository;
        private final EnvironmentRepository environmentRepository;
        private final SecretRepository secretRepository;
        private final ObjectMapper objectMapper;

        public SecretListMetadataTool(
                ProjectRepository projectRepository,
                EnvironmentRepository environmentRepository,
                SecretRepository secretRepository,
                ObjectMapper objectMapper
        ) {
            this.projectRepository = projectRepository;
            this.environmentRepository = environmentRepository;
            this.secretRepository = secretRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "secret.listMetadata";
        }

        @Override
        public String getDescription() {
            return "Lists safe secret metadata (name, environment, version number, status, timestamps) without any secret values.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "environmentId", Map.of("type", "string", "description", "Optional Environment UUID filter"),
                            "projectId", Map.of("type", "string", "description", "Optional Project UUID filter")
                    )
            );
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                List<Project> wsProjects = projectRepository.findByWorkspaceId(context.workspaceId());
                Set<UUID> projectIds = new HashSet<>();
                for (Project p : wsProjects) projectIds.add(p.getId());

                List<UUID> targetEnvIds = new ArrayList<>();

                if (arguments.containsKey("environmentId") && arguments.get("environmentId") != null) {
                    try {
                        UUID envId = UUID.fromString(arguments.get("environmentId").toString());
                        Optional<Environment> envOpt = environmentRepository.findById(envId);
                        if (envOpt.isPresent() && projectIds.contains(envOpt.get().getProjectId())) {
                            targetEnvIds.add(envId);
                        } else {
                            return AiToolResult.error(getName(), "Access Denied: Environment not in authorized workspace.", System.currentTimeMillis() - start);
                        }
                    } catch (IllegalArgumentException e) {
                        return AiToolResult.error(getName(), "Invalid environmentId format.", System.currentTimeMillis() - start);
                    }
                } else if (arguments.containsKey("projectId") && arguments.get("projectId") != null) {
                    try {
                        UUID pId = UUID.fromString(arguments.get("projectId").toString());
                        if (projectIds.contains(pId)) {
                            List<Environment> envs = environmentRepository.findByProjectId(pId);
                            for (Environment e : envs) targetEnvIds.add(e.getId());
                        } else {
                            return AiToolResult.error(getName(), "Access Denied: Project not in authorized workspace.", System.currentTimeMillis() - start);
                        }
                    } catch (IllegalArgumentException e) {
                        return AiToolResult.error(getName(), "Invalid projectId format.", System.currentTimeMillis() - start);
                    }
                } else {
                    for (UUID pId : projectIds) {
                        List<Environment> envs = environmentRepository.findByProjectId(pId);
                        for (Environment e : envs) targetEnvIds.add(e.getId());
                    }
                }

                List<Map<String, Object>> safeSecretList = new ArrayList<>();
                for (UUID envId : targetEnvIds) {
                    List<Secret> secrets = secretRepository.findByEnvironmentId(envId);
                    for (Secret s : secrets) {
                        safeSecretList.add(Map.of(
                                "id", s.getId().toString(),
                                "environmentId", s.getEnvironmentId().toString(),
                                "name", s.getName(),
                                "description", s.getDescription() != null ? s.getDescription() : "",
                                "currentVersionNumber", s.getCurrentVersionNumber(),
                                "status", s.getStatus() != null ? s.getStatus().name() : "ACTIVE",
                                "createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toString() : "",
                                "updatedAt", s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : ""
                        ));
                    }
                }

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(Map.of("totalSecrets", safeSecretList.size(), "secrets", safeSecretList)), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    @Component
    public static class SecretGetMetadataTool implements AiTool {
        private final ProjectRepository projectRepository;
        private final EnvironmentRepository environmentRepository;
        private final SecretRepository secretRepository;
        private final ObjectMapper objectMapper;

        public SecretGetMetadataTool(
                ProjectRepository projectRepository,
                EnvironmentRepository environmentRepository,
                SecretRepository secretRepository,
                ObjectMapper objectMapper
        ) {
            this.projectRepository = projectRepository;
            this.environmentRepository = environmentRepository;
            this.secretRepository = secretRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "secret.getMetadata";
        }

        @Override
        public String getDescription() {
            return "Retrieves detailed structural metadata for a single secret by name or ID without revealing secret plaintext.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "secretId", Map.of("type", "string", "description", "The secret UUID"),
                            "name", Map.of("type", "string", "description", "The secret name (e.g. DATABASE_URL)"),
                            "environmentId", Map.of("type", "string", "description", "The environment UUID if querying by name")
                    )
            );
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                List<Project> wsProjects = projectRepository.findByWorkspaceId(context.workspaceId());
                Set<UUID> projectIds = new HashSet<>();
                for (Project p : wsProjects) projectIds.add(p.getId());

                Set<UUID> envIds = new HashSet<>();
                for (UUID pId : projectIds) {
                    for (Environment e : environmentRepository.findByProjectId(pId)) {
                        envIds.add(e.getId());
                    }
                }

                Optional<Secret> secretOpt = Optional.empty();

                if (arguments.containsKey("secretId") && arguments.get("secretId") != null) {
                    try {
                        UUID sId = UUID.fromString(arguments.get("secretId").toString());
                        Optional<Secret> found = secretRepository.findById(sId);
                        if (found.isPresent() && envIds.contains(found.get().getEnvironmentId())) {
                            secretOpt = found;
                        }
                    } catch (IllegalArgumentException ignored) {}
                }

                if (secretOpt.isEmpty() && arguments.containsKey("name") && arguments.get("name") != null) {
                    String name = arguments.get("name").toString().trim();
                    for (UUID envId : envIds) {
                        Optional<Secret> found = secretRepository.findByEnvironmentIdAndName(envId, name);
                        if (found.isPresent()) {
                            secretOpt = found;
                            break;
                        }
                    }
                }

                if (secretOpt.isEmpty()) {
                    return AiToolResult.error(getName(), "Secret not found in authorized workspace environments.", System.currentTimeMillis() - start);
                }

                Secret s = secretOpt.get();
                Map<String, Object> safeDto = Map.of(
                        "id", s.getId().toString(),
                        "environmentId", s.getEnvironmentId().toString(),
                        "name", s.getName(),
                        "description", s.getDescription() != null ? s.getDescription() : "",
                        "currentVersionNumber", s.getCurrentVersionNumber(),
                        "status", s.getStatus() != null ? s.getStatus().name() : "ACTIVE",
                        "createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toString() : "",
                        "updatedAt", s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : ""
                );

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(safeDto), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    @Component
    public static class SecretHistoryTool implements AiTool {
        private final ProjectRepository projectRepository;
        private final EnvironmentRepository environmentRepository;
        private final SecretRepository secretRepository;
        private final SecretVersionRepository versionRepository;
        private final ObjectMapper objectMapper;

        public SecretHistoryTool(
                ProjectRepository projectRepository,
                EnvironmentRepository environmentRepository,
                SecretRepository secretRepository,
                SecretVersionRepository versionRepository,
                ObjectMapper objectMapper
        ) {
            this.projectRepository = projectRepository;
            this.environmentRepository = environmentRepository;
            this.secretRepository = secretRepository;
            this.versionRepository = versionRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "secret.history";
        }

        @Override
        public String getDescription() {
            return "Retrieves version timeline and cryptographic hash fingerprints for a secret (zero plaintext).";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "secretId", Map.of("type", "string", "description", "Secret UUID"),
                            "name", Map.of("type", "string", "description", "Secret name")
                    )
            );
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                List<Project> wsProjects = projectRepository.findByWorkspaceId(context.workspaceId());
                Set<UUID> projectIds = new HashSet<>();
                for (Project p : wsProjects) projectIds.add(p.getId());

                Set<UUID> envIds = new HashSet<>();
                for (UUID pId : projectIds) {
                    for (Environment e : environmentRepository.findByProjectId(pId)) {
                        envIds.add(e.getId());
                    }
                }

                Optional<Secret> secretOpt = Optional.empty();
                if (arguments.containsKey("secretId") && arguments.get("secretId") != null) {
                    try {
                        UUID sId = UUID.fromString(arguments.get("secretId").toString());
                        Optional<Secret> found = secretRepository.findById(sId);
                        if (found.isPresent() && envIds.contains(found.get().getEnvironmentId())) {
                            secretOpt = found;
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
                if (secretOpt.isEmpty() && arguments.containsKey("name") && arguments.get("name") != null) {
                    String name = arguments.get("name").toString().trim();
                    for (UUID envId : envIds) {
                        Optional<Secret> found = secretRepository.findByEnvironmentIdAndName(envId, name);
                        if (found.isPresent()) {
                            secretOpt = found;
                            break;
                        }
                    }
                }

                if (secretOpt.isEmpty()) {
                    return AiToolResult.error(getName(), "Secret not found in authorized workspace.", System.currentTimeMillis() - start);
                }

                Secret secret = secretOpt.get();
                List<SecretVersion> versions = versionRepository.findBySecretIdOrderByVersionNumberDesc(secret.getId());

                List<Map<String, Object>> safeVersions = versions.stream()
                        .map(v -> Map.<String, Object>of(
                                "versionNumber", v.getVersionNumber(),
                                "versionType", v.getVersionType() != null ? v.getVersionType().name() : "STANDARD",
                                "fingerprint", v.getFingerprint() != null ? v.getFingerprint() : "sha256-uncalculated",
                                "createdAt", v.getCreatedAt() != null ? v.getCreatedAt().toString() : ""
                        ))
                        .toList();

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(Map.of(
                        "secretId", secret.getId().toString(),
                        "name", secret.getName(),
                        "totalVersions", safeVersions.size(),
                        "versions", safeVersions
                )), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }

    @Component
    public static class SecretBlastRadiusTool implements AiTool {
        private final ProjectRepository projectRepository;
        private final EnvironmentRepository environmentRepository;
        private final SecretRepository secretRepository;
        private final SecretDependencyRepository dependencyRepository;
        private final ObjectMapper objectMapper;

        public SecretBlastRadiusTool(
                ProjectRepository projectRepository,
                EnvironmentRepository environmentRepository,
                SecretRepository secretRepository,
                @Autowired(required = false) SecretDependencyRepository dependencyRepository,
                ObjectMapper objectMapper
        ) {
            this.projectRepository = projectRepository;
            this.environmentRepository = environmentRepository;
            this.secretRepository = secretRepository;
            this.dependencyRepository = dependencyRepository;
            this.objectMapper = objectMapper;
        }

        @Override
        public String getName() {
            return "secret.blastRadius";
        }

        @Override
        public String getDescription() {
            return "Evaluates blast radius: discovers dependent applications, consumers, environments, and services affected if a secret is changed or rotated.";
        }

        @Override
        public Map<String, Object> getParameterSchema() {
            return Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "secretId", Map.of("type", "string", "description", "Secret UUID"),
                            "name", Map.of("type", "string", "description", "Secret Name")
                    )
            );
        }

        @Override
        public AiToolResult execute(AiToolInvocationContext context, Map<String, Object> arguments) {
            long start = System.currentTimeMillis();
            try {
                List<Project> wsProjects = projectRepository.findByWorkspaceId(context.workspaceId());
                Set<UUID> projectIds = new HashSet<>();
                for (Project p : wsProjects) projectIds.add(p.getId());

                Map<UUID, Environment> envMap = new HashMap<>();
                for (UUID pId : projectIds) {
                    for (Environment e : environmentRepository.findByProjectId(pId)) {
                        envMap.put(e.getId(), e);
                    }
                }

                Optional<Secret> secretOpt = Optional.empty();
                if (arguments.containsKey("secretId") && arguments.get("secretId") != null) {
                    try {
                        UUID sId = UUID.fromString(arguments.get("secretId").toString());
                        Optional<Secret> found = secretRepository.findById(sId);
                        if (found.isPresent() && envMap.containsKey(found.get().getEnvironmentId())) {
                            secretOpt = found;
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
                if (secretOpt.isEmpty() && arguments.containsKey("name") && arguments.get("name") != null) {
                    String name = arguments.get("name").toString().trim();
                    for (UUID envId : envMap.keySet()) {
                        Optional<Secret> found = secretRepository.findByEnvironmentIdAndName(envId, name);
                        if (found.isPresent()) {
                            secretOpt = found;
                            break;
                        }
                    }
                }

                if (secretOpt.isEmpty()) {
                    return AiToolResult.error(getName(), "Secret not found in authorized workspace.", System.currentTimeMillis() - start);
                }

                Secret s = secretOpt.get();
                Environment env = envMap.get(s.getEnvironmentId());
                String envType = env != null && env.getEnvType() != null ? env.getEnvType().name() : "DEV";

                List<String> dependentConsumers = new ArrayList<>();
                if (dependencyRepository != null) {
                    List<SecretDependency> deps = dependencyRepository.findBySecretId(s.getId());
                    for (SecretDependency d : deps) {
                        dependentConsumers.add("Consumer: " + d.getConsumerId().toString());
                    }
                }

                String severity = "LOW";
                if ("PROD".equalsIgnoreCase(envType) || "PRODUCTION".equalsIgnoreCase(envType)) {
                    severity = "CRITICAL";
                } else if ("STAGING".equalsIgnoreCase(envType)) {
                    severity = "HIGH";
                }

                Map<String, Object> result = Map.of(
                        "secretId", s.getId().toString(),
                        "name", s.getName(),
                        "environment", env != null ? env.getName() : "unknown",
                        "environmentType", envType,
                        "blastRadiusSeverity", severity,
                        "dependentConsumersCount", dependentConsumers.size(),
                        "dependentConsumers", dependentConsumers,
                        "recommendedContainment", "PROD".equalsIgnoreCase(envType)
                                ? "Enforce dual-version tolerance window (300s TTL) and monitor consumer heartbeats before decommission."
                                : "Standard rolling restart of registered consumers.",
                        "requiresFourEyesApproval", "CRITICAL".equals(severity)
                );

                return AiToolResult.ok(getName(), objectMapper.writeValueAsString(result), System.currentTimeMillis() - start);
            } catch (Exception e) {
                return AiToolResult.error(getName(), e.getMessage(), System.currentTimeMillis() - start);
            }
        }
    }
}
