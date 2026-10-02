package com.secretvault.provider.adapter.render;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.provider.adapter.ProviderAdapter;
import com.secretvault.provider.entity.ProviderResourceMapping;
import com.secretvault.provider.model.ProviderCapability;
import com.secretvault.provider.model.ProviderDiscoveredEnvironment;
import com.secretvault.provider.model.ProviderDiscoveredResource;
import com.secretvault.provider.model.ProviderErrorCode;
import com.secretvault.provider.model.ProviderResourceType;
import com.secretvault.provider.model.ProviderSecretMetadata;
import com.secretvault.provider.model.ProviderSecretOperationResult;
import com.secretvault.provider.model.ProviderType;
import com.secretvault.provider.model.ProviderValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dedicated Render platform integration adapter.
 * Communicates with Render REST API v1 for service discovery, environment mapping, and environment variable management.
 */
@Component
public class RenderProviderAdapter implements ProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(RenderProviderAdapter.class);
    private static final Set<ProviderCapability> CAPABILITIES = Set.of(
            ProviderCapability.VALIDATE_CONNECTION,
            ProviderCapability.DISCOVER_SERVICES,
            ProviderCapability.DISCOVER_PROJECTS,
            ProviderCapability.DISCOVER_ENVIRONMENTS,
            ProviderCapability.READ_SECRET_METADATA,
            ProviderCapability.WRITE_SECRETS,
            ProviderCapability.DELETE_SECRETS
    );

    private final String baseUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public RenderProviderAdapter(
            @Value("${secretvault.provider.render.base-url:https://api.render.com/v1}") String baseUrl,
            ObjectMapper objectMapper
    ) {
        this(baseUrl, objectMapper, createDefaultRestClient(baseUrl));
    }

    public RenderProviderAdapter(
            String baseUrl,
            ObjectMapper objectMapper,
            RestClient restClient
    ) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
        this.restClient = Objects.requireNonNull(restClient, "RestClient must not be null");
    }

    private static RestClient createDefaultRestClient(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.RENDER;
    }

    @Override
    public Set<ProviderCapability> getCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public ProviderValidationResult validateConnection(Map<String, Object> configuration, String credential) {
        try {
            String responseBody = executeGet("/owners?limit=1", credential);
            JsonNode root = objectMapper.readTree(responseBody);

            if (root.isArray() && !root.isEmpty()) {
                JsonNode firstOwner = root.get(0).path("owner");
                String name = firstOwner.path("name").asText(firstOwner.path("email").asText("Render Account"));
                String id = firstOwner.path("id").asText();
                return ProviderValidationResult.success(name, id, CAPABILITIES);
            }

            return ProviderValidationResult.success("Render Account", "render-auth", CAPABILITIES);
        } catch (HttpClientErrorException.Unauthorized e) {
            return ProviderValidationResult.failure(ProviderErrorCode.PROVIDER_AUTHENTICATION_FAILED, "Invalid or expired Render API key");
        } catch (HttpClientErrorException.Forbidden e) {
            return ProviderValidationResult.failure(ProviderErrorCode.PROVIDER_AUTHORIZATION_FAILED, "Access forbidden by Render permissions");
        } catch (HttpClientErrorException.TooManyRequests e) {
            return ProviderValidationResult.failure(ProviderErrorCode.PROVIDER_RATE_LIMITED, "Render API rate limit exceeded");
        } catch (ResourceAccessException e) {
            return ProviderValidationResult.failure(ProviderErrorCode.PROVIDER_TIMEOUT, "Connection to Render API timed out");
        } catch (Exception e) {
            log.warn("Render connection validation failed: {}", e.getMessage());
            return ProviderValidationResult.failure(ProviderErrorCode.PROVIDER_UNAVAILABLE, "Unable to reach Render API: " + e.getMessage());
        }
    }

    @Override
    public List<ProviderDiscoveredResource> discoverResources(Map<String, Object> configuration, String credential) {
        try {
            String responseBody = executeGet("/services?limit=100", credential);
            JsonNode root = objectMapper.readTree(responseBody);

            List<ProviderDiscoveredResource> resources = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode item : root) {
                    JsonNode service = item.has("service") ? item.get("service") : item;
                    String id = service.path("id").asText();
                    String name = service.path("name").asText();
                    String type = service.path("type").asText("web_service");
                    String region = service.path("region").asText("oregon");
                    String status = service.path("suspended").asText("not_suspended").equals("suspended") ? "SUSPENDED" : "ACTIVE";

                    resources.add(new ProviderDiscoveredResource(
                            id,
                            name,
                            ProviderResourceType.SERVICE,
                            status,
                            region,
                            Map.of("serviceType", type, "repo", service.path("repo").asText(""))
                    ));
                }
            }
            return resources;
        } catch (Exception e) {
            log.warn("Render service discovery failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<ProviderDiscoveredEnvironment> discoverEnvironments(Map<String, Object> configuration, String credential, String providerResourceId) {
        // Render deployment tiers
        return List.of(
                new ProviderDiscoveredEnvironment("production", "Production Service Tier", "production", Map.of("description", "Live Production Deployment")),
                new ProviderDiscoveredEnvironment("staging", "Staging Service Tier", "staging", Map.of("description", "Pre-production Staging Deployment")),
                new ProviderDiscoveredEnvironment("development", "Development Service Tier", "development", Map.of("description", "Development / Test Deployment"))
        );
    }

    @Override
    public List<ProviderSecretMetadata> listSecrets(Map<String, Object> configuration, String credential, ProviderResourceMapping mapping) {
        try {
            String serviceId = mapping.getProviderResourceId();
            String responseBody = executeGet("/services/" + serviceId + "/env-vars?limit=100", credential);
            JsonNode root = objectMapper.readTree(responseBody);

            List<ProviderSecretMetadata> results = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode item : root) {
                    JsonNode envVar = item.has("envVar") ? item.get("envVar") : item;
                    String key = envVar.path("key").asText();
                    if (!key.isBlank()) {
                        results.add(new ProviderSecretMetadata(
                                key,
                                mapping.getProviderEnvironment(),
                                Instant.now(),
                                key
                        ));
                    }
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("Failed to list Render env vars for service [{}]: {}", mapping.getProviderResourceId(), e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public ProviderSecretOperationResult pushSecret(
            Map<String, Object> configuration,
            String credential,
            ProviderResourceMapping mapping,
            String secretName,
            String secretValue
    ) {
        try {
            String serviceId = mapping.getProviderResourceId();
            String uri = "/services/" + serviceId + "/env-vars/" + secretName;

            Map<String, Object> payload = Map.of("value", secretValue);
            executeRequest(HttpMethod.PUT, uri, credential, payload);

            return ProviderSecretOperationResult.success("PUSH", secretName, secretName);
        } catch (HttpClientErrorException.NotFound e) {
            // If direct PUT /env-vars/{key} returns 404, upsert via batch PUT /env-vars
            try {
                String serviceId = mapping.getProviderResourceId();
                String uri = "/services/" + serviceId + "/env-vars";
                List<Map<String, String>> batchPayload = List.of(Map.of("key", secretName, "value", secretValue));
                executeRequest(HttpMethod.PUT, uri, credential, batchPayload);
                return ProviderSecretOperationResult.success("PUSH", secretName, secretName);
            } catch (Exception ex) {
                return ProviderSecretOperationResult.failure("PUSH", secretName, ProviderErrorCode.PROVIDER_UNAVAILABLE, "Render API error: " + ex.getMessage());
            }
        } catch (HttpClientErrorException.Unauthorized e) {
            return ProviderSecretOperationResult.failure("PUSH", secretName, ProviderErrorCode.PROVIDER_AUTHENTICATION_FAILED, "Render API key unauthorized");
        } catch (HttpClientErrorException.Forbidden e) {
            return ProviderSecretOperationResult.failure("PUSH", secretName, ProviderErrorCode.PROVIDER_AUTHORIZATION_FAILED, "Insufficient permissions to modify Render service environment variables");
        } catch (HttpClientErrorException.TooManyRequests e) {
            return ProviderSecretOperationResult.failure("PUSH", secretName, ProviderErrorCode.PROVIDER_RATE_LIMITED, "Render API rate limit exceeded");
        } catch (Exception e) {
            log.warn("Failed to push secret [{}] to Render service: {}", secretName, e.getMessage());
            return ProviderSecretOperationResult.failure("PUSH", secretName, ProviderErrorCode.PROVIDER_UNAVAILABLE, "Render API error: " + e.getMessage());
        }
    }

    @Override
    public ProviderSecretOperationResult deleteSecret(
            Map<String, Object> configuration,
            String credential,
            ProviderResourceMapping mapping,
            String secretName
    ) {
        try {
            String serviceId = mapping.getProviderResourceId();
            String uri = "/services/" + serviceId + "/env-vars/" + secretName;

            executeRequest(HttpMethod.DELETE, uri, credential, null);
            return ProviderSecretOperationResult.success("DELETE", secretName, secretName);
        } catch (HttpClientErrorException.NotFound e) {
            return ProviderSecretOperationResult.success("DELETE", secretName, "not_found");
        } catch (Exception e) {
            log.warn("Failed to delete secret [{}] from Render service: {}", secretName, e.getMessage());
            return ProviderSecretOperationResult.failure("DELETE", secretName, ProviderErrorCode.PROVIDER_UNAVAILABLE, "Render API error: " + e.getMessage());
        }
    }

    private String executeGet(String uri, String credential) {
        return executeRequest(HttpMethod.GET, uri, credential, null);
    }

    private String executeRequest(HttpMethod method, String uri, String credential, Object body) {
        RestClient.RequestBodySpec spec = restClient.method(method)
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + credential)
                .accept(MediaType.APPLICATION_JSON);

        if (body != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }

        return spec.retrieve().body(String.class);
    }
}
