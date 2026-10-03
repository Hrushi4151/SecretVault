package com.secretvault.repository.engine;

import com.secretvault.repository.model.ScanType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Adapter that provides a uniform interface across various repository source types:
 * local directories, Git repositories, pull requests, and uploaded archives.
 */
@Component
public class RepositorySourceAdapter {

    private static final Logger log = LoggerFactory.getLogger(RepositorySourceAdapter.class);

    private final GitProcessExecutor gitExecutor;
    private final ArchiveScanner archiveScanner;
    private final PathTraversalGuard pathGuard;

    public RepositorySourceAdapter(
            GitProcessExecutor gitExecutor,
            ArchiveScanner archiveScanner,
            PathTraversalGuard pathGuard) {
        this.gitExecutor = gitExecutor;
        this.archiveScanner = archiveScanner;
        this.pathGuard = pathGuard;
    }

    public record SourceContext(Path sourceDir, boolean requiresCleanup) implements AutoCloseable {
        @Override
        public void close() {
            if (requiresCleanup && sourceDir != null && Files.exists(sourceDir)) {
                try {
                    Files.walk(sourceDir)
                            .sorted((a, b) -> b.compareTo(a))
                            .map(Path::toFile)
                            .forEach(File::delete);
                } catch (Exception e) {
                    log.warn("Failed to clean up source context directory {}: {}", sourceDir, e.getMessage());
                }
            }
        }
    }

    public SourceContext acquireLocalDirectory(String localPath) {
        Path path = Path.of(localPath).toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new IllegalArgumentException("Target directory does not exist or is not a directory: " + localPath);
        }
        return new SourceContext(path, false);
    }

    public SourceContext acquireArchive(InputStream archiveStream) {
        try {
            Path tempDir = Files.createTempDirectory("secretvault-archive-scan-");
            archiveScanner.extractZipSafely(archiveStream, tempDir);
            return new SourceContext(tempDir, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract scan archive: " + e.getMessage(), e);
        }
    }

    public SourceContext cloneGitRepository(String cloneUrl, String branch) {
        try {
            Path tempDir = Files.createTempDirectory("secretvault-repo-scan-");
            // Perform safe clone with hooks disabled
            GitProcessExecutor.ExecutionResult res = gitExecutor.execute(
                    null, "clone", "--depth=100", "-b", (branch != null ? branch : "main"), cloneUrl, tempDir.toString());

            if (!res.isSuccess()) {
                // If branch main failed, try default clone
                res = gitExecutor.execute(null, "clone", "--depth=100", cloneUrl, tempDir.toString());
                if (!res.isSuccess()) {
                    archiveScanner.cleanDirectory(tempDir);
                    throw new IllegalStateException("Git clone failed: " + res.stderr());
                }
            }

            return new SourceContext(tempDir, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to acquire repository from " + cloneUrl + ": " + e.getMessage(), e);
        }
    }
}
