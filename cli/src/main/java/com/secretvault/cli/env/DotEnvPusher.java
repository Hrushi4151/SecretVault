package com.secretvault.cli.env;

import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.SecretDtos;
import com.secretvault.cli.output.ConsolePrinter;
import com.secretvault.cli.output.TableFormatter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Handles pushing local .env key-value pairs to SecretVault.
 * Provides dry-run previews with zero plaintext secret exposure,
 * calculates creation vs update diffs, and executes batched remote updates.
 */
public class DotEnvPusher {

    private final SecretVaultApiClient apiClient;
    private final ConsolePrinter printer;

    public record PushDiff(
            Map<String, String> parsedEntries,
            List<String> toCreate,
            List<String> toUpdate,
            List<String> unchanged
    ) {}

    public DotEnvPusher(SecretVaultApiClient apiClient, ConsolePrinter printer) {
        this.apiClient = apiClient;
        this.printer = printer;
    }

    /**
     * Calculates diff between local .env file and active remote environment secrets.
     */
    public PushDiff calculateDiff(Path path, UUID workspaceId, UUID projectId, UUID environmentId) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null");
        }
        Path normalized = path.normalize();
        if (normalized.toString().contains("\0")) {
            throw new SecurityException("Path traversal attempt detected: null byte in path");
        }
        if (!Files.exists(normalized)) {
            throw new IllegalArgumentException("File not found: " + path);
        }
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("Not a regular file: " + path);
        }

        Map<String, String> localEntries = DotEnvParser.parse(normalized);
        if (localEntries.isEmpty()) {
            return new PushDiff(Collections.emptyMap(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }

        List<SecretDtos.SecretMetadataDto> remoteList = apiClient.listSecrets(workspaceId, projectId, environmentId, null, "ACTIVE");
        Set<String> remoteNames = new HashSet<>();
        if (remoteList != null) {
            for (SecretDtos.SecretMetadataDto meta : remoteList) {
                remoteNames.add(meta.name());
            }
        }

        List<String> toCreate = new ArrayList<>();
        List<String> toUpdate = new ArrayList<>();
        List<String> unchanged = new ArrayList<>();

        for (String key : localEntries.keySet()) {
            if (remoteNames.contains(key)) {
                toUpdate.add(key);
            } else {
                toCreate.add(key);
            }
        }

        return new PushDiff(localEntries, toCreate, toUpdate, unchanged);
    }

    /**
     * Previews planned changes in a clean table format. Zero plaintext secrets are displayed.
     */
    public void previewDiff(PushDiff diff) {
        printer.highlight("SecretVault .env push preview (Zero Plaintext Displayed):");

        TableFormatter table = new TableFormatter("ACTION", "SECRET NAME");

        for (String key : diff.toCreate()) {
            table.addRow("CREATE (v1)", key);
        }
        for (String key : diff.toUpdate()) {
            table.addRow("UPDATE (vN+1)", key);
        }
        for (String key : diff.unchanged()) {
            table.addRow("UNCHANGED", key);
        }

        printer.raw(table.render());
        printer.info(String.format("Summary: %d new secrets, %d updates.", diff.toCreate().size(), diff.toUpdate().size()));
    }

    /**
     * Executes the push by batch-importing the secrets via the REST API.
     */
    public void executePush(PushDiff diff, UUID workspaceId, UUID projectId, UUID environmentId, boolean overwrite) {
        if (diff == null || diff.parsedEntries().isEmpty()) {
            printer.info("No secrets to push.");
            return;
        }

        List<SecretDtos.CreateSecretRequest> allRequests = new ArrayList<>();
        for (Map.Entry<String, String> entry : diff.parsedEntries().entrySet()) {
            allRequests.add(new SecretDtos.CreateSecretRequest(
                    entry.getKey(),
                    entry.getValue(),
                    "Imported from .env"
            ));
        }

        // Chunk into batches of 100 to avoid backend batch-size limits
        int batchSize = 100;
        int totalCreated = 0;
        int totalUpdated = 0;
        int totalSkipped = 0;

        for (int i = 0; i < allRequests.size(); i += batchSize) {
            List<SecretDtos.CreateSecretRequest> batch = allRequests.subList(i, Math.min(i + batchSize, allRequests.size()));
            SecretDtos.BatchImportResponse response = apiClient.batchImportSecrets(workspaceId, projectId, environmentId, batch, overwrite);
            if (response != null) {
                totalCreated += response.createdCount();
                totalUpdated += response.updatedCount();
                totalSkipped += response.skippedCount();
            }
        }

        printer.success(String.format("Push complete: %d created, %d updated, %d skipped.", totalCreated, totalUpdated, totalSkipped));
    }
}
