package com.secretvault.cli.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Manages CLI configuration loading, persistence, and platform-specific paths.
 */
public class ConfigManager {

    private static final String APP_NAME = "SecretVault";
    private static final String CONFIG_FILE_NAME = "config.json";

    private final Path configDirectory;
    private final Path configFile;
    private final ObjectMapper objectMapper;

    public ConfigManager() {
        this(resolveDefaultConfigDirectory());
    }

    public ConfigManager(Path configDirectory) {
        this.configDirectory = configDirectory;
        this.configFile = configDirectory.resolve(CONFIG_FILE_NAME);
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Path getConfigDirectory() {
        return configDirectory;
    }

    public Path getConfigFile() {
        return configFile;
    }

    public synchronized CliConfig loadConfig() {
        if (!Files.exists(configFile)) {
            CliConfig defaultConfig = new CliConfig();
            saveConfig(defaultConfig);
            return defaultConfig;
        }
        try {
            return objectMapper.readValue(configFile.toFile(), CliConfig.class);
        } catch (Exception e) {
            // Fallback to fresh config if file was corrupted
            return new CliConfig();
        }
    }

    public synchronized void saveConfig(CliConfig config) {
        try {
            if (!Files.exists(configDirectory)) {
                Files.createDirectories(configDirectory);
                applySecureDirectoryPermissions(configDirectory);
            }
            objectMapper.writeValue(configFile.toFile(), config);
            applySecureFilePermissions(configFile);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save SecretVault CLI configuration: " + e.getMessage(), e);
        }
    }

    public synchronized void resetConfig() {
        try {
            if (Files.exists(configFile)) {
                Files.delete(configFile);
            }
        } catch (Exception ignored) {
        }
    }

    public static Path resolveDefaultConfigDirectory() {
        String envOverride = System.getenv("SECRET_VAULT_CONFIG_DIR");
        if (envOverride != null && !envOverride.isBlank()) {
            return Paths.get(envOverride);
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        String userHome = System.getProperty("user.home", ".");

        if (os.contains("mac") || os.contains("darwin")) {
            return Paths.get(userHome, "Library", "Application Support", APP_NAME);
        } else if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Paths.get(appData, APP_NAME);
            }
            return Paths.get(userHome, "AppData", "Roaming", APP_NAME);
        } else {
            // Linux / Unix
            String xdgConfig = System.getenv("XDG_CONFIG_HOME");
            if (xdgConfig != null && !xdgConfig.isBlank()) {
                return Paths.get(xdgConfig, "secretvault");
            }
            return Paths.get(userHome, ".config", "secretvault");
        }
    }

    private void applySecureDirectoryPermissions(Path path) {
        try {
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwx------");
            Files.setPosixFilePermissions(path, permissions);
        } catch (Exception ignored) {
        }
    }

    private void applySecureFilePermissions(Path path) {
        try {
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            File file = path.toFile();
            file.setReadable(true, true);
            file.setWritable(true, true);
            file.setExecutable(false, false);
        } catch (Exception ignored) {
        }
    }
}
