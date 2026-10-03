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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class DotEnvPullerTest {

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
    @DisplayName("Fetches and reveals all active environment secrets")
    void fetchesActiveSecrets() {
        UUID wsId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();
        UUID secret1Id = UUID.randomUUID();
        UUID secret2Id = UUID.randomUUID();

        SecretDtos.SecretMetadataDto meta1 = new SecretDtos.SecretMetadataDto(
                secret1Id, envId, "DB_PASS", "Database Password", "ACTIVE", 1, UUID.randomUUID(), Instant.now(), Instant.now(), "••••••••"
        );
        SecretDtos.SecretMetadataDto meta2 = new SecretDtos.SecretMetadataDto(
                secret2Id, envId, "API_KEY", "API Key", "ACTIVE", 2, UUID.randomUUID(), Instant.now(), Instant.now(), "••••••••"
        );

        when(apiClient.listSecrets(eq(wsId), eq(projId), eq(envId), isNull(), eq("ACTIVE")))
                .thenReturn(List.of(meta1, meta2));

        when(apiClient.revealSecret(eq(wsId), eq(projId), eq(envId), eq(secret1Id), isNull()))
                .thenReturn(new SecretDtos.SecretRevealDto(secret1Id, envId, "DB_PASS", 1, "SUPER_SECRET_TEST_VALUE_123", Instant.now()));

        when(apiClient.revealSecret(eq(wsId), eq(projId), eq(envId), eq(secret2Id), isNull()))
                .thenReturn(new SecretDtos.SecretRevealDto(secret2Id, envId, "API_KEY", 2, "my-api-key-value", Instant.now()));

        DotEnvPuller puller = new DotEnvPuller(apiClient, printer);
        Map<String, String> result = puller.fetchEnvironmentSecrets(wsId, projId, envId);

        assertThat(result)
                .containsEntry("DB_PASS", "SUPER_SECRET_TEST_VALUE_123")
                .containsEntry("API_KEY", "my-api-key-value");

        // Verify sentinel did not leak to err output
        assertThat(errStream.toString()).doesNotContain("SUPER_SECRET_TEST_VALUE_123");
    }

    @Test
    @DisplayName("Exports secrets to local file with .gitignore warning when not ignored")
    void exportsToFileWithGitIgnoreWarning(@TempDir Path tempDir) throws IOException {
        Path targetFile = tempDir.resolve(".env");
        Map<String, String> secrets = Map.of(
                "DATABASE_URL", "postgres://localhost/db",
                "PASSWORD", "SUPER_SECRET_TEST_VALUE_123"
        );

        DotEnvPuller puller = new DotEnvPuller(apiClient, printer);
        puller.exportToFile(secrets, targetFile, true);

        assertThat(Files.exists(targetFile)).isTrue();
        String content = Files.readString(targetFile, StandardCharsets.UTF_8);
        assertThat(content).contains("DATABASE_URL=postgres://localhost/db");
        assertThat(content).contains("PASSWORD=SUPER_SECRET_TEST_VALUE_123");

        // Should warn that .env is NOT in .gitignore
        assertThat(errStream.toString()).contains("is NOT listed in .gitignore");
        // Sentinel value must NOT appear in any warning or error stream
        assertThat(errStream.toString()).doesNotContain("SUPER_SECRET_TEST_VALUE_123");
    }

    @Test
    @DisplayName("Suppresses .gitignore warning when .env is listed in .gitignore")
    void suppressesGitIgnoreWarningWhenIgnored(@TempDir Path tempDir) throws IOException {
        Path gitIgnore = tempDir.resolve(".gitignore");
        Files.writeString(gitIgnore, "# Ignored files\n.env\n.env.local\nnode_modules\n");

        Path targetFile = tempDir.resolve(".env");
        Map<String, String> secrets = Map.of("KEY", "SUPER_SECRET_TEST_VALUE_123");

        DotEnvPuller puller = new DotEnvPuller(apiClient, printer);
        puller.exportToFile(secrets, targetFile, true);

        assertThat(Files.exists(targetFile)).isTrue();
        // Should NOT warn about .gitignore
        assertThat(errStream.toString()).doesNotContain("is NOT listed in .gitignore");
        assertThat(errStream.toString()).doesNotContain("SUPER_SECRET_TEST_VALUE_123");
    }

    @Test
    @DisplayName("Rejects null bytes in target path (path traversal defense)")
    void rejectsNullBytePath() {
        DotEnvPuller puller = new DotEnvPuller(apiClient, printer);
        assertThatThrownBy(() -> puller.exportToFile(Map.of("K", "V"), Path.of("test\0.env"), true))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Exports secrets to stdout in requested format")
    void exportsToStdoutInVariousFormats() {
        Map<String, String> secrets = Map.of("PORT", "8080", "KEY", "val");
        DotEnvPuller puller = new DotEnvPuller(apiClient, printer);

        puller.exportToStdout(secrets, "env");
        assertThat(outStream.toString()).contains("PORT=8080");

        outStream.reset();
        puller.exportToStdout(secrets, "shell");
        assertThat(outStream.toString()).contains("export PORT='8080'");

        outStream.reset();
        puller.exportToStdout(secrets, "json");
        assertThat(outStream.toString()).contains("\"PORT\" : \"8080\"");
    }
}
