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
 * Generic HTTP Webhook Rotator that delegates secret rotation, validation, or lifecycle
 * hooks to a secured external HTTP service or custom orchestrator.
 */
@Component
@Order(30)
public class GenericHttpRotator implements SecretRotator {

    private static final Logger log = LoggerFactory.getLogger(GenericHttpRotator.class);
    private final SecretGenerationEngine generationEngine;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GenericHttpRotator(SecretGenerationEngine generationEngine, ObjectMapper objectMapper) {
        this.generationEngine = generationEngine;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public boolean supports(SecretType type, RotationPolicy policy) {
        if (policy != null && policy.getSecretGeneratorConfig() != null && policy.getSecretGeneratorConfig().contains("rotateWebhookUrl")) {
            return true;
        }
        return false;
    }

    @Override
    public String generate(RotationPolicy policy, RotationJob job) {
        String configJson = policy != null ? policy.getSecretGeneratorConfig() : null;
        if (configJson != null) {
            try {
                JsonNode root = objectMapper.readTree(configJson);
                if (root.has("rotateWebhookUrl")) {
                    String webhookUrl = root.get("rotateWebhookUrl").asText();
                    log.info("Requesting new secret generation from external webhook: {}", webhookUrl);

                    String payload = objectMapper.writeValueAsString(java.util.Map.of(
                            "secretId", policy.getSecretId(),
                            "jobId", job.getId(),
                            "action", "GENERATE"
                    ));

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(webhookUrl))
                            .header("Content-Type", "application/json")
                            .header("User-Agent", "SecretVault-Rotation-Engine/1.0")
                            .timeout(Duration.ofSeconds(10))
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        JsonNode resJson = objectMapper.readTree(response.body());
                        if (resJson.has("secretValue")) {
                            return resJson.get("secretValue").asText();
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to generate secret via webhook, falling back to secure generation engine: {}", e.getMessage());
            }
        }
        return generationEngine.generatePassword(36, true, true);
    }

    @Override
    public boolean validate(String newPlaintext, RotationPolicy policy, RotationJob job) {
        return newPlaintext != null && !newPlaintext.trim().isEmpty();
    }
}
