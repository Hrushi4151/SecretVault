package io.secretvault.sdk.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecretValueTest {

    @Test
    @DisplayName("toString() must ALWAYS redact the secret payload")
    void testToStringRedaction() {
        SecretValue secret = new SecretValue(
                "DB_PASSWORD",
                "SuperSensitiveSecret123!",
                3,
                "production",
                Instant.now(),
                Instant.now(),
                null,
                Map.of("service", "payment")
        );

        String str = secret.toString();
        assertThat(str).contains("name='DB_PASSWORD'");
        assertThat(str).contains("version=3");
        assertThat(str).contains("value=[REDACTED]");
        assertThat(str).doesNotContain("SuperSensitiveSecret123!");
    }

    @Test
    @DisplayName("Character array and byte array conversions work correctly")
    void testCharArrayAndByteArray() {
        SecretValue secret = SecretValue.of("API_KEY", "secret_key_value_999", 1, "development");

        assertThat(secret.asCharArray()).containsExactly("secret_key_value_999".toCharArray());
        assertThat(new String(secret.asByteArray())).isEqualTo("secret_key_value_999");
    }

    @Test
    @DisplayName("Expiration helpers calculate correctly")
    void testExpiration() {
        Instant past = Instant.now().minusSeconds(10);
        Instant future = Instant.now().plusSeconds(60);

        SecretValue expired = new SecretValue("EXPIRED_KEY", "val", 1, "dev", Instant.now(), Instant.now(), past, Map.of());
        SecretValue active = new SecretValue("ACTIVE_KEY", "val", 1, "dev", Instant.now(), Instant.now(), future, Map.of());

        assertThat(expired.isExpired()).isTrue();
        assertThat(active.isExpired()).isFalse();
    }
}
