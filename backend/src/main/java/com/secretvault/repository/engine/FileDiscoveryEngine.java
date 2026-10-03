package com.secretvault.repository.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Discovers and filters files within a target scan directory.
 * Safely ignores binaries, build artifacts, dependency directories, and files exceeding size limits.
 */
@Component
public class FileDiscoveryEngine {

    private static final Logger log = LoggerFactory.getLogger(FileDiscoveryEngine.class);

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB per file
    private static final int MAX_TOTAL_FILES = 25_000;

    private static final Set<String> EXCLUDED_DIR_NAMES = Set.of(
            ".git", "node_modules", "target", "build", "dist", "vendor",
            ".gradle", ".idea", ".vscode", "coverage", ".next", ".nuxt", "bin", "obj"
    );

    private static final Set<String> EXCLUDED_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg", ".webp",
            ".pdf", ".zip", ".tar", ".gz", ".jar", ".war", ".ear",
            ".class", ".pyc", ".exe", ".dll", ".so", ".dylib", ".bin",
            ".woff", ".woff2", ".ttf", ".eot", ".mp4", ".mov", ".avi"
    );

    public List<Path> discoverFiles(Path rootDir) {
        List<Path> discovered = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(rootDir)) {
            stream.forEach(path -> {
                if (discovered.size() >= MAX_TOTAL_FILES) {
                    return;
                }

                File file = path.toFile();
                if (file.isDirectory()) {
                    return;
                }

                // Exclude directory check
                if (isInExcludedDirectory(path, rootDir)) {
                    return;
                }

                // Exclude binary extensions
                String name = file.getName().toLowerCase();
                boolean excludedExt = EXCLUDED_EXTENSIONS.stream().anyMatch(name::endsWith);
                if (excludedExt) {
                    return;
                }

                // Check file size
                if (file.length() > MAX_FILE_SIZE_BYTES || file.length() == 0) {
                    return;
                }

                // Check if binary file by probing content
                if (isBinaryFile(path)) {
                    return;
                }

                discovered.add(path);
            });
        } catch (IOException e) {
            log.error("Error during file discovery in {}: {}", rootDir, e.getMessage());
        }

        return discovered;
    }

    private boolean isInExcludedDirectory(Path path, Path rootDir) {
        Path relative = rootDir.relativize(path);
        for (Path part : relative) {
            if (EXCLUDED_DIR_NAMES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    public boolean isBinaryFile(Path path) {
        try {
            byte[] bytes = new byte[1024];
            int read;
            try (var is = Files.newInputStream(path)) {
                read = is.read(bytes);
            }
            if (read <= 0) return false;

            int nullCount = 0;
            for (int i = 0; i < read; i++) {
                if (bytes[i] == 0) {
                    nullCount++;
                }
            }
            // If more than 1% null bytes, consider it binary
            return ((double) nullCount / read) > 0.01;
        } catch (Exception e) {
            return true; // if cannot read safely, skip
        }
    }
}
