package com.secretvault.webhook;

import com.secretvault.common.exception.ApiException;
import com.secretvault.webhook.security.HardenedWebhookTransport;
import com.secretvault.webhook.security.SsrfProtectionValidator;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Phase 13: Hardened Webhook Transport & SSRF / DNS Rebinding Protection Tests")
class HardenedWebhookTransportTest {

    private HttpServer localServer;
    private int localPort;
    private HardenedWebhookTransport transport;
    private SsrfProtectionValidator mockValidator;

    @BeforeEach
    void setUp() throws Exception {
        localServer = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        localPort = localServer.getAddress().getPort();
        localServer.start();

        // Validator configured with custom resolution for local loopback in test
        mockValidator = new SsrfProtectionValidator() {
            @Override
            public List<InetAddress> resolveAndValidateDestination(String urlString) {
                try {
                    return Collections.singletonList(InetAddress.getByName("127.0.0.1"));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        mockValidator.setAllowHttp(true);
        transport = new HardenedWebhookTransport(mockValidator);
    }

    @AfterEach
    void tearDown() {
        if (localServer != null) {
            localServer.stop(0);
        }
    }

    @Test
    @DisplayName("Successfully dispatches HTTP POST payload and receives status 200 OK")
    void testSuccessfulHttpPost() throws Exception {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedHeader = new AtomicReference<>();

        localServer.createContext("/webhook-recv", exchange -> {
            receivedHeader.set(exchange.getRequestHeaders().getFirst("X-Custom-Header"));
            try (InputStream in = exchange.getRequestBody()) {
                receivedBody.set(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] responseBytes = "{\"received\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(responseBytes);
            }
        });

        String destinationUrl = "http://localhost:" + localPort + "/webhook-recv";
        Map<String, String> headers = Map.of("X-Custom-Header", "SecretVault-Test");
        String payload = "{\"event\":\"SECRET_ROTATED\"}";

        HardenedWebhookTransport.WebhookHttpResponse resp = transport.executePost(
                destinationUrl,
                headers,
                payload,
                Duration.ofSeconds(2),
                Duration.ofSeconds(2)
        );

        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).contains("{\"received\":true}");
        assertThat(receivedBody.get()).isEqualTo(payload);
        assertThat(receivedHeader.get()).isEqualTo("SecretVault-Test");
    }

    @Test
    @DisplayName("Extracts and bounds Retry-After header on HTTP 429 response")
    void testRetryAfterHeaderExtraction() throws Exception {
        localServer.createContext("/rate-limited", exchange -> {
            exchange.getResponseHeaders().set("Retry-After", "45");
            byte[] responseBytes = "{\"error\":\"Too Many Requests\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, responseBytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(responseBytes);
            }
        });

        String destinationUrl = "http://localhost:" + localPort + "/rate-limited";
        HardenedWebhookTransport.WebhookHttpResponse resp = transport.executePost(
                destinationUrl,
                Map.of(),
                "{}",
                Duration.ofSeconds(2),
                Duration.ofSeconds(2)
        );

        assertThat(resp.statusCode()).isEqualTo(429);
        assertThat(resp.getRetryAfterSeconds()).isEqualTo(45L);
    }

    @Test
    @DisplayName("Enforces safe response body limit (caps at 64 KB without OOM)")
    void testSafeResponseBodyLimit() throws Exception {
        localServer.createContext("/large-payload", exchange -> {
            // Generate 128 KB response
            byte[] largeResponse = new byte[128 * 1024];
            java.util.Arrays.fill(largeResponse, (byte) 'A');
            exchange.sendResponseHeaders(200, largeResponse.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(largeResponse);
            }
        });

        String destinationUrl = "http://localhost:" + localPort + "/large-payload";
        HardenedWebhookTransport.WebhookHttpResponse resp = transport.executePost(
                destinationUrl,
                Map.of(),
                "{}",
                Duration.ofSeconds(2),
                Duration.ofSeconds(2)
        );

        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body().length()).isEqualTo(HardenedWebhookTransport.MAX_RESPONSE_BODY_BYTES);
    }

    @Test
    @DisplayName("Enforces read timeout when remote receiver hangs")
    void testReadTimeoutEnforcement() {
        localServer.createContext("/hanging-endpoint", exchange -> {
            try {
                Thread.sleep(500); // Exceeds 100ms timeout
            } catch (InterruptedException ignored) {}
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });

        String destinationUrl = "http://localhost:" + localPort + "/hanging-endpoint";

        assertThatThrownBy(() -> transport.executePost(
                destinationUrl,
                Map.of(),
                "{}",
                Duration.ofSeconds(1),
                Duration.ofMillis(100) // 100ms read timeout
        )).isInstanceOf(SocketTimeoutException.class);
    }

    @Test
    @DisplayName("Blocks request when SsrfProtectionValidator flags target address")
    void testSsrfBlockingByValidator() {
        SsrfProtectionValidator strictValidator = new SsrfProtectionValidator();
        HardenedWebhookTransport strictTransport = new HardenedWebhookTransport(strictValidator);

        assertThatThrownBy(() -> strictTransport.executePost(
                "http://169.254.169.254/latest/meta-data/",
                Map.of(),
                "{}",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        )).isInstanceOf(ApiException.class);
    }
}
