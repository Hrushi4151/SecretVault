package com.secretvault.rotation.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.rotation.engine.SecretGenerationEngine;
import com.secretvault.rotation.entity.RotationJob;
import com.secretvault.rotation.entity.RotationPolicy;
import com.secretvault.rotation.model.SecretType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Rotator specialized for third-party and internal API keys with custom prefixes,
 * cryptographic entropy, and HTTP endpoint validation checks.
 */
@Component
@Order(20)
public class ApiKeyRotator implements SecretRotator {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyRotator.class);
    private final SecretGenerationEngine generationEngine;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ApiKeyRotator(SecretGenerationEngine generationEngine, ObjectMapper objectMapper) {
        this.generationEngine = generationEngine;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public boolean supports(SecretType type, RotationPolicy policy) {
        return type == SecretType.API_KEY;
    }

    @Override
    public String generate(RotationPolicy policy, RotationJob job) {
        String prefix = "sv_live_";
        int entropy = 32;

        String configJson = policy != null ? policy.getSecretGeneratorConfig() : null;
        if (configJson != null && !configJson.trim().isEmpty()) {
            try {
                JsonNode root = objectMapper.readTree(configJson);
                if (root.has("prefix")) {
                    prefix = root.get("prefix").asText();
                }
                if (root.has("entropyBytes")) {
                    entropy = root.get("entropyBytes").asInt();
                }
            } catch (Exception ignored) {
            }
        }

        log.info("Generating API key with prefix '{}' and length {}", prefix, entropy);
        return generationEngine.generateApiKey(prefix, entropy);
    }

    @Override
    public boolean validate(String newPlaintext, RotationPolicy policy, RotationJob job) {
        if (newPlaintext == null || newPlaintext.length() < 20) {
            return false;
        }

        String configJson = policy != null ? policy.getSecretGeneratorConfig() : null;
        if (configJson != null && !configJson.trim().isEmpty()) {
            try {
                JsonNode root = objectMapper.readTree(configJson);
                if (root.has("validationUrl")) {
                    String validationUrl = root.get("validationUrl").asText();
                    String authHeader = root.has("authHeader") ? root.get("authHeader").asText() : "Bearer";

                    log.info("Validating generated API key against validation endpoint: {}", validationUrl);
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(validationUrl))
                            .header("Authorization", authHeader + " " + newPlaintext)
                            .header("User-Agent", "SecretVault-Rotation-Engine/1.0")
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build();

                    HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                    return response.statusCode() >= 200 && response.statusCode() < 400;
                }
            } catch (Exception e) {
                log.warn("API Key remote validation check error: {}", e.getMessage());
                return false;
            }
        }

        return true;
    }
}
