package com.secretvault.repository.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Sandboxed Git process executor that enforces strict security controls:
 * - Uses ProcessBuilder with discrete arguments (NO shell execution /bin/sh -c)
 * - Disables git hooks via -c core.hooksPath=/dev/null
 * - Isolates system configuration via GIT_CONFIG_NOSYSTEM=1
 * - Disables interactive credential prompts via GIT_TERMINAL_PROMPT=0
 * - Enforces process timeouts and output limits to prevent resource exhaustion
 */
@Component
public class GitProcessExecutor {

    private static final Logger log = LoggerFactory.getLogger(GitProcessExecutor.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_OUTPUT_CHARS = 10_000_000; // 10MB text limit

    public record ExecutionResult(int exitCode, String stdout, String stderr) {
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    public ExecutionResult execute(File workingDir, String... gitArgs) {
        return executeWithTimeout(workingDir, DEFAULT_TIMEOUT, gitArgs);
    }

    public ExecutionResult executeWithTimeout(File workingDir, Duration timeout, String... gitArgs) {
        List<String> command = new ArrayList<>();
        command.add("git");
        // Security flags to prevent hook execution and system config override
        command.add("-c");
        command.add("core.hooksPath=/dev/null");
        command.addAll(Arrays.asList(gitArgs));

        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDir != null) {
            pb.directory(workingDir);
        }

        // Environment sandboxing
        pb.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");
        pb.environment().put("GIT_ASKPASS", "");

        try {
            Process process = pb.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread outThread = new Thread(() -> readStream(process.getInputStream(), stdout));
            Thread errThread = new Thread(() -> readStream(process.getErrorStream(), stderr));

            outThread.start();
            errThread.start();

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Git command timed out after " + timeout.getSeconds() + "s: " + String.join(" ", gitArgs));
            }

            outThread.join(1000);
            errThread.join(1000);

            return new ExecutionResult(process.exitValue(), stdout.toString(), stderr.toString());
        } catch (Exception e) {
            log.error("Failed to execute Git command [{}]: {}", String.join(" ", gitArgs), e.getMessage());
            throw new RuntimeException("Git execution failure: " + e.getMessage(), e);
        }
    }

    private void readStream(java.io.InputStream is, StringBuilder sb) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                if (sb.length() + read > MAX_OUTPUT_CHARS) {
                    sb.append("\n[OUTPUT TRUNCATED]");
                    break;
                }
                sb.append(buffer, 0, read);
            }
        } catch (Exception ignored) {
        }
    }
}
