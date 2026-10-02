package com.secretvault.cli.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptedFileCredentialStoreTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Encrypts credentials on disk and successfully decrypts when loaded")
    void savesAndLoadsEncryptedCredentials() throws IOException {
        EncryptedFileCredentialStore store = new EncryptedFileCredentialStore(tempDir);
        String profile = "work";
        String server = "https://vault.company.com";
        String accessToken = "jwt-access-token-12345";
        String refreshToken = "jwt-refresh-token-67890";

        StoredCredentials creds = new StoredCredentials(
                accessToken,
                refreshToken,
                Instant.now().plusSeconds(3600),
                server,
                "dev@company.com",
                UUID.randomUUID()
        );

        store.save(profile, server, creds);

        // Verify disk file is NOT in plaintext
        Path encFile = tempDir.resolve(".credentials.enc");
        assertThat(Files.exists(encFile)).isTrue();

        byte[] rawBytes = Files.readAllBytes(encFile);
        String rawContent = new String(rawBytes);
        assertThat(rawContent).doesNotContain(accessToken);
        assertThat(rawContent).doesNotContain(refreshToken);
        assertThat(rawContent).doesNotContain("dev@company.com");

        // Verify load successfully decrypts
        Optional<StoredCredentials> loaded = store.load(profile, server);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().accessToken()).isEqualTo(accessToken);
        assertThat(loaded.get().refreshToken()).isEqualTo(refreshToken);
        assertThat(loaded.get().userEmail()).isEqualTo("dev@company.com");
    }

    @Test
    @DisplayName("Isolates credentials between different profiles and servers")
    void isolatesDifferentProfilesAndServers() {
        EncryptedFileCredentialStore store = new EncryptedFileCredentialStore(tempDir);

        StoredCredentials workCreds = new StoredCredentials(
                "token-work", "refresh-work", Instant.now().plusSeconds(3600),
                "https://vault.work.com", "work@company.com", UUID.randomUUID()
        );
        StoredCredentials personalCreds = new StoredCredentials(
                "token-personal", "refresh-personal", Instant.now().plusSeconds(3600),
                "https://vault.personal.org", "personal@me.com", UUID.randomUUID()
        );

        store.save("work", "https://vault.work.com", workCreds);
        store.save("personal", "https://vault.personal.org", personalCreds);

        Optional<StoredCredentials> loadedWork = store.load("work", "https://vault.work.com");
        Optional<StoredCredentials> loadedPersonal = store.load("personal", "https://vault.personal.org");
        Optional<StoredCredentials> crossLeak = store.load("work", "https://vault.personal.org");

        assertThat(loadedWork).isPresent();
        assertThat(loadedWork.get().accessToken()).isEqualTo("token-work");

        assertThat(loadedPersonal).isPresent();
        assertThat(loadedPersonal.get().accessToken()).isEqualTo("token-personal");

        assertThat(crossLeak).isEmpty();
    }

    @Test
    @DisplayName("Deletes specific profile credentials correctly")
    void deletesProfileCredentials() {
        EncryptedFileCredentialStore store = new EncryptedFileCredentialStore(tempDir);
        StoredCredentials creds = new StoredCredentials(
                "tok", "ref", Instant.now().plusSeconds(3600),
                "http://localhost:8080", "user@test.com", UUID.randomUUID()
        );

        store.save("default", "http://localhost:8080", creds);
        assertThat(store.load("default", "http://localhost:8080")).isPresent();

        store.delete("default", "http://localhost:8080");
        assertThat(store.load("default", "http://localhost:8080")).isEmpty();
    }
}
