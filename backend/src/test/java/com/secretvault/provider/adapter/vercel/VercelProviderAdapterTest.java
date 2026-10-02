package com.secretvault.provider.adapter.vercel;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VercelProviderAdapterTest {

    private static final String BASE_URL = "https://api.vercel.com";
    private VercelProviderAdapter adapter;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        adapter = new VercelProviderAdapter(BASE_URL, objectMapper, restClientBuilder.build());
    }

    @Test
    @DisplayName("Provider Type and Capabilities match Vercel contract")
    void testProviderTypeAndCapabilities() {
        assertThat(adapter.getProviderType()).isEqualTo(ProviderType.VERCEL);
        assertThat(adapter.getCapabilities()).contains(
                ProviderCapability.VALIDATE_CONNECTION,
                ProviderCapability.DISCOVER_PROJECTS,
                ProviderCapability.DISCOVER_ENVIRONMENTS,
                ProviderCapability.READ_SECRET_METADATA,
                ProviderCapability.WRITE_SECRETS,
                ProviderCapability.DELETE_SECRETS
        );
    }

    @Test
    @DisplayName("Validate Connection: Success with user account")
    void testValidateConnectionSuccess() {
        mockServer.expect(requestTo(BASE_URL + "/v2/user"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test_token_123"))
                .andRespond(withSuccess("""
                        {
                            "user": {
                                "id": "usr_12345",
                                "username": "alice_dev",
                                "email": "alice@example.com"
                            }
                        }
                        """, MediaType.APPLICATION_JSON));

        ProviderValidationResult result = adapter.validateConnection(Map.of(), "test_token_123");
        mockServer.verify();

        assertThat(result.valid()).isTrue();
        assertThat(result.accountOrTeamName()).isEqualTo("alice_dev");
        assertThat(result.accountId()).isEqualTo("usr_12345");
        assertThat(result.capabilities()).isNotEmpty();
    }

    @Test
    @DisplayName("Validate Connection: 401 Unauthorized maps to PROVIDER_AUTHENTICATION_FAILED")
    void testValidateConnectionUnauthorized() {
        mockServer.expect(requestTo(BASE_URL + "/v2/user"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"error\":{\"code\":\"unauthorized\"}}"));

        ProviderValidationResult result = adapter.validateConnection(Map.of(), "bad_token");
        mockServer.verify();

        assertThat(result.valid()).isFalse();
        assertThat(result.errorCode()).isEqualTo(ProviderErrorCode.PROVIDER_AUTHENTICATION_FAILED);
    }

    @Test
    @DisplayName("Discover Projects: Parses project list from Vercel")
    void testDiscoverProjects() {
        mockServer.expect(requestTo(BASE_URL + "/v9/projects?limit=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                            "projects": [
                                {
                                    "id": "prj_web1",
                                    "name": "nextjs-dashboard",
                                    "framework": "nextjs",
                                    "accountId": "team_123"
                                }
                            ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<ProviderDiscoveredResource> projects = adapter.discoverResources(Map.of(), "token");
        mockServer.verify();

        assertThat(projects).hasSize(1);
        assertThat(projects.get(0).providerResourceId()).isEqualTo("prj_web1");
        assertThat(projects.get(0).name()).isEqualTo("nextjs-dashboard");
        assertThat(projects.get(0).type()).isEqualTo(ProviderResourceType.PROJECT);
    }

    @Test
    @DisplayName("Discover Environments: Returns standard production, preview, development targets")
    void testDiscoverEnvironments() {
        List<ProviderDiscoveredEnvironment> envs = adapter.discoverEnvironments(Map.of(), "token", "prj_web1");
        assertThat(envs).extracting(ProviderDiscoveredEnvironment::target)
                .containsExactlyInAnyOrder("production", "preview", "development");
    }

    @Test
    @DisplayName("Push Secret: Creates new encrypted env var when not existing")
    void testPushSecretNewVariable() {
        ProviderResourceMapping mapping = new ProviderResourceMapping();
        mapping.setProviderResourceId("prj_web1");
        mapping.setProviderEnvironment("production");

        // 1. listSecrets call (returns empty)
        mockServer.expect(requestTo(BASE_URL + "/v10/projects/prj_web1/env"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"envs\":[]}", MediaType.APPLICATION_JSON));

        // 2. create env var call
        mockServer.expect(requestTo(BASE_URL + "/v10/projects/prj_web1/env"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\":\"env_new_123\",\"key\":\"DATABASE_URL\"}", MediaType.APPLICATION_JSON));

        ProviderSecretOperationResult result = adapter.pushSecret(
                Map.of(), "token", mapping, "DATABASE_URL", "postgresql://user:pass@db:5432/db"
        );
        mockServer.verify();

        assertThat(result.success()).isTrue();
        assertThat(result.operation()).isEqualTo("CREATE");
        assertThat(result.providerSecretId()).isEqualTo("env_new_123");
    }

    @Test
    @DisplayName("Delete Secret: Deletes existing env var by key")
    void testDeleteSecret() {
        ProviderResourceMapping mapping = new ProviderResourceMapping();
        mapping.setProviderResourceId("prj_web1");
        mapping.setProviderEnvironment("production");

        // 1. listSecrets call
        mockServer.expect(requestTo(BASE_URL + "/v10/projects/prj_web1/env"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                            "envs": [
                                {
                                    "id": "env_target_456",
                                    "key": "STRIPE_SECRET_KEY",
                                    "target": ["production"]
                                }
                            ]
                        }
                        """, MediaType.APPLICATION_JSON));

        // 2. delete call
        mockServer.expect(requestTo(BASE_URL + "/v10/projects/prj_web1/env/env_target_456"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        ProviderSecretOperationResult result = adapter.deleteSecret(Map.of(), "token", mapping, "STRIPE_SECRET_KEY");
        mockServer.verify();

        assertThat(result.success()).isTrue();
        assertThat(result.operation()).isEqualTo("DELETE");
        assertThat(result.providerSecretId()).isEqualTo("env_target_456");
    }
}
