package io.secretvault.starter.health;

import io.secretvault.sdk.api.SecretVaultClient;
import io.secretvault.sdk.auth.StaticTokenProvider;
import io.secretvault.sdk.client.SdkConfig;
import io.secretvault.starter.properties.SecretVaultProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class SecretVaultHealthIndicatorTest {

    @Test
    @DisplayName("HealthIndicator reports disabled when starter is disabled")
    void testDisabledHealth() {
        SdkConfig config = SdkConfig.builder()
                .endpoint(URI.create("http://localhost:8080"))
                .credentials(new StaticTokenProvider("test-token"))
                .build();
        SecretVaultClient client = SecretVaultClient.create(config);

        SecretVaultProperties props = new SecretVaultProperties();
        props.setEnabled(false);

        SecretVaultHealthIndicator healthIndicator = new SecretVaultHealthIndicator(client, props);
        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("status", "DISABLED");
    }

    @Test
    @DisplayName("HealthIndicator does NOT leak secret values or sensitive keys in telemetry")
    void testHealthDetailsSanitization() {
        SdkConfig config = SdkConfig.builder()
                .endpoint(URI.create("http://localhost:8080"))
                .credentials(new StaticTokenProvider("test-token"))
                .defaultScope("default", "payment-gateway", "development")
                .build();
        SecretVaultClient client = SecretVaultClient.create(config);

        SecretVaultProperties props = new SecretVaultProperties();
        props.setProject("payment-gateway");

        SecretVaultHealthIndicator healthIndicator = new SecretVaultHealthIndicator(client, props);
        Health health = healthIndicator.health();

        // Must never contain secret payload names or values
        assertThat(health.getDetails().keySet())
                .noneMatch(k -> k.toLowerCase().contains("password") || k.toLowerCase().contains("token"));
    }
}
