package com.secretvault.cli.runtime;

import com.secretvault.cli.client.SecretVaultApiClient;
import com.secretvault.cli.client.dto.SecretDtos;
import com.secretvault.cli.output.ConsolePrinter;

import com.secretvault.cli.security.SecretRevealHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Injects SecretVault secrets directly into child process memory.
 * Zero plaintext files are written to disk.
 */
public class SecretInjector {

    private final SecretVaultApiClient apiClient;
    private final ConsolePrinter printer;

    public SecretInjector(SecretVaultApiClient apiClient, ConsolePrinter printer) {
        this.apiClient = apiClient;
        this.printer = printer;
    }

    public Map<String, String> fetchAndBuildEnvironment(
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            List<String> explicitSecretAllowList,
            boolean overrideExisting
    ) {
        List<SecretDtos.SecretMetadataDto> metadataList = apiClient.listSecrets(workspaceId, projectId, environmentId, null, "ACTIVE");

        Set<String> allowedNames = (explicitSecretAllowList != null && !explicitSecretAllowList.isEmpty())
                ? explicitSecretAllowList.stream().collect(Collectors.toSet())
                : null;

        Map<String, String> injectedSecrets = new HashMap<>();

        for (SecretDtos.SecretMetadataDto meta : metadataList) {
            if (allowedNames != null && !allowedNames.contains(meta.name())) {
                continue; // Skip secret not in explicit allow list
            }

            try {
                SecretDtos.SecretRevealDto reveal = SecretRevealHelper.revealProtectedSecret(
                        apiClient, workspaceId, projectId, environmentId, meta.id(), null, "Runtime injection via CLI", printer
                );
                if (reveal != null && reveal.value() != null) {
                    injectedSecrets.put(meta.name(), reveal.value());
                }
            } catch (Exception e) {
                printer.warn("Unable to inject secret '" + meta.name() + "': " + e.getMessage());
            }
        }

        // Merge with existing host environment
        Map<String, String> combinedEnv = new HashMap<>(System.getenv());
        for (Map.Entry<String, String> entry : injectedSecrets.entrySet()) {
            if (overrideExisting || !combinedEnv.containsKey(entry.getKey())) {
                combinedEnv.put(entry.getKey(), entry.getValue());
            }
        }

        return combinedEnv;
    }
}
