package io.secretvault.sdk.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.secretvault.sdk.client.SecretVaultHttpClient;
import io.secretvault.sdk.exception.AuthenticationException;
import io.secretvault.sdk.exception.SecretVaultUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsumerHeartbeatDaemonTest {

    @Mock
    private SecretVaultHttpClient httpClient;

    private UUID workspaceId;
    private UUID consumerId;
    private ConsumerHeartbeatConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        consumerId = UUID.randomUUID();
        config = ConsumerHeartbeatConfig.builder()
                .workspaceId(workspaceId)
                .consumerId(consumerId)
                .interval(Duration.ofMillis(100))
                .sdkVersion("1.0.0")
                .runtimeFramework("Java 21")
                .initialAcknowledgedVersion(1)
                .enabled(true)
                .maxRetries(3)
                .build();
    }

    @Test
    @DisplayName("Configuration: Enforces required consumerId and positive interval")
    void testConfigValidation() {
        assertThatThrownBy(() -> ConsumerHeartbeatConfig.builder()
                .consumerId(null)
                .enabled(true)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consumerId is required");

        assertThatThrownBy(() -> ConsumerHeartbeatConfig.builder()
                .consumerId(UUID.randomUUID())
                .interval(Duration.ZERO)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("interval must be positive");
    }

    @Test
    @DisplayName("Scheduler starts, isRunning is true, and stops gracefully")
    void testSchedulerLifecycle() {
        try (ConsumerHeartbeatDaemon daemon = new ConsumerHeartbeatDaemon(config, httpClient)) {
            assertThat(daemon.isRunning()).isFalse();

            daemon.start();
            assertThat(daemon.isRunning()).isTrue();

            daemon.stop();
            assertThat(daemon.isRunning()).isFalse();
        }
    }

    @Test
    @DisplayName("Duplicate initialization: Calling start() multiple times is safely idempotent")
    void testDuplicateInitialization() {
        try (ConsumerHeartbeatDaemon daemon = new ConsumerHeartbeatDaemon(config, httpClient)) {
            daemon.start();
            assertThat(daemon.isRunning()).isTrue();

            // Second start must not throw or create duplicate loops
            daemon.start();
            assertThat(daemon.isRunning()).isTrue();

            daemon.stop();
        }
    }

    @Test
    @DisplayName("Acknowledged version: Successfully updates acknowledged version dynamically")
    void testAcknowledgedVersionUpdate() {
        try (ConsumerHeartbeatDaemon daemon = new ConsumerHeartbeatDaemon(config, httpClient)) {
            assertThat(daemon.getAcknowledgedVersion()).isEqualTo(1);

            daemon.updateAcknowledgedVersion(5);
            assertThat(daemon.getAcknowledgedVersion()).isEqualTo(5);
        }
    }

    @Test
    @DisplayName("Send Heartbeat: Successfully posts valid payload to backend API")
    void testSendHeartbeatSuccess() throws Exception {
        when(httpClient.executeApi(eq("POST"), anyString(), anyString(), eq(workspaceId)))
                .thenReturn(objectMapper.readTree("{\"status\":\"SUCCESS\"}"));

        try (ConsumerHeartbeatDaemon daemon = new ConsumerHeartbeatDaemon(config, httpClient)) {
            ConsumerHeartbeatResult result = daemon.sendHeartbeatNow();
            assertThat(result.success()).isTrue();
            assertThat(result.acknowledgedVersion()).isEqualTo(1);

            String expectedPath = "/api/v1/workspaces/" + workspaceId + "/consumers/" + consumerId + "/heartbeat";
            verify(httpClient).executeApi(eq("POST"), eq(expectedPath), anyString(), eq(workspaceId));
        }
    }

    @Test
    @DisplayName("Security Invariant: Heartbeat payload contains ONLY version and metadata, NEVER secret material")
    void testNoSecretMaterialInPayload() throws Exception {
        when(httpClient.executeApi(eq("POST"), anyString(), anyString(), eq(workspaceId)))
                .thenReturn(objectMapper.readTree("{\"status\":\"SUCCESS\"}"));

        try (ConsumerHeartbeatDaemon daemon = new ConsumerHeartbeatDaemon(config, httpClient)) {
            daemon.updateAcknowledgedVersion(42);
            daemon.sendHeartbeatNow();

            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
            verify(httpClient).executeApi(eq("POST"), anyString(), bodyCaptor.capture(), eq(workspaceId));

            String requestBody = bodyCaptor.getValue();
            JsonNode json = objectMapper.readTree(requestBody);

            // Allowed fields ONLY
            assertThat(json.has("currentAcknowledgedVersion")).isTrue();
            assertThat(json.get("currentAcknowledgedVersion").asInt()).isEqualTo(42);
            assertThat(json.has("sdkVersion")).isTrue();
            assertThat(json.get("sdkVersion").asText()).isEqualTo("1.0.0");
            assertThat(json.has("runtimeFramework")).isTrue();
            assertThat(json.get("runtimeFramework").asText()).isEqualTo("Java 21");

            // Strictly forbidden fields
            assertThat(json.has("secret")).isFalse();
            assertThat(json.has("secretValue")).isFalse();
            assertThat(json.has("value")).isFalse();
            assertThat(json.has("dek")).isFalse();
            assertThat(json.has("kek")).isFalse();
            assertThat(json.has("password")).isFalse();
            assertThat(json.has("token")).isFalse();
            assertThat(json.has("credential")).isFalse();
        }
    }

    @Test
    @DisplayName("Network Failure: Bounded retries executed and failure handled gracefully")
    void testTransientNetworkFailureRetry() {
        when(httpClient.executeApi(eq("POST"), anyString(), anyString(), eq(workspaceId)))
                .thenThrow(new SecretVaultUnavailableException("503 Service Unavailable"));

        try (ConsumerHeartbeatDaemon daemon = new ConsumerHeartbeatDaemon(config, httpClient)) {
            ConsumerHeartbeatResult result = daemon.sendHeartbeatNow();
            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("503 Service Unavailable");
            assertThat(daemon.getConsecutiveFailures()).isEqualTo(3);

            // Verified 3 bounded retry attempts, never infinite
            verify(httpClient, times(3)).executeApi(eq("POST"), anyString(), anyString(), eq(workspaceId));
        }
    }

    @Test
    @DisplayName("Auth Failure: Fails closed immediately without retrying or crashing host app")
    void testAuthenticationFailureFailsClosed() {
        when(httpClient.executeApi(eq("POST"), anyString(), anyString(), eq(workspaceId)))
                .thenThrow(new AuthenticationException("Invalid bearer token"));

        try (ConsumerHeartbeatDaemon daemon = new ConsumerHeartbeatDaemon(config, httpClient)) {
            ConsumerHeartbeatResult result = daemon.sendHeartbeatNow();
            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("Auth failure");

            // Fails closed immediately without continuous retry loops
            verify(httpClient, times(1)).executeApi(eq("POST"), anyString(), anyString(), eq(workspaceId));
        }
    }

    @Test
    @DisplayName("Safe Restart: Daemon can be stopped and restarted cleanly")
    void testSafeRestart() {
        try (ConsumerHeartbeatDaemon daemon = new ConsumerHeartbeatDaemon(config, httpClient)) {
            daemon.start();
            assertThat(daemon.isRunning()).isTrue();
            daemon.stop();
            assertThat(daemon.isRunning()).isFalse();

            // Restart
            daemon.start();
            assertThat(daemon.isRunning()).isTrue();
            daemon.stop();
            assertThat(daemon.isRunning()).isFalse();
        }
    }

    @Test
    @DisplayName("Disabled Config: Daemon does not start when enabled is false")
    void testDisabledConfigDoesNotStart() {
        ConsumerHeartbeatConfig disabledConfig = ConsumerHeartbeatConfig.builder()
                .consumerId(consumerId)
                .enabled(false)
                .build();

        try (ConsumerHeartbeatDaemon daemon = new ConsumerHeartbeatDaemon(disabledConfig, httpClient)) {
            daemon.start();
            assertThat(daemon.isRunning()).isFalse();
            verify(httpClient, never()).executeApi(anyString(), anyString(), anyString(), any());
        }
    }
}
