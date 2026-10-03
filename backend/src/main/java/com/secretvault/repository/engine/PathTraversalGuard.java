package com.secretvault.repository.engine;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Sandboxing security guard that prevents path traversal, symlink escapes,
 * and access outside the designated scan sandbox.
 */
@Component
public class PathTraversalGuard {

    public Path validateAndResolve(Path rootDir, String relativePath) {
        if (rootDir == null) {
            throw new IllegalArgumentException("Root directory cannot be null");
        }
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Relative path cannot be empty");
        }

        // Reject null byte injection
        if (relativePath.contains("\0")) {
            throw new SecurityException("Path traversal attempt detected: null byte injection");
        }

        Path resolved = rootDir.resolve(relativePath).normalize();

        try {
            Path canonicalRoot = rootDir.toRealPath();
            Path canonicalResolved = resolved.toRealPath();

            if (!canonicalResolved.startsWith(canonicalRoot)) {
                throw new SecurityException("Path traversal attempt detected: resolved path escapes sandbox root: " + relativePath);
            }

            // Verify not a symlink pointing outside root
            if (Files.isSymbolicLink(resolved)) {
                Path symlinkTarget = Files.readSymbolicLink(resolved);
                Path absoluteTarget = resolved.getParent().resolve(symlinkTarget).toRealPath();
                if (!absoluteTarget.startsWith(canonicalRoot)) {
                    throw new SecurityException("Symlink escape detected: " + relativePath + " points outside root");
                }
            }

            return canonicalResolved;
        } catch (IOException e) {
            // If file does not exist yet (e.g. target creation check)
            Path normalizedRoot = rootDir.toAbsolutePath().normalize();
            Path normalizedResolved = resolved.toAbsolutePath().normalize();
            if (!normalizedResolved.startsWith(normalizedRoot)) {
                throw new SecurityException("Path traversal attempt detected: path escapes sandbox: " + relativePath);
            }
            return normalizedResolved;
        }
    }

    public boolean isSafePath(Path rootDir, File file) {
        try {
            Path canonicalRoot = rootDir.toRealPath();
            Path canonicalFile = file.toPath().toRealPath();
            return canonicalFile.startsWith(canonicalRoot);
        } catch (IOException e) {
            return false;
        }
    }
}
