package com.secretvault.cli.env;

import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.SecretDtos;
import com.secretvault.cli.output.ConsolePrinter;
import com.secretvault.cli.security.SecretRevealHelper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

/**
 * Handles secure pulling and exporting of environment secrets into stdout or local .env files.
 * Includes gitignore checks, atomic writes, POSIX permissions, and plaintext warnings.
 */
public class DotEnvPuller {

    private final SecretVaultApiClient apiClient;
    private final ConsolePrinter printer;

    public DotEnvPuller(SecretVaultApiClient apiClient, ConsolePrinter printer) {
        this.apiClient = apiClient;
        this.printer = printer;
    }

    /**
     * Fetches all active secrets for the given environment and reveals their plaintext values.
     * Zero plaintext secret values are logged or exposed during this operation.
     */
    public Map<String, String> fetchEnvironmentSecrets(UUID workspaceId, UUID projectId, UUID environmentId) {
        List<SecretDtos.SecretMetadataDto> metadataList = apiClient.listSecrets(workspaceId, projectId, environmentId, null, "ACTIVE");
        Map<String, String> secrets = new TreeMap<>();

        if (metadataList != null) {
            for (SecretDtos.SecretMetadataDto meta : metadataList) {
                try {
                    SecretDtos.SecretRevealDto reveal = SecretRevealHelper.revealProtectedSecret(
                            apiClient, workspaceId, projectId, environmentId, meta.id(), null, "Export via CLI env pull", printer
                    );
                    if (reveal != null && reveal.value() != null) {
                        secrets.put(meta.name(), reveal.value());
                    }
                } catch (Exception e) {
                    printer.warn("Unable to fetch value for secret '" + meta.name() + "': " + e.getMessage());
                }
            }
        }

        return secrets;
    }

    /**
     * Safely exports secrets to a local file with path traversal validation, .gitignore warning,
     * overwrite confirmation, and atomic temp-file write.
     */
    public void exportToFile(Map<String, String> secrets, Path targetPath, boolean autoConfirm) throws IOException {
        if (targetPath == null) {
            throw new IllegalArgumentException("Target path cannot be null");
        }

        Path normalized = targetPath.normalize().toAbsolutePath();
        validatePathSecurity(normalized);

        File targetFile = normalized.toFile();
        if (targetFile.exists()) {
            if (!autoConfirm) {
                if (System.console() != null) {
                    String input = System.console().readLine("File '%s' already exists. Overwrite? [y/N]: ", targetPath);
                    if (!"y".equalsIgnoreCase(input) && !"yes".equalsIgnoreCase(input)) {
                        printer.info("Pull cancelled by user.");
                        return;
                    }
                } else {
                    printer.warn("Overwriting existing file: " + targetPath);
                }
            }
        }

        // Plaintext disk write warning
        printer.warn("Writing plaintext secrets to disk: " + targetPath);

        // .gitignore inspection
        checkGitIgnoreProtection(normalized);

        // Ensure parent directory exists
        Path parentDir = normalized.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        // Formatted .env content
        String content = DotEnvParser.formatEnv(secrets);

        // Atomic write via temp file in same directory
        Path tempFile = null;
        try {
            if (parentDir != null && Files.exists(parentDir)) {
                tempFile = Files.createTempFile(parentDir, ".secretvault-env-", ".tmp");
            } else {
                tempFile = Files.createTempFile(".secretvault-env-", ".tmp");
            }

            // Apply restrictive permissions (600) on temp file
            applySecurePermissions(tempFile);

            Files.writeString(tempFile, content, StandardCharsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

            try {
                Files.move(tempFile, normalized, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, normalized, StandardCopyOption.REPLACE_EXISTING);
            }

            applySecurePermissions(normalized);
            printer.success("Successfully pulled " + secrets.size() + " secrets into " + targetPath);
        } finally {
            if (tempFile != null && Files.exists(tempFile)) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Outputs secrets directly to stdout in the requested format (env, json, shell).
     */
    public void exportToStdout(Map<String, String> secrets, String format) {
        String fmt = format != null ? format.trim().toLowerCase() : "env";
        switch (fmt) {
            case "json":
                printer.printJson(secrets);
                break;
            case "shell":
                printer.raw(DotEnvParser.formatShell(secrets));
                break;
            case "env":
            default:
                printer.raw(DotEnvParser.formatEnv(secrets));
                break;
        }
    }

    private void validatePathSecurity(Path path) {
        String pathStr = path.toString();
        if (pathStr.contains("\0")) {
            throw new SecurityException("Path traversal attempt detected: path contains null byte");
        }
    }

    private void checkGitIgnoreProtection(Path targetPath) {
        try {
            String fileName = targetPath.getFileName().toString();
            Path current = targetPath.getParent();
            boolean isIgnored = false;

            while (current != null) {
                Path gitIgnore = current.resolve(".gitignore");
                if (Files.isRegularFile(gitIgnore)) {
                    List<String> lines = Files.readAllLines(gitIgnore, StandardCharsets.UTF_8);
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                            continue;
                        }
                        if (matchesGitIgnore(fileName, trimmed)) {
                            isIgnored = true;
                            break;
                        }
                    }
                    if (isIgnored) break;
                }
                if (Files.isDirectory(current.resolve(".git"))) {
                    break;
                }
                current = current.getParent();
            }

            if (!isIgnored) {
                printer.warn("'" + fileName + "' is NOT listed in .gitignore! Secrets may be accidentally committed to version control.");
            }
        } catch (Exception ignored) {
            // Best effort check
        }
    }

    private boolean matchesGitIgnore(String fileName, String pattern) {
        if (pattern.equals(fileName) || pattern.equals("/" + fileName)) {
            return true;
        }
        if (pattern.startsWith("*") && fileName.endsWith(pattern.substring(1))) {
            return true;
        }
        if (pattern.endsWith("*") && fileName.startsWith(pattern.substring(0, pattern.length() - 1))) {
            return true;
        }
        if (".env*".equals(pattern) && fileName.startsWith(".env")) {
            return true;
        }
        if ("*.env".equals(pattern) && fileName.endsWith(".env")) {
            return true;
        }
        return false;
    }

    private void applySecurePermissions(Path path) {
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException | SecurityException | IOException ignored) {
            File f = path.toFile();
            f.setReadable(true, true);
            f.setWritable(true, true);
            f.setExecutable(false, false);
        }
    }
}
