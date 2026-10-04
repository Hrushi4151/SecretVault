package com.secretvault.cli.terraform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Terraform Provider Production Contract Tests")
class TerraformProviderContractTest {

    private static final Path TF_BASE = Path.of("..", "infrastructure", "terraform");

    @Test
    @DisplayName("Verify Terraform Provider Go module and source structure")
    void testProviderStructure() {
        assertTrue(Files.exists(TF_BASE.resolve("go.mod")), "go.mod must exist");
        assertTrue(Files.exists(TF_BASE.resolve("main.go")), "main.go must exist");
        assertTrue(Files.exists(TF_BASE.resolve(Path.of("internal", "provider", "provider.go"))), "provider.go must exist");
        assertTrue(Files.exists(TF_BASE.resolve(Path.of("internal", "client", "client.go"))), "client.go must exist");
        assertTrue(Files.exists(TF_BASE.resolve(Path.of("internal", "client", "models.go"))), "models.go must exist");
        assertTrue(Files.exists(TF_BASE.resolve(Path.of("internal", "client", "errors.go"))), "errors.go must exist");
        assertTrue(Files.exists(TF_BASE.resolve(Path.of("internal", "client", "auth.go"))), "auth.go must exist");
    }

    @Test
    @DisplayName("Verify All 5 Terraform Resources are implemented")
    void testResourcesExist() {
        Path resDir = TF_BASE.resolve(Path.of("internal", "resources"));
        assertTrue(Files.exists(resDir.resolve("resource_project.go")), "resource_project.go must exist");
        assertTrue(Files.exists(resDir.resolve("resource_environment.go")), "resource_environment.go must exist");
        assertTrue(Files.exists(resDir.resolve("resource_secret.go")), "resource_secret.go must exist");
        assertTrue(Files.exists(resDir.resolve("resource_machine_identity.go")), "resource_machine_identity.go must exist");
        assertTrue(Files.exists(resDir.resolve("resource_provider_integration.go")), "resource_provider_integration.go must exist");
    }

    @Test
    @DisplayName("Verify All 4 Terraform Data Sources are implemented")
    void testDataSourcesExist() {
        Path dsDir = TF_BASE.resolve(Path.of("internal", "datasources"));
        assertTrue(Files.exists(dsDir.resolve("datasource_workspace.go")), "datasource_workspace.go must exist");
        assertTrue(Files.exists(dsDir.resolve("datasource_project.go")), "datasource_project.go must exist");
        assertTrue(Files.exists(dsDir.resolve("datasource_environment.go")), "datasource_environment.go must exist");
        assertTrue(Files.exists(dsDir.resolve("datasource_secret.go")), "datasource_secret.go must exist");
    }

    @Test
    @DisplayName("Verify Zero-Plaintext Read and Sensitive State Invariants in Secret Resource")
    void testSecretResourceSecurityInvariants() throws Exception {
        String secretCode = Files.readString(TF_BASE.resolve(Path.of("internal", "resources", "resource_secret.go")));

        assertTrue(secretCode.contains("Sensitive:   true"), "Secret value attribute must be marked Sensitive: true");
        assertTrue(secretCode.contains("GetSecretMetadata"), "Read() must invoke GetSecretMetadata only");
        assertFalse(secretCode.contains("RevealSecret"), "Secret resource must never invoke reveal endpoints");
        assertTrue(secretCode.contains("fingerprint"), "Must compute SHA256 fingerprint for drift tracking");
    }

    @Test
    @DisplayName("Verify Secret Data Source is strictly Metadata-Only with zero plaintext attributes")
    void testSecretDataSourceZeroPlaintext() throws Exception {
        String dsCode = Files.readString(TF_BASE.resolve(Path.of("internal", "datasources", "datasource_secret.go")));

        assertFalse(dsCode.contains("\"value\":"), "Secret data source must NOT expose value attribute");
        assertTrue(dsCode.contains("\"version\":"), "Secret data source must expose version metadata");
        assertTrue(dsCode.contains("\"content_type\":"), "Secret data source must expose contentType");
        assertTrue(dsCode.contains("GetSecretMetadata"), "Secret data source must only call metadata endpoint");
    }

    @Test
    @DisplayName("Verify Client HTTP mappings match Spring Boot Backend controllers")
    void testBackendEndpointMappings() throws Exception {
        String clientCode = Files.readString(TF_BASE.resolve(Path.of("internal", "client", "client.go")));

        assertTrue(clientCode.contains("/api/v1/workspaces/%s"), "Must match WorkspaceController");
        assertTrue(clientCode.contains("/api/v1/workspaces/%s/projects"), "Must match ProjectController");
        assertTrue(clientCode.contains("/api/v1/workspaces/%s/projects/%s/environments"), "Must match EnvironmentController");
        assertTrue(clientCode.contains("/api/v1/workspaces/%s/projects/%s/environments/%s/secrets"), "Must match SecretController");
        assertTrue(clientCode.contains("/api/v1/workspaces/%s/machines"), "Must match MachineIdentityController");
        assertTrue(clientCode.contains("/api/v1/workspaces/%s/integrations"), "Must match ProviderIntegrationController");
    }

    @Test
    @DisplayName("Verify Terraform Examples and Documentation are comprehensive")
    void testExamplesAndDocsExist() {
        assertTrue(Files.exists(TF_BASE.resolve(Path.of("examples", "main.tf"))), "examples/main.tf must exist");
        assertTrue(Files.exists(TF_BASE.resolve(Path.of("examples", "variables.tf"))), "examples/variables.tf must exist");
        assertTrue(Files.exists(TF_BASE.resolve(Path.of("examples", "outputs.tf"))), "examples/outputs.tf must exist");
        assertTrue(Files.exists(TF_BASE.resolve(Path.of("examples", "terraform.tfvars.example"))), "examples/terraform.tfvars.example must exist");

        assertTrue(Files.exists(TF_BASE.resolve(Path.of("docs", "ARCHITECTURE.md"))), "docs/ARCHITECTURE.md must exist");
        assertTrue(Files.exists(TF_BASE.resolve(Path.of("docs", "SECURITY.md"))), "docs/SECURITY.md must exist");
        assertTrue(Files.exists(TF_BASE.resolve(Path.of("docs", "SECRET_STATE_SAFETY.md"))), "docs/SECRET_STATE_SAFETY.md must exist");
        assertTrue(Files.exists(TF_BASE.resolve(Path.of("docs", "AUTHENTICATION.md"))), "docs/AUTHENTICATION.md must exist");
        assertTrue(Files.exists(TF_BASE.resolve(Path.of("docs", "TROUBLESHOOTING.md"))), "docs/TROUBLESHOOTING.md must exist");
    }
}
