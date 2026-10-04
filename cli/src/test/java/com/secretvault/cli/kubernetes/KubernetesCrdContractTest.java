package com.secretvault.cli.kubernetes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KubernetesCrdContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Validates SecretVaultSecret OpenAPI v3 structural schema exists and enforces security invariants")
    void validatesSecretVaultSecretCrdManifest() throws Exception {
        Path crdPath = Paths.get("..", "infrastructure", "kubernetes", "config", "crd", "bases", "secretvault.io_secretvaultsecrets.yaml");
        if (!Files.exists(crdPath)) {
            crdPath = Paths.get("infrastructure", "kubernetes", "config", "crd", "bases", "secretvault.io_secretvaultsecrets.yaml");
        }
        assertThat(Files.exists(crdPath)).isTrue();

        String yaml = Files.readString(crdPath);
        assertThat(yaml).contains("group: secretvault.io");
        assertThat(yaml).contains("kind: SecretVaultSecret");
        assertThat(yaml).contains("name: v1alpha1");
        assertThat(yaml).contains("shortNames:\n      - svs");

        // Zero Plaintext & Credential Invariants
        List<String> prohibited = List.of("plaintext", "secretValue", "password", "apiToken", "refreshToken", "jwtToken", "masterKey");
        for (String field : prohibited) {
            assertThat(yaml).doesNotContain(field + ":");
        }

        // Required fields
        assertThat(yaml).contains("- workspace");
        assertThat(yaml).contains("- project");
        assertThat(yaml).contains("- environment");
        assertThat(yaml).contains("- secretName");
    }

    @Test
    @DisplayName("Validates SecretVaultSync OpenAPI v3 structural schema exists and enforces security invariants")
    void validatesSecretVaultSyncCrdManifest() throws Exception {
        Path crdPath = Paths.get("..", "infrastructure", "kubernetes", "config", "crd", "bases", "secretvault.io_secretvaultsyncs.yaml");
        if (!Files.exists(crdPath)) {
            crdPath = Paths.get("infrastructure", "kubernetes", "config", "crd", "bases", "secretvault.io_secretvaultsyncs.yaml");
        }
        assertThat(Files.exists(crdPath)).isTrue();

        String yaml = Files.readString(crdPath);
        assertThat(yaml).contains("group: secretvault.io");
        assertThat(yaml).contains("kind: SecretVaultSync");
        assertThat(yaml).contains("name: v1alpha1");
        assertThat(yaml).contains("shortNames:\n      - svsync");

        // Zero Plaintext & Credential Invariants
        List<String> prohibited = List.of("plaintext", "secretValue", "password", "apiToken", "refreshToken", "jwtToken", "masterKey");
        for (String field : prohibited) {
            assertThat(yaml).doesNotContain(field + ":");
        }

        // Required fields
        assertThat(yaml).contains("- workspace");
        assertThat(yaml).contains("- project");
        assertThat(yaml).contains("- environment");
        assertThat(yaml).contains("- target");
    }
}
