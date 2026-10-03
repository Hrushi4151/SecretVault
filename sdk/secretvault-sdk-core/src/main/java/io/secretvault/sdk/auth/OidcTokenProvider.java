package io.secretvault.sdk.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.secretvault.sdk.exception.AuthenticationException;
import io.secretvault.sdk.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Automates workload authentication via OIDC token exchange (GitHub Actions, GitLab CI, Generic OIDC).
 * Exchanges OIDC tokens for short-lived (600s) SecretVault session tokens and refreshes them seamlessly.
 */
public class OidcTokenProvider implements CredentialsProvider {

    private static final Logger log = LoggerFactory.getLogger(OidcTokenProvider.class);

    private final URI endpoint;
    private final UUID providerId;
    private final UUID machineId;
    private final Supplier<String> oidcTokenSupplier;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private volatile String cachedToken;
    private volatile Instant expiresAt;

    public OidcTokenProvider(URI endpoint, UUID providerId, UUID machineId, Supplier<String> oidcTokenSupplier) {
        this(endpoint, providerId, machineId, oidcTokenSupplier, HttpClient.newHttpClient());
    }

    public OidcTokenProvider(URI endpoint, UUID providerId, UUID machineId, Supplier<String> oidcTokenSupplier, HttpClient httpClient) {
        this.endpoint = Objects.requireNonNull(endpoint, "Endpoint cannot be null");
        this.providerId = Objects.requireNonNull(providerId, "Provider ID cannot be null");
        this.machineId = Objects.requireNonNull(machineId, "Machine ID cannot be null");
        this.oidcTokenSupplier = Objects.requireNonNull(oidcTokenSupplier, "OIDC token supplier cannot be null");
        this.httpClient = httpClient != null ? httpClient : HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public synchronized String getBearerToken() {
        if (cachedToken != null && expiresAt != null && Instant.now().isBefore(expiresAt.minusSeconds(30))) {
            return cachedToken;
        }
        exchangeToken();
        return cachedToken;
    }

    @Override
    public boolean supportsRefresh() {
        return true;
    }

    @Override
    public synchronized void refresh() {
        exchangeToken();
    }

    private void exchangeToken() {
        String rawOidcToken = oidcTokenSupplier.get();
        if (rawOidcToken == null || rawOidcToken.trim().isEmpty()) {
            throw new AuthenticationException("OIDC token supplier returned an empty token", ErrorCode.SV_AUTH_REQUIRED);
        }

        try {
            URI exchangeUri = endpoint.resolve("/api/v1/auth/oidc/token");
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "providerId", providerId.toString(),
                    "token", rawOidcToken.trim()
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(exchangeUri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("OIDC token exchange failed with HTTP {}", response.statusCode());
                throw new AuthenticationException(
                        "OIDC exchange failed: HTTP " + response.statusCode(),
                        ErrorCode.SV_AUTH_INVALID,
                        response.statusCode(),
                        null
                );
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.path("data");
            this.cachedToken = data.path("accessToken").asText();
            if (this.cachedToken == null || this.cachedToken.isEmpty()) {
                this.cachedToken = data.path("token").asText();
            }
            long ttlSeconds = data.path("expiresIn").asLong(600);
            this.expiresAt = Instant.now().plusSeconds(ttlSeconds);

            log.info("Successfully exchanged OIDC token for SecretVault machine session (TTL: {}s)", ttlSeconds);
        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute OIDC token exchange: {}", e.getMessage());
            throw new AuthenticationException("OIDC exchange communication error: " + e.getMessage(), ErrorCode.SV_UNAVAILABLE);
        }
    }

    @Override
    public String toString() {
        return "OidcTokenProvider{providerId=" + providerId + ", machineId=" + machineId + ", token=[REDACTED]}";
    }
}
