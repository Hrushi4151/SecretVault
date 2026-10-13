package com.secretvault.repository.engine;

import com.secretvault.repository.model.ScanType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
    private final GitCloneUrlValidator urlValidator;
    private final HttpClient httpClient;

    public RepositorySourceAdapter(
            GitProcessExecutor gitExecutor,
            ArchiveScanner archiveScanner,
            PathTraversalGuard pathGuard,
            GitCloneUrlValidator urlValidator) {
        this.gitExecutor = gitExecutor;
        this.archiveScanner = archiveScanner;
        this.pathGuard = pathGuard;
        this.urlValidator = urlValidator;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
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
        urlValidator.validateCloneUrl(cloneUrl);
        String targetBranch = (branch != null && !branch.isBlank()) ? branch.trim() : "main";

        try {
            Path tempDir = Files.createTempDirectory("secretvault-repo-scan-");
            // Delete the empty temp directory so git clone can populate it cleanly
            Files.deleteIfExists(tempDir);

            try {
                // 1. First attempt: Sandboxed Git clone
                GitProcessExecutor.ExecutionResult res = gitExecutor.execute(
                        null, "clone", "--depth=50", "-b", targetBranch, cloneUrl, tempDir.toString());

                if (!res.isSuccess()) {
                    // If target branch failed, retry default clone
                    archiveScanner.cleanDirectory(tempDir);
                    Files.deleteIfExists(tempDir);
                    res = gitExecutor.execute(null, "clone", "--depth=50", cloneUrl, tempDir.toString());
                }

                if (res.isSuccess() && Files.exists(tempDir)) {
                    return new SourceContext(tempDir, true);
                }

                log.warn("Git clone failed (exit code {}). Attempting HTTP archive acquisition fallback for {}", res.exitCode(), cloneUrl);
            } catch (Exception gitEx) {
                log.warn("Git clone encountered error: {}. Attempting HTTP archive fallback for {}", gitEx.getMessage(), cloneUrl);
            }

            // 2. Resilient Fallback: If Git CLI fails/times out, fetch repo archive over HTTP
            archiveScanner.cleanDirectory(tempDir);
            Files.createDirectories(tempDir);

            boolean archiveSuccess = tryDownloadGitHubArchive(cloneUrl, targetBranch, tempDir);
            if (archiveSuccess) {
                log.info("Successfully acquired repository via HTTP archive fallback for {}", cloneUrl);
                return new SourceContext(tempDir, true);
            }

            archiveScanner.cleanDirectory(tempDir);
            throw new IllegalStateException("Failed to clone or download repository from " + cloneUrl);
        } catch (Exception e) {
            throw new RuntimeException("Failed to acquire repository from " + cloneUrl + ": " + e.getMessage(), e);
        }
    }

    private boolean tryDownloadGitHubArchive(String cloneUrl, String branch, Path targetDir) {
        if (!cloneUrl.contains("github.com")) {
            return false;
        }

        try {
            String path = cloneUrl;
            if (path.startsWith("https://github.com/")) {
                path = path.substring("https://github.com/".length());
            } else if (path.startsWith("http://github.com/")) {
                path = path.substring("http://github.com/".length());
            }
            if (path.endsWith(".git")) {
                path = path.substring(0, path.length() - 4);
            }

            String[] parts = path.split("/");
            if (parts.length < 2) {
                return false;
            }
            String owner = parts[0].trim();
            String repo = parts[1].trim();

            String token = System.getenv("GITHUB_TOKEN");
            if (token == null || token.isBlank()) {
                token = System.getProperty("GITHUB_TOKEN");
            }

            // Try GitHub zipball API endpoint first
            String zipUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/zipball/" + branch;
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(zipUrl))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "SecretVault-Scanner/1.0")
                    .GET()
                    .timeout(Duration.ofSeconds(30));

            if (token != null && !token.isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + token.trim());
            }

            HttpResponse<InputStream> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 200) {
                try (InputStream is = response.body()) {
                    archiveScanner.extractZipSafely(is, targetDir);
                    return true;
                }
            }

            // If API returned 404 for branch, try default archive URL
            String publicZipUrl = "https://codeload.github.com/" + owner + "/" + repo + "/zip/refs/heads/" + branch;
            HttpRequest publicReq = HttpRequest.newBuilder()
                    .uri(URI.create(publicZipUrl))
                    .header("User-Agent", "SecretVault-Scanner/1.0")
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<InputStream> publicResp = httpClient.send(publicReq, HttpResponse.BodyHandlers.ofInputStream());
            if (publicResp.statusCode() == 200) {
                try (InputStream is = publicResp.body()) {
                    archiveScanner.extractZipSafely(is, targetDir);
                    return true;
                }
            }

            // Fallback to main/master archive URL
            String masterZipUrl = "https://codeload.github.com/" + owner + "/" + repo + "/zip/refs/heads/master";
            HttpRequest masterReq = HttpRequest.newBuilder()
                    .uri(URI.create(masterZipUrl))
                    .header("User-Agent", "SecretVault-Scanner/1.0")
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<InputStream> masterResp = httpClient.send(masterReq, HttpResponse.BodyHandlers.ofInputStream());
            if (masterResp.statusCode() == 200) {
                try (InputStream is = masterResp.body()) {
                    archiveScanner.extractZipSafely(is, targetDir);
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("GitHub archive download attempt failed for {}: {}", cloneUrl, e.getMessage());
        }
        return false;
    }
}
