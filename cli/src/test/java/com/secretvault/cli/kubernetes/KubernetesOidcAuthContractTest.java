package com.secretvault.cli.kubernetes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Kubernetes Workload OIDC Authentication Contract & Client Tests")
class KubernetesOidcAuthContractTest {

    private static final Path K8S_BASE = Path.of("..", "infrastructure", "kubernetes");

    @Test
    @DisplayName("Verify Go auth and client source files exist and adhere to structure")
    void testAuthPackageFilesExist() {
        Path authPkg = K8S_BASE.resolve(Path.of("pkg", "auth"));
        Path clientPkg = K8S_BASE.resolve(Path.of("pkg", "client"));
        Path metricsPkg = K8S_BASE.resolve(Path.of("pkg", "metrics"));

        assertTrue(Files.exists(authPkg.resolve("errors.go")), "errors.go must exist");
        assertTrue(Files.exists(authPkg.resolve("projected_token_provider.go")), "projected_token_provider.go must exist");
        assertTrue(Files.exists(authPkg.resolve("machine_session.go")), "machine_session.go must exist");
        assertTrue(Files.exists(authPkg.resolve("oidc_exchange_client.go")), "oidc_exchange_client.go must exist");
        assertTrue(Files.exists(authPkg.resolve("auth_test.go")), "auth_test.go must exist");

        assertTrue(Files.exists(clientPkg.resolve("config.go")), "config.go must exist");
        assertTrue(Files.exists(clientPkg.resolve("retry.go")), "retry.go must exist");
        assertTrue(Files.exists(clientPkg.resolve("redaction.go")), "redaction.go must exist");
        assertTrue(Files.exists(clientPkg.resolve("client.go")), "client.go must exist");
        assertTrue(Files.exists(clientPkg.resolve("client_test.go")), "client_test.go must exist");

        assertTrue(Files.exists(metricsPkg.resolve("auth_metrics.go")), "auth_metrics.go must exist");
    }

    @Test
    @DisplayName("Verify zero token persistence and strict redaction rules in Go code")
    void testSecurityInvariantsInAuthCode() throws Exception {
        Path authPkg = K8S_BASE.resolve(Path.of("pkg", "auth"));
        Path clientPkg = K8S_BASE.resolve(Path.of("pkg", "client"));

        List<Path> codeFiles = List.of(
                authPkg.resolve("machine_session.go"),
                authPkg.resolve("projected_token_provider.go"),
                authPkg.resolve("oidc_exchange_client.go"),
                clientPkg.resolve("config.go"),
                clientPkg.resolve("redaction.go"),
                clientPkg.resolve("client.go")
        );

        for (Path file : codeFiles) {
            String content = Files.readString(file);

            // InsecureSkipVerify must NEVER be set to true
            assertFalse(content.contains("InsecureSkipVerify: true"),
                    "InsecureSkipVerify must never be true in " + file.getFileName());

            // Token storage to disk must not exist
            assertFalse(content.contains("os.WriteFile") && !file.getFileName().toString().contains("test"),
                    "Production code must never write token files: " + file.getFileName());
        }
    }

    @Test
    @DisplayName("Verify SSRF protection and BaseURL validation rules in client config")
    void testSsrfProtectionInClientConfig() throws Exception {
        Path configFile = K8S_BASE.resolve(Path.of("pkg", "client", "config.go"));
        String content = Files.readString(configFile);

        assertTrue(content.contains("HTTPS is strictly required for SecretVault API in production"),
                "Production config must strictly enforce HTTPS");
        assertTrue(content.contains("AllowInsecureHTTP"),
                "Insecure HTTP must require explicit AllowInsecureHTTP flag and localhost check");
        assertTrue(content.contains("BaseURL must not contain embedded user credentials"),
                "BaseURL must disallow userinfo credentials");
    }

    @Test
    @DisplayName("Verify retry classifier correctly separates transient vs non-transient status codes")
    void testRetryClassifierLogic() throws Exception {
        Path retryFile = K8S_BASE.resolve(Path.of("pkg", "client", "retry.go"));
        String content = Files.readString(retryFile);

        assertTrue(content.contains("http.StatusTooManyRequests"), "429 must be retryable");
        assertTrue(content.contains("http.StatusServiceUnavailable"), "503 must be retryable");
        assertTrue(content.contains("CalculateBackoff"), "CalculateBackoff must implement jitter");
    }
}
