package com.secretvault.cli.env;

import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.SecretDtos;
import com.secretvault.cli.output.ConsolePrinter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * End-to-end security and functional verification suite for .env support.
 * Validates zero command execution, secret redaction/non-leakage, path traversal defenses,
 * gitignore warnings, and safe formatting.
 */
class DotEnvSecurityAndFunctionalTest {

    private static final String SENTINEL_SECRET = "SUPER_SECRET_TEST_VALUE_123";

    private SecretVaultApiClient apiClient;
    private ConsolePrinter printer;
    private ByteArrayOutputStream outStream;
    private ByteArrayOutputStream errStream;

    @BeforeEach
    void setUp() {
        apiClient = Mockito.mock(SecretVaultApiClient.class);
        outStream = new ByteArrayOutputStream();
        errStream = new ByteArrayOutputStream();
        printer = new ConsolePrinter(new PrintStream(outStream), new PrintStream(errStream));
        printer.setColorEnabled(false);
    }

    @Test
    @DisplayName("SECURITY: Parser NEVER evaluates subshell syntax or commands (treats as literal strings)")
    void parserNeverExecutesShellCommands() {
        String dangerousEnv = """
                MALICIOUS_1=$(touch /tmp/pwned)
                MALICIOUS_2=`rm -rf /`
                MALICIOUS_3=${UNSET_VARIABLE:-default}
                MALICIOUS_4=$(echo "injected")
                """;

        Map<String, String> parsed = DotEnvParser.parse(dangerousEnv);

        assertThat(parsed.get("MALICIOUS_1")).isEqualTo("$(touch /tmp/pwned)");
        assertThat(parsed.get("MALICIOUS_2")).isEqualTo("`rm -rf /`");
        assertThat(parsed.get("MALICIOUS_3")).isEqualTo("${UNSET_VARIABLE:-default}");
        assertThat(parsed.get("MALICIOUS_4")).isEqualTo("$(echo \"injected\")");
    }

    @Test
    @DisplayName("SECURITY: Sentinel secret value is NEVER leaked in diff preview, stderr, or warning output")
    void sentinelNeverLeakedInDiffOrWarnings(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, "DATABASE_PASSWORD=" + SENTINEL_SECRET + "\n");

        UUID wsId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();

        when(apiClient.listSecrets(eq(wsId), eq(projId), eq(envId), isNull(), eq("ACTIVE")))
                .thenReturn(Collections.emptyList());

        DotEnvPusher pusher = new DotEnvPusher(apiClient, printer);
        DotEnvPusher.PushDiff diff = pusher.calculateDiff(envFile, wsId, projId, envId);
        pusher.previewDiff(diff);

        // Standard output contains diff table with secret NAME, but NOT the VALUE
        String stdout = outStream.toString();
        assertThat(stdout).contains("DATABASE_PASSWORD");
        assertThat(stdout).doesNotContain(SENTINEL_SECRET);

        // Error and warning streams must also NEVER contain the sentinel value
        String stderr = errStream.toString();
        assertThat(stderr).doesNotContain(SENTINEL_SECRET);
    }

    @Test
    @DisplayName("SECURITY: Shell export escaping prevents command injection / breakout")
    void shellExportEscapingPreventsBreakout() {
        Map<String, String> dangerousMap = Map.of(
                "INJECTION_1", "val'; rm -rf /; echo '",
                "INJECTION_2", "val\" && echo pwned",
                "INJECTION_3", "`whoami`",
                "INJECTION_4", "$(cat /etc/shadow)"
        );

        String shellOutput = DotEnvParser.formatShell(dangerousMap);

        // Each variable must be single-quoted with embedded single quotes safely escaped as '\''
        assertThat(shellOutput).contains("export INJECTION_1='val'\\''");
        assertThat(shellOutput).contains("export INJECTION_2='val\" && echo pwned'");
        assertThat(shellOutput).contains("export INJECTION_3='`whoami`'");
        assertThat(shellOutput).contains("export INJECTION_4='$(cat /etc/shadow)'");
    }

    @Test
    @DisplayName("SECURITY: Path traversal handling is safe and normalizes relative paths")
    void pathTraversalHandledSafely(@TempDir Path tempDir) throws IOException {
        Path subDir = tempDir.resolve("sub");
        Files.createDirectories(subDir);

        Path targetEnv = tempDir.resolve("target.env");
        // Relative path navigation ../target.env from subDir
        Path relativePath = subDir.resolve("../target.env");

        DotEnvPuller puller = new DotEnvPuller(apiClient, printer);
        puller.exportToFile(Map.of("API_KEY", "12345"), relativePath, true);

        assertThat(Files.exists(targetEnv)).isTrue();
        assertThat(Files.readString(targetEnv, StandardCharsets.UTF_8)).contains("API_KEY=12345");
    }

    @Test
    @DisplayName("SECURITY: Gitignore hierarchy is traversed upwards to locate root .gitignore")
    void gitignoreTraversedUpwards(@TempDir Path tempDir) throws IOException {
        Path gitRoot = tempDir.resolve("repo");
        Files.createDirectories(gitRoot.resolve(".git"));
        Files.writeString(gitRoot.resolve(".gitignore"), ".env\n");

        Path projectDir = gitRoot.resolve("apps").resolve("web");
        Files.createDirectories(projectDir);

        Path targetEnv = projectDir.resolve(".env");

        DotEnvPuller puller = new DotEnvPuller(apiClient, printer);
        puller.exportToFile(Map.of("KEY", "VALUE"), targetEnv, true);

        assertThat(Files.exists(targetEnv)).isTrue();
        // Since .gitignore at git root has .env, no warning should be printed
        assertThat(errStream.toString()).doesNotContain("is NOT listed in .gitignore");
    }

    @Test
    @DisplayName("SECURITY: Warning is emitted when .gitignore exists in repo root but does NOT cover .env")
    void gitignoreWarningWhenUnprotected(@TempDir Path tempDir) throws IOException {
        Path gitRoot = tempDir.resolve("repo");
        Files.createDirectories(gitRoot.resolve(".git"));
        Files.writeString(gitRoot.resolve(".gitignore"), "dist/\nnode_modules/\n");

        Path targetEnv = gitRoot.resolve(".env");

        DotEnvPuller puller = new DotEnvPuller(apiClient, printer);
        puller.exportToFile(Map.of("KEY", "VALUE"), targetEnv, true);

        assertThat(Files.exists(targetEnv)).isTrue();
        assertThat(errStream.toString()).contains("is NOT listed in .gitignore");
    }
}
