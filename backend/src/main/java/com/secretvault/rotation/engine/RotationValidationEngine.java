package com.secretvault.rotation.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.rotation.entity.RotationJob;
import com.secretvault.rotation.entity.RotationPolicy;
import com.secretvault.rotation.entity.RotationValidation;
import com.secretvault.rotation.model.ValidationType;
import com.secretvault.rotation.repository.RotationValidationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Validation Engine executing pre-activation verification for newly generated credentials.
 */
@Component
public class RotationValidationEngine {

    private static final Logger log = LoggerFactory.getLogger(RotationValidationEngine.class);

    private final RotationValidationRepository validationRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RotationValidationEngine(RotationValidationRepository validationRepository, ObjectMapper objectMapper) {
        this.validationRepository = validationRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Executes the configured validation strategy against the new plaintext secret.
     * Records validation attempts in rotation_validations.
     */
    public boolean validateSecret(String newPlaintext, RotationPolicy policy, RotationJob job) {
        ValidationType type = policy != null && policy.getValidationType() != null ? policy.getValidationType() : ValidationType.NONE;
        log.info("Executing rotation validation '{}' for job {}", type, job.getId());

        long startTime = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;

        try {
            switch (type) {
                case NONE:
                    success = newPlaintext != null && !newPlaintext.trim().isEmpty();
                    break;

                case CONNECTIVITY:
                case CUSTOM_HTTP:
                    success = validateHttpEndpoint(newPlaintext, policy);
                    break;

                case AUTHENTICATION:
                    success = validateAuthentication(newPlaintext, policy);
                    break;

                case DATABASE_CONNECTION:
                    success = validateDatabase(newPlaintext, policy);
                    break;

                case APPLICATION_HEALTH:
                    success = newPlaintext != null && newPlaintext.length() >= 8;
                    break;

                case PROVIDER_API:
                default:
                    success = newPlaintext != null && !newPlaintext.trim().isEmpty();
                    break;
            }
        } catch (Exception e) {
            log.warn("Validation execution threw exception: {}", e.getMessage());
            success = false;
            errorMessage = e.getMessage();
        }

        long durationMs = System.currentTimeMillis() - startTime;

        // Persist validation record
        try {
            RotationValidation record = new RotationValidation();
            record.setJobId(job.getId());
            record.setValidationType(type);
            record.setStatus(success ? "PASSED" : "FAILED");
            record.setValidatedAt(Instant.now());
            record.setLatencyMs(durationMs);
            record.setErrorMessage(errorMessage);
            validationRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to save RotationValidation record: {}", e.getMessage());
        }

        return success;
    }

    private boolean validateHttpEndpoint(String secret, RotationPolicy policy) {
        String configJson = policy != null ? policy.getSecretGeneratorConfig() : null;
        if (configJson == null || configJson.isBlank()) {
            return secret != null && !secret.isBlank();
        }

        try {
            JsonNode root = objectMapper.readTree(configJson);
            if (!root.has("validationUrl")) {
                return true;
            }
            String url = root.get("validationUrl").asText();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 400;
        } catch (Exception e) {
            log.warn("HTTP validation check failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean validateAuthentication(String secret, RotationPolicy policy) {
        String configJson = policy != null ? policy.getSecretGeneratorConfig() : null;
        if (configJson == null || configJson.isBlank()) {
            return secret != null && secret.length() >= 8;
        }

        try {
            JsonNode root = objectMapper.readTree(configJson);
            if (root.has("authEndpoint")) {
                String url = root.get("authEndpoint").asText();
                String authHeader = root.has("authHeader") ? root.get("authHeader").asText() : "Bearer";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", authHeader + " " + secret)
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                return response.statusCode() == 200;
            }
            return true;
        } catch (Exception e) {
            log.warn("Auth endpoint validation failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean validateDatabase(String secret, RotationPolicy policy) {
        String configJson = policy != null ? policy.getSecretGeneratorConfig() : null;
        if (configJson == null || configJson.isBlank()) {
            return secret != null && secret.length() >= 8;
        }

        try {
            JsonNode root = objectMapper.readTree(configJson);
            if (root.has("jdbcUrl") && root.has("username")) {
                String jdbcUrl = root.get("jdbcUrl").asText();
                String username = root.get("username").asText();
                try (Connection conn = DriverManager.getConnection(jdbcUrl, username, secret)) {
                    return conn.isValid(5);
                } catch (Exception e) {
                    log.warn("JDBC connection validation failed: {}", e.getMessage());
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("Database validation config parsing failed: {}", e.getMessage());
            return false;
        }
    }
}
