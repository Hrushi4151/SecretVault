package com.secretvault.cli.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Resolves context (Server, Profile, Workspace, Project, Environment) across:
 * 1. Explicit CLI arguments
 * 2. Environment variables (SECRET_VAULT_*)
 * 3. Local directory configuration (.secretvault/project.json)
 * 4. User profile defaults in config.json
 */
public class ContextManager {

    public static final String LOCAL_DIR_NAME = ".secretvault";
    public static final String LOCAL_PROJECT_FILE = "project.json";

    private final ConfigManager configManager;
    private final ObjectMapper objectMapper;
    private final Path workingDirectory;

    public ContextManager(ConfigManager configManager) {
        this(configManager, Paths.get(System.getProperty("user.dir", ".")));
    }

    public ContextManager(ConfigManager configManager, Path workingDirectory) {
        this.configManager = configManager;
        this.objectMapper = new ObjectMapper();
        this.workingDirectory = workingDirectory;
    }

    public record ResolvedContext(
            String profile,
            String server,
            String workspace,
            String project,
            String environment,
            String outputFormat
    ) {}

    public ResolvedContext resolve(
            String cliProfile,
            String cliServer,
            String cliWorkspace,
            String cliProject,
            String cliEnvironment,
            String cliOutputFormat
    ) {
        CliConfig config = configManager.loadConfig();

        // 1. Resolve Profile
        String profile = (cliProfile != null && !cliProfile.isBlank()) ? cliProfile
                : Optional.ofNullable(System.getenv("SECRET_VAULT_PROFILE")).filter(s -> !s.isBlank())
                .orElse(config.getDefaultProfile());

        ProfileConfig profileConfig = config.getProfile(profile);

        // 2. Discover local directory .secretvault/project.json
        Optional<ProjectLocalConfig> localConfig = findLocalProjectConfig();

        // 3. Resolve Server URL
        String server = (cliServer != null && !cliServer.isBlank()) ? cliServer
                : Optional.ofNullable(System.getenv("SECRET_VAULT_SERVER")).filter(s -> !s.isBlank())
                .orElseGet(() -> localConfig.map(ProjectLocalConfig::getServer)
                        .filter(s -> !s.isBlank())
                        .orElse(profileConfig.getServer()));

        // 4. Resolve Workspace (Name/Slug/UUID)
        String workspace = (cliWorkspace != null && !cliWorkspace.isBlank()) ? cliWorkspace
                : Optional.ofNullable(System.getenv("SECRET_VAULT_WORKSPACE")).filter(s -> !s.isBlank())
                .orElseGet(() -> localConfig.map(ProjectLocalConfig::getWorkspaceSlug)
                        .or(() -> localConfig.map(ProjectLocalConfig::getWorkspaceId))
                        .filter(s -> !s.isBlank())
                        .orElseGet(() -> profileConfig.getWorkspaceSlug() != null ? profileConfig.getWorkspaceSlug() : profileConfig.getWorkspaceId()));

        // 5. Resolve Project (Name/Slug/UUID)
        String project = (cliProject != null && !cliProject.isBlank()) ? cliProject
                : Optional.ofNullable(System.getenv("SECRET_VAULT_PROJECT")).filter(s -> !s.isBlank())
                .orElseGet(() -> localConfig.map(ProjectLocalConfig::getProjectSlug)
                        .or(() -> localConfig.map(ProjectLocalConfig::getProjectId))
                        .filter(s -> !s.isBlank())
                        .orElseGet(() -> profileConfig.getProjectSlug() != null ? profileConfig.getProjectSlug() : profileConfig.getProjectId()));

        // 6. Resolve Environment (Name/Slug/UUID)
        String environment = (cliEnvironment != null && !cliEnvironment.isBlank()) ? cliEnvironment
                : Optional.ofNullable(System.getenv("SECRET_VAULT_ENVIRONMENT")).filter(s -> !s.isBlank())
                .orElseGet(() -> localConfig.map(ProjectLocalConfig::getEnvironmentSlug)
                        .or(() -> localConfig.map(ProjectLocalConfig::getEnvironmentId))
                        .filter(s -> !s.isBlank())
                        .orElseGet(() -> profileConfig.getEnvironmentSlug() != null ? profileConfig.getEnvironmentSlug() : profileConfig.getEnvironmentId()));

        // 7. Resolve Output Format
        String outputFormat = (cliOutputFormat != null && !cliOutputFormat.isBlank()) ? cliOutputFormat
                : Optional.ofNullable(System.getenv("SECRET_VAULT_OUTPUT")).filter(s -> !s.isBlank())
                .orElse(profileConfig.getOutputFormat());

        return new ResolvedContext(profile, server, workspace, project, environment, outputFormat);
    }

    public Optional<ProjectLocalConfig> findLocalProjectConfig() {
        Path current = workingDirectory.toAbsolutePath();
        while (current != null) {
            Path target = current.resolve(LOCAL_DIR_NAME).resolve(LOCAL_PROJECT_FILE);
            if (Files.exists(target) && Files.isRegularFile(target)) {
                try {
                    return Optional.of(objectMapper.readValue(target.toFile(), ProjectLocalConfig.class));
                } catch (Exception ignored) {
                }
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    public void saveLocalProjectConfig(ProjectLocalConfig projectConfig) {
        try {
            Path dir = workingDirectory.resolve(LOCAL_DIR_NAME);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Path target = dir.resolve(LOCAL_PROJECT_FILE);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), projectConfig);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write local project config: " + e.getMessage(), e);
        }
    }
}
