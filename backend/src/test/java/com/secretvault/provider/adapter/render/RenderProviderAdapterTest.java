package com.secretvault.provider.adapter.render;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RenderProviderAdapterTest {

    private static final String BASE_URL = "https://api.render.com/v1";
    private RenderProviderAdapter adapter;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        adapter = new RenderProviderAdapter(BASE_URL, objectMapper, restClientBuilder.build());
    }

    @Test
    @DisplayName("Provider Type and Capabilities match Render contract")
    void testProviderTypeAndCapabilities() {
        assertThat(adapter.getProviderType()).isEqualTo(ProviderType.RENDER);
        assertThat(adapter.getCapabilities()).contains(
                ProviderCapability.VALIDATE_CONNECTION,
                ProviderCapability.DISCOVER_SERVICES,
                ProviderCapability.DISCOVER_PROJECTS,
                ProviderCapability.DISCOVER_ENVIRONMENTS,
                ProviderCapability.READ_SECRET_METADATA,
                ProviderCapability.WRITE_SECRETS,
                ProviderCapability.DELETE_SECRETS
        );
    }

    @Test
    @DisplayName("Validate Connection: Success with owner account")
    void testValidateConnectionSuccess() {
        mockServer.expect(requestTo(BASE_URL + "/owners?limit=1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer rnd_token_123"))
                .andRespond(withSuccess("""
                        [
                            {
                                "owner": {
                                    "id": "tea_render_corp",
                                    "name": "Render Engineering Team",
                                    "email": "dev@render.com"
                                }
                            }
                        ]
                        """, MediaType.APPLICATION_JSON));

        ProviderValidationResult result = adapter.validateConnection(Map.of(), "rnd_token_123");
        mockServer.verify();

        assertThat(result.valid()).isTrue();
        assertThat(result.accountOrTeamName()).isEqualTo("Render Engineering Team");
        assertThat(result.accountId()).isEqualTo("tea_render_corp");
    }

    @Test
    @DisplayName("Validate Connection: 401 Unauthorized maps to PROVIDER_AUTHENTICATION_FAILED")
    void testValidateConnectionUnauthorized() {
        mockServer.expect(requestTo(BASE_URL + "/owners?limit=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"message\":\"unauthorized\"}"));

        ProviderValidationResult result = adapter.validateConnection(Map.of(), "bad_rnd_key");
        mockServer.verify();

        assertThat(result.valid()).isFalse();
        assertThat(result.errorCode()).isEqualTo(ProviderErrorCode.PROVIDER_AUTHENTICATION_FAILED);
    }

    @Test
    @DisplayName("Discover Resources: Parses services from Render")
    void testDiscoverResources() {
        mockServer.expect(requestTo(BASE_URL + "/services?limit=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                            {
                                "service": {
                                    "id": "srv_api_99",
                                    "name": "payment-service",
                                    "type": "web_service",
                                    "repo": "https://github.com/acme/payment",
                                    "region": "frankfurt"
                                }
                            }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<ProviderDiscoveredResource> services = adapter.discoverResources(Map.of(), "token");
        mockServer.verify();

        assertThat(services).hasSize(1);
        assertThat(services.get(0).providerResourceId()).isEqualTo("srv_api_99");
        assertThat(services.get(0).name()).isEqualTo("payment-service");
        assertThat(services.get(0).type()).isEqualTo(ProviderResourceType.SERVICE);
    }

    @Test
    @DisplayName("Discover Environments: Returns production, staging, development service tiers")
    void testDiscoverEnvironments() {
        List<ProviderDiscoveredEnvironment> envs = adapter.discoverEnvironments(Map.of(), "token", "srv_api_99");
        assertThat(envs).extracting(ProviderDiscoveredEnvironment::target)
                .containsExactlyInAnyOrder("production", "staging", "development");
    }

    @Test
    @DisplayName("List Secrets: Lists environment variables for Render service")
    void testListSecrets() {
        ProviderResourceMapping mapping = new ProviderResourceMapping();
        mapping.setProviderResourceId("srv_api_99");
        mapping.setProviderEnvironment("production");

        mockServer.expect(requestTo(BASE_URL + "/services/srv_api_99/env-vars?limit=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                            {
                                "envVar": {
                                    "key": "PORT",
                                    "value": "8080"
                                }
                            },
                            {
                                "envVar": {
                                    "key": "DATABASE_URL",
                                    "value": "postgresql://..."
                                }
                            }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<ProviderSecretMetadata> envVars = adapter.listSecrets(Map.of(), "token", mapping);
        mockServer.verify();

        assertThat(envVars).hasSize(2);
        assertThat(envVars).extracting(ProviderSecretMetadata::key).containsExactly("PORT", "DATABASE_URL");
    }

    @Test
    @DisplayName("Push Secret: Pushes variable update to Render service")
    void testPushSecret() {
        ProviderResourceMapping mapping = new ProviderResourceMapping();
        mapping.setProviderResourceId("srv_api_99");
        mapping.setProviderEnvironment("production");

        mockServer.expect(requestTo(BASE_URL + "/services/srv_api_99/env-vars/JWT_SECRET"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        ProviderSecretOperationResult result = adapter.pushSecret(
                Map.of(), "token", mapping, "JWT_SECRET", "super_secret_jwt_signing_key_123"
        );
        mockServer.verify();

        assertThat(result.success()).isTrue();
        assertThat(result.key()).isEqualTo("JWT_SECRET");
    }

    @Test
    @DisplayName("Delete Secret: Deletes variable from Render service")
    void testDeleteSecret() {
        ProviderResourceMapping mapping = new ProviderResourceMapping();
        mapping.setProviderResourceId("srv_api_99");
        mapping.setProviderEnvironment("production");

        mockServer.expect(requestTo(BASE_URL + "/services/srv_api_99/env-vars/UNUSED_VAR"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        ProviderSecretOperationResult result = adapter.deleteSecret(Map.of(), "token", mapping, "UNUSED_VAR");
        mockServer.verify();

        assertThat(result.success()).isTrue();
        assertThat(result.key()).isEqualTo("UNUSED_VAR");
    }
}
