package com.secretvault.cli.env;

import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.SecretDtos;
import com.secretvault.cli.output.ConsolePrinter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DotEnvPusherTest {

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
    @DisplayName("Calculates create vs update diff against remote environment metadata")
    void calculatesDiffCorrectly(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, """
                NEW_SECRET=SUPER_SECRET_TEST_VALUE_123
                EXISTING_SECRET=updated_val
                """);

        UUID wsId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();

        SecretDtos.SecretMetadataDto existingMeta = new SecretDtos.SecretMetadataDto(
                UUID.randomUUID(), envId, "EXISTING_SECRET", "Desc", "ACTIVE", 1, UUID.randomUUID(), Instant.now(), Instant.now(), "••••••••"
        );

        when(apiClient.listSecrets(eq(wsId), eq(projId), eq(envId), isNull(), eq("ACTIVE")))
                .thenReturn(List.of(existingMeta));

        DotEnvPusher pusher = new DotEnvPusher(apiClient, printer);
        DotEnvPusher.PushDiff diff = pusher.calculateDiff(envFile, wsId, projId, envId);

        assertThat(diff.toCreate()).containsExactly("NEW_SECRET");
        assertThat(diff.toUpdate()).containsExactly("EXISTING_SECRET");
        assertThat(diff.parsedEntries())
                .containsEntry("NEW_SECRET", "SUPER_SECRET_TEST_VALUE_123")
                .containsEntry("EXISTING_SECRET", "updated_val");
    }

    @Test
    @DisplayName("Preview diff displays actions and keys with zero secret values displayed")
    void previewDiffNeverDisplaysSecretValues() {
        DotEnvPusher.PushDiff diff = new DotEnvPusher.PushDiff(
                Map.of("API_KEY", "SUPER_SECRET_TEST_VALUE_123", "DB_URL", "postgres://user:pass@host/db"),
                List.of("API_KEY"),
                List.of("DB_URL"),
                Collections.emptyList()
        );

        DotEnvPusher pusher = new DotEnvPusher(apiClient, printer);
        pusher.previewDiff(diff);

        String output = outStream.toString();
        assertThat(output).contains("SecretVault .env push preview (Zero Plaintext Displayed):");
        assertThat(output).contains("CREATE (v1)");
        assertThat(output).contains("API_KEY");
        assertThat(output).contains("UPDATE (vN+1)");
        assertThat(output).contains("DB_URL");
        assertThat(output).contains("Summary: 1 new secrets, 1 updates.");

        // Critical security assertion: plaintext secret values must NEVER appear in output
        assertThat(output).doesNotContain("SUPER_SECRET_TEST_VALUE_123");
        assertThat(output).doesNotContain("postgres://user:pass@host/db");
        assertThat(errStream.toString()).doesNotContain("SUPER_SECRET_TEST_VALUE_123");
    }

    @Test
    @DisplayName("Executes push and batches create/update requests to API client")
    void executesPushWithBatching() {
        UUID wsId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();

        DotEnvPusher.PushDiff diff = new DotEnvPusher.PushDiff(
                Map.of("SECRET_A", "valA", "SECRET_B", "valB"),
                List.of("SECRET_A"),
                List.of("SECRET_B"),
                Collections.emptyList()
        );

        when(apiClient.batchImportSecrets(eq(wsId), eq(projId), eq(envId), anyList(), eq(true)))
                .thenReturn(new SecretDtos.BatchImportResponse(1, 1, 0, List.of("SECRET_A"), List.of("SECRET_B"), Collections.emptyList()));

        DotEnvPusher pusher = new DotEnvPusher(apiClient, printer);
        pusher.executePush(diff, wsId, projId, envId, true);

        ArgumentCaptor<List<SecretDtos.CreateSecretRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(apiClient).batchImportSecrets(eq(wsId), eq(projId), eq(envId), captor.capture(), eq(true));

        List<SecretDtos.CreateSecretRequest> captured = captor.getValue();
        assertThat(captured).hasSize(2);

        assertThat(outStream.toString()).contains("Push complete: 1 created, 1 updated, 0 skipped.");
    }

    @Test
    @DisplayName("Rejects non-existent file or path traversal with null bytes")
    void rejectsInvalidFiles(@TempDir Path tempDir) {
        DotEnvPusher pusher = new DotEnvPusher(apiClient, printer);
        UUID wsId = UUID.randomUUID();
        UUID projId = UUID.randomUUID();
        UUID envId = UUID.randomUUID();

        assertThatThrownBy(() -> pusher.calculateDiff(tempDir.resolve("non_existent.env"), wsId, projId, envId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File not found");

        assertThatThrownBy(() -> pusher.calculateDiff(Path.of("test\0.env"), wsId, projId, envId))
                .isInstanceOf(Exception.class);
    }
}
