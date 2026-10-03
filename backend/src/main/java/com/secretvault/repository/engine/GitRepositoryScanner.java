package com.secretvault.repository.engine;

import com.secretvault.repository.model.ScanType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes scans across Git working trees, commit histories, branch diffs, and pull requests.
 */
@Component
public class GitRepositoryScanner {

    private static final Logger log = LoggerFactory.getLogger(GitRepositoryScanner.class);

    private final GitProcessExecutor gitExecutor;
    private final FileDiscoveryEngine fileDiscoveryEngine;
    private final SecretDetector secretDetector;

    public GitRepositoryScanner(
            GitProcessExecutor gitExecutor,
            FileDiscoveryEngine fileDiscoveryEngine,
            SecretDetector secretDetector) {
        this.gitExecutor = gitExecutor;
        this.fileDiscoveryEngine = fileDiscoveryEngine;
        this.secretDetector = secretDetector;
    }

    public record GitScanOutput(
            List<SecretDetectionResult> findings,
            int filesScanned,
            int commitsScanned,
            String headCommitSha
    ) {}

    public GitScanOutput scanWorkingTree(Path repoDir, String branch) {
        List<Path> files = fileDiscoveryEngine.discoverFiles(repoDir);
        List<SecretDetectionResult> findings = new ArrayList<>();

        String headSha = getHeadCommitSha(repoDir.toFile());

        for (Path file : files) {
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                String relativePath = repoDir.relativize(file).toString();
                List<SecretDetectionResult> results = secretDetector.scanContent(
                        content, relativePath, headSha, branch, "HEAD");
                findings.addAll(results);
            } catch (IOException e) {
                log.warn("Could not read file {} during scan: {}", file, e.getMessage());
            }
        }

        return new GitScanOutput(findings, files.size(), 1, headSha);
    }

    public GitScanOutput scanGitHistory(Path repoDir, String baseSha, String headSha, int maxCommits) {
        File repoFile = repoDir.toFile();
        String currentHead = headSha != null ? headSha : getHeadCommitSha(repoFile);

        // Fetch commit list
        String revRange;
        if (baseSha != null && !baseSha.isBlank() && !baseSha.equals(currentHead)) {
            revRange = baseSha + ".." + currentHead;
        } else {
            revRange = "HEAD";
        }

        GitProcessExecutor.ExecutionResult logResult = gitExecutor.execute(
                repoFile, "log", "--pretty=format:%H%x09%an", "-n", String.valueOf(maxCommits), revRange);

        if (!logResult.isSuccess() || logResult.stdout().isBlank()) {
            // Fallback to working tree scan if log yields nothing
            return scanWorkingTree(repoDir, "HEAD");
        }

        String[] commitLines = logResult.stdout().split("\n");
        List<SecretDetectionResult> historyFindings = new ArrayList<>();
        int commitsCount = 0;

        for (String line : commitLines) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\t");
            String commitSha = parts[0].trim();
            String author = parts.length > 1 ? parts[1].trim() : "Unknown";
            commitsCount++;

            // Inspect diff for this commit
            GitProcessExecutor.ExecutionResult showResult = gitExecutor.execute(
                    repoFile, "show", "--no-color", "--unified=1", commitSha);

            if (showResult.isSuccess()) {
                List<SecretDetectionResult> diffFindings = scanDiffContent(showResult.stdout(), commitSha, author);
                historyFindings.addAll(diffFindings);
            }
        }

        // Also scan current working tree to ensure complete coverage
        GitScanOutput workingTreeOutput = scanWorkingTree(repoDir, "HEAD");
        historyFindings.addAll(workingTreeOutput.findings());

        return new GitScanOutput(
                historyFindings,
                workingTreeOutput.filesScanned(),
                commitsCount,
                currentHead
        );
    }

    private List<SecretDetectionResult> scanDiffContent(String diffOutput, String commitSha, String author) {
        List<SecretDetectionResult> findings = new ArrayList<>();
        String[] lines = diffOutput.split("\n");
        String currentFile = "unknown";
        int currentLineNumber = 1;

        for (String line : lines) {
            if (line.startsWith("+++ b/")) {
                currentFile = line.substring(6).trim();
            } else if (line.startsWith("@@")) {
                // Parse line number if possible, e.g. @@ -10,4 +12,6 @@
                int plusIdx = line.indexOf('+');
                if (plusIdx > 0) {
                    try {
                        String numPart = line.substring(plusIdx + 1).split(",")[0].trim();
                        currentLineNumber = Integer.parseInt(numPart);
                    } catch (Exception ignored) {}
                }
            } else if (line.startsWith("+") && !line.startsWith("+++")) {
                String addedContent = line.substring(1);
                List<SecretDetectionResult> lineFindings = secretDetector.scanContent(
                        addedContent, currentFile, commitSha, "history", author);

                for (SecretDetectionResult res : lineFindings) {
                    // Update line number accurately from diff offset
                    findings.add(new SecretDetectionResult(
                            res.secretType(),
                            res.detectorType(),
                            res.fingerprint(),
                            res.maskedValue(),
                            currentFile,
                            currentLineNumber,
                            res.columnNumber(),
                            res.severity(),
                            res.confidence(),
                            res.entropy(),
                            commitSha,
                            "history",
                            author,
                            res.evidenceSummary() + " [Discovered in commit " + commitSha.substring(0, Math.min(8, commitSha.length())) + "]",
                            null
                    ));
                }
                currentLineNumber++;
            }
        }

        return findings;
    }

    public String getHeadCommitSha(File repoDir) {
        try {
            GitProcessExecutor.ExecutionResult res = gitExecutor.execute(repoDir, "rev-parse", "HEAD");
            if (res.isSuccess()) {
                return res.stdout().trim();
            }
        } catch (Exception e) {
            log.warn("Could not determine HEAD commit SHA in {}: {}", repoDir, e.getMessage());
        }
        return "0000000000000000000000000000000000000000";
    }
}
