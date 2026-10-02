package com.secretvault.cli.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedactionHelperTest {

    @Test
    @DisplayName("Redacts Bearer authorization tokens and headers from error messages")
    void redactsBearerTokens() {
        String input = "Failed request with header Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0In0.abcdef";
        String redacted = RedactionHelper.redact(input);

        assertThat(redacted).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
        assertThat(redacted).contains("[REDACTED]");
    }

    @Test
    @DisplayName("Redacts standalone JWT tokens")
    void redactsStandaloneJwts() {
        String input = "Token is eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0In0.abcdef12345 in response";
        String redacted = RedactionHelper.redact(input);

        assertThat(redacted).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
        assertThat(redacted).contains("[REDACTED_JWT]");
    }

    @Test
    @DisplayName("Redacts sensitive JSON password and secret fields")
    void redactsSensitiveJsonFields() {
        String json = "{\"email\":\"admin@example.com\",\"password\":\"SuperSecretPassword123\",\"secret\":\"my_api_key\"}";
        String redacted = RedactionHelper.redact(json);

        assertThat(redacted).doesNotContain("SuperSecretPassword123");
        assertThat(redacted).doesNotContain("my_api_key");
        assertThat(redacted).contains("\"password\": \"[REDACTED]\"");
        assertThat(redacted).contains("\"secret\": \"[REDACTED]\"");
    }

    @Test
    @DisplayName("Wipes character array buffers in-place")
    void wipesCharacterArrays() {
        char[] password = "MySensitivePassword".toCharArray();
        RedactionHelper.wipe(password);

        for (char c : password) {
            assertThat(c).isEqualTo('\0');
        }
    }

    @Test
    @DisplayName("Wipes byte array buffers in-place")
    void wipesByteArrays() {
        byte[] bytes = new byte[]{1, 2, 3, 4, 5};
        RedactionHelper.wipe(bytes);

        for (byte b : bytes) {
            assertThat(b).isEqualTo((byte) 0);
        }
    }
}
