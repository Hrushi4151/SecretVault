package io.secretvault.starter.health;

import io.secretvault.sdk.api.SecretVaultClient;
import io.secretvault.starter.properties.SecretVaultProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Spring Boot Actuator HealthIndicator reporting SecretVault connectivity and circuit breaker status.
 * NEVER outputs secret keys, plaintext payloads, or authentication tokens in health telemetry.
 */
public class SecretVaultHealthIndicator implements HealthIndicator {

    private final SecretVaultClient client;
    private final SecretVaultProperties properties;

    public SecretVaultHealthIndicator(SecretVaultClient client, SecretVaultProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (!properties.isEnabled()) {
            return Health.up().withDetail("status", "DISABLED").build();
        }

        try {
            boolean reachable = client.ping();
            String cbState = client.getCircuitBreaker().getState().name();

            if (reachable) {
                return Health.up()
                        .withDetail("endpoint", properties.getEndpoint().toString())
                        .withDetail("workspace", properties.getWorkspace())
                        .withDetail("project", properties.getProject() != null ? properties.getProject() : "N/A")
                        .withDetail("environment", properties.getEnvironment())
                        .withDetail("circuitBreaker", cbState)
                        .withDetail("cachedSecretsCount", client.getCache().size())
                        .build();
            } else {
                return Health.down()
                        .withDetail("endpoint", properties.getEndpoint().toString())
                        .withDetail("circuitBreaker", cbState)
                        .withDetail("error", "SecretVault service unreachable")
                        .build();
            }
        } catch (Exception e) {
            return Health.down()
                    .withDetail("endpoint", properties.getEndpoint().toString())
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
