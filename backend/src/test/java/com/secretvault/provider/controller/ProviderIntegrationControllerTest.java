package com.secretvault.provider.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.project.dto.CreateProjectRequest;
import com.secretvault.provider.adapter.ProviderAdapter;
import com.secretvault.provider.adapter.ProviderAdapterRegistry;
import com.secretvault.provider.dto.CreateProviderIntegrationRequest;
import com.secretvault.provider.dto.CreateResourceMappingRequest;
import com.secretvault.provider.dto.UpdateProviderIntegrationRequest;
import com.secretvault.provider.dto.UpdateResourceMappingRequest;
import com.secretvault.provider.model.IntegrationStatus;
import com.secretvault.provider.model.ProviderCapability;
import com.secretvault.provider.model.ProviderDiscoveredEnvironment;
import com.secretvault.provider.model.ProviderDiscoveredResource;
import com.secretvault.provider.model.ProviderResourceType;
import com.secretvault.provider.model.ProviderSecretMetadata;
import com.secretvault.provider.model.ProviderSecretOperationResult;
import com.secretvault.provider.model.ProviderType;
import com.secretvault.provider.model.ProviderValidationResult;
import com.secretvault.secret.dto.CreateSecretRequest;
import com.secretvault.workspace.dto.AddMemberRequest;
import com.secretvault.workspace.entity.WorkspaceRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProviderIntegrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProviderAdapterRegistry adapterRegistry;

    private ProviderAdapter mockVercelAdapter;
    private ProviderAdapter mockRenderAdapter;

    private static class TestContext {
        String token;
        String wsId;
        String projId;
        String envId;
    }

    @BeforeEach
    void setUp() {
        mockVercelAdapter = Mockito.mock(ProviderAdapter.class);
        mockRenderAdapter = Mockito.mock(ProviderAdapter.class);

        when(mockVercelAdapter.getProviderType()).thenReturn(ProviderType.VERCEL);
        when(mockRenderAdapter.getProviderType()).thenReturn(ProviderType.RENDER);

        Set<ProviderCapability> vercelCaps = Set.of(
                ProviderCapability.VALIDATE_CONNECTION,
                ProviderCapability.DISCOVER_PROJECTS,
                ProviderCapability.DISCOVER_ENVIRONMENTS,
                ProviderCapability.READ_SECRET_METADATA,
                ProviderCapability.WRITE_SECRETS,
                ProviderCapability.DELETE_SECRETS
        );
        when(mockVercelAdapter.getCapabilities()).thenReturn(vercelCaps);
        when(mockRenderAdapter.getCapabilities()).thenReturn(vercelCaps);

        when(adapterRegistry.getAdapter(ProviderType.VERCEL)).thenReturn(mockVercelAdapter);
        when(adapterRegistry.getAdapter(ProviderType.RENDER)).thenReturn(mockRenderAdapter);
    }

    private TestContext setupTestWorkspace(String prefix) throws Exception {
        TestContext ctx = new TestContext();
        String ownerEmail = prefix + "_" + UUID.randomUUID() + "@example.com";
        RegisterRequest regReq = new RegisterRequest(ownerEmail, "Password123!Secure", "Provider Owner", "PlatformCorp");
        MvcResult regRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated()).andReturn();
        ctx.token = objectMapper.readTree(regRes.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        MvcResult wsRes = mockMvc.perform(get("/api/v1/workspaces").header("Authorization", "Bearer " + ctx.token)).andReturn();
        ctx.wsId = objectMapper.readTree(wsRes.getResponse().getContentAsString()).path("data").get(0).path("id").asText();

        CreateProjectRequest projReq = new CreateProjectRequest("Web App", "web-app", "Web frontend");
        MvcResult pRes = mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projReq)))
                .andExpect(status().isCreated()).andReturn();
        ctx.projId = objectMapper.readTree(pRes.getResponse().getContentAsString()).path("data").path("id").asText();

        MvcResult envsRes = mockMvc.perform(get("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk()).andReturn();
        ctx.envId = objectMapper.readTree(envsRes.getResponse().getContentAsString()).path("data").get(0).path("id").asText();
        return ctx;
    }

    @Test
    @DisplayName("Complete Provider Integration Lifecycle: Create, List, Get, Validate, Discover, Map, Update, Delete")
    void testProviderIntegrationLifecycle() throws Exception {
        TestContext ctx = setupTestWorkspace("integ_lifecycle");
        String canaryToken = "vercel_tok_sec_1234567890abcdef";

        when(mockVercelAdapter.validateConnection(any(), anyString()))
                .thenReturn(ProviderValidationResult.success("Acme Org", "user-123", Set.of(ProviderCapability.WRITE_SECRETS)));

        when(mockVercelAdapter.discoverResources(any(), anyString()))
                .thenReturn(List.of(
                        new ProviderDiscoveredResource("prj_vercel_1", "nextjs-app", ProviderResourceType.PROJECT, "READY", "iad1", Map.of())
                ));

        when(mockVercelAdapter.discoverEnvironments(any(), anyString(), anyString()))
                .thenReturn(List.of(
                        new ProviderDiscoveredEnvironment("production", "Production", "production", Map.of()),
                        new ProviderDiscoveredEnvironment("preview", "Preview", "preview", Map.of())
                ));

        // 1. Create Integration
        CreateProviderIntegrationRequest createReq = new CreateProviderIntegrationRequest(
                ProviderType.VERCEL,
                "Vercel Production Deployment",
                canaryToken,
                Map.of("teamId", "team_123")
        );

        MvcResult createRes = mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/integrations")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.displayName").value("Vercel Production Deployment"))
                .andExpect(jsonPath("$.data.providerType").value("VERCEL"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.redactedCredentialHint").isNotEmpty())
                .andReturn();

        String rawResponse = createRes.getResponse().getContentAsString();
        assertThat(rawResponse).doesNotContain(canaryToken);

        String integrationId = objectMapper.readTree(rawResponse).path("data").path("id").asText();

        // 2. List Integrations
        mockMvc.perform(get("/api/v1/workspaces/" + ctx.wsId + "/integrations")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(integrationId));

        // 3. Get Integration details
        mockMvc.perform(get("/api/v1/workspaces/" + ctx.wsId + "/integrations/" + integrationId)
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(integrationId))
                .andExpect(jsonPath("$.data.displayName").value("Vercel Production Deployment"));

        // 4. Validate Connection
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/integrations/" + integrationId + "/validate")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.accountOrTeamName").value("Acme Org"));

        // 5. Capabilities
        mockMvc.perform(get("/api/v1/workspaces/" + ctx.wsId + "/integrations/" + integrationId + "/capabilities")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasItem("WRITE_SECRETS")));

        // 6. Discover Resources & Environments
        mockMvc.perform(get("/api/v1/workspaces/" + ctx.wsId + "/integrations/" + integrationId + "/resources")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].providerResourceId").value("prj_vercel_1"))
                .andExpect(jsonPath("$.data[0].name").value("nextjs-app"));

        mockMvc.perform(get("/api/v1/workspaces/" + ctx.wsId + "/integrations/" + integrationId + "/resources/prj_vercel_1/environments")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].providerEnvironmentId").value("production"));

        // 7. Create Resource Mapping
        CreateResourceMappingRequest mappingReq = new CreateResourceMappingRequest(
                UUID.fromString(ctx.projId),
                UUID.fromString(ctx.envId),
                ProviderResourceType.PROJECT,
                "prj_vercel_1",
                "nextjs-app",
                "production",
                Map.of(),
                true
        );

        MvcResult mappingRes = mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/integrations/" + integrationId + "/mappings")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mappingReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.providerResourceId").value("prj_vercel_1"))
                .andExpect(jsonPath("$.data.providerEnvironment").value("production"))
                .andReturn();

        String mappingId = objectMapper.readTree(mappingRes.getResponse().getContentAsString()).path("data").path("id").asText();

        // 8. List Mappings
        mockMvc.perform(get("/api/v1/workspaces/" + ctx.wsId + "/integrations/" + integrationId + "/mappings")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(mappingId));

        // 9. Update Mapping
        UpdateResourceMappingRequest updateMapReq = new UpdateResourceMappingRequest("nextjs-app-v2", "production", false, Map.of("tag", "v2"));
        mockMvc.perform(patch("/api/v1/workspaces/" + ctx.wsId + "/integrations/" + integrationId + "/mappings/" + mappingId)
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateMapReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.syncEnabled").value(false));

        // 10. Update Integration (displayName and status)
        UpdateProviderIntegrationRequest updateReq = new UpdateProviderIntegrationRequest(
                "Updated Vercel Integration",
                IntegrationStatus.DISABLED,
                null,
                null
        );
        mockMvc.perform(patch("/api/v1/workspaces/" + ctx.wsId + "/integrations/" + integrationId)
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Updated Vercel Integration"))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        // 11. Delete Mapping
        mockMvc.perform(delete("/api/v1/workspaces/" + ctx.wsId + "/integrations/" + integrationId + "/mappings/" + mappingId)
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk());

        // 12. Delete Integration
        mockMvc.perform(delete("/api/v1/workspaces/" + ctx.wsId + "/integrations/" + integrationId)
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Provider Secret Synchronization and Canary Secret Leak Prevention")
    void testProviderSecretPushAndCanaryProtection() throws Exception {
        TestContext ctx = setupTestWorkspace("push_sync");
        String canarySecretValue = "SUPER_SECRET_CANARY_VALUE_998877";

        when(mockVercelAdapter.validateConnection(any(), anyString()))
                .thenReturn(ProviderValidationResult.success("Org", "user-1", Set.of(ProviderCapability.WRITE_SECRETS)));

        when(mockVercelAdapter.pushSecret(any(), anyString(), any(), anyString(), anyString()))
                .thenReturn(ProviderSecretOperationResult.success("CREATE", "API_KEY", "env_var_vercel_123"));

        when(mockVercelAdapter.listSecrets(any(), anyString(), any()))
                .thenReturn(List.of(new ProviderSecretMetadata("API_KEY", "production", Instant.now(), "env_var_vercel_123")));

        when(mockVercelAdapter.deleteSecret(any(), anyString(), any(), anyString()))
                .thenReturn(ProviderSecretOperationResult.success("DELETE", "API_KEY", "env_var_vercel_123"));

        // 1. Create SecretVault Secret with canary
        CreateSecretRequest secReq = new CreateSecretRequest("API_KEY", canarySecretValue, "Production API key");
        MvcResult secRes = mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secReq)))
                .andExpect(status().isCreated()).andReturn();
        String secretId = objectMapper.readTree(secRes.getResponse().getContentAsString()).path("data").path("id").asText();

        // 2. Create Integration
        CreateProviderIntegrationRequest createIntegReq = new CreateProviderIntegrationRequest(
                ProviderType.VERCEL, "Vercel Sync", "tok_vercel_valid_123", Map.of()
        );
        MvcResult integRes = mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/integrations")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createIntegReq)))
                .andExpect(status().isCreated()).andReturn();
        String integrationId = objectMapper.readTree(integRes.getResponse().getContentAsString()).path("data").path("id").asText();

        // 3. Create Resource Mapping
        CreateResourceMappingRequest mappingReq = new CreateResourceMappingRequest(
                UUID.fromString(ctx.projId),
                UUID.fromString(ctx.envId),
                ProviderResourceType.PROJECT,
                "prj_v_1",
                "Frontend",
                "production",
                Map.of(),
                true
        );
        MvcResult mappingRes = mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/integrations/" + integrationId + "/mappings")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mappingReq)))
                .andExpect(status().isCreated()).andReturn();
        String mappingId = objectMapper.readTree(mappingRes.getResponse().getContentAsString()).path("data").path("id").asText();

        // 4. Push Secret to Provider
        MvcResult pushRes = mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/integrations/" + integrationId + "/mappings/" + mappingId + "/push/" + secretId)
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.secretKey").value("API_KEY"))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.providerResourceId").value("prj_v_1"))
                .andReturn();

        // CRITICAL SAFETY ASSERTION: Ensure canary secret plaintext never appears in push response
        String pushResponseStr = pushRes.getResponse().getContentAsString();
        assertThat(pushResponseStr).doesNotContain(canarySecretValue);

        // 5. List Provider Secrets (Returns metadata only)
        mockMvc.perform(get("/api/v1/workspaces/" + ctx.wsId + "/integrations/" + integrationId + "/mappings/" + mappingId + "/secrets")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].key").value("API_KEY"));

        // 6. Delete Secret on Provider
        mockMvc.perform(delete("/api/v1/workspaces/" + ctx.wsId + "/integrations/" + integrationId + "/mappings/" + mappingId + "/secrets/API_KEY")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true));
    }

    @Test
    @DisplayName("RBAC and Tenant Isolation: Viewer cannot manage integrations, Workspace B cannot access Workspace A")
    void testRbacAndTenantIsolation() throws Exception {
        TestContext wsA = setupTestWorkspace("ws_a");
        TestContext wsB = setupTestWorkspace("ws_b");

        // 1. Create a VIEWER member in Workspace A
        String viewerEmail = "viewer_" + UUID.randomUUID() + "@example.com";
        RegisterRequest viewerReg = new RegisterRequest(viewerEmail, "Password123!Secure", "Viewer User", "ViewerCorp");
        MvcResult viewerRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(viewerReg)))
                .andExpect(status().isCreated()).andReturn();
        String viewerToken = objectMapper.readTree(viewerRes.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        // Add to workspace A as VIEWER
        AddMemberRequest addReq = new AddMemberRequest(viewerEmail, WorkspaceRole.VIEWER);
        mockMvc.perform(post("/api/v1/workspaces/" + wsA.wsId + "/members")
                        .header("Authorization", "Bearer " + wsA.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isCreated());

        // 2. Admin creates Integration in Workspace A
        when(mockVercelAdapter.validateConnection(any(), anyString()))
                .thenReturn(ProviderValidationResult.success("Org", "user-1", Set.of(ProviderCapability.WRITE_SECRETS)));

        CreateProviderIntegrationRequest createReq = new CreateProviderIntegrationRequest(
                ProviderType.VERCEL, "Vercel A", "tok_vercel_a", Map.of()
        );
        MvcResult integRes = mockMvc.perform(post("/api/v1/workspaces/" + wsA.wsId + "/integrations")
                        .header("Authorization", "Bearer " + wsA.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated()).andReturn();
        String integrationIdA = objectMapper.readTree(integRes.getResponse().getContentAsString()).path("data").path("id").asText();

        // 3. VIEWER can view integration details
        mockMvc.perform(get("/api/v1/workspaces/" + wsA.wsId + "/integrations/" + integrationIdA)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Vercel A"));

        // 4. VIEWER is forbidden (403) from creating, updating, or deleting integrations
        mockMvc.perform(post("/api/v1/workspaces/" + wsA.wsId + "/integrations")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isForbidden());

        UpdateProviderIntegrationRequest updateReq = new UpdateProviderIntegrationRequest("Hacked Name", null, null, null);
        mockMvc.perform(patch("/api/v1/workspaces/" + wsA.wsId + "/integrations/" + integrationIdA)
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/workspaces/" + wsA.wsId + "/integrations/" + integrationIdA)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());

        // 5. Cross-Tenant IDOR: Workspace B admin cannot view or modify Workspace A's integration
        mockMvc.perform(get("/api/v1/workspaces/" + wsB.wsId + "/integrations/" + integrationIdA)
                        .header("Authorization", "Bearer " + wsB.token))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/workspaces/" + wsB.wsId + "/integrations/" + integrationIdA)
                        .header("Authorization", "Bearer " + wsB.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/workspaces/" + wsB.wsId + "/integrations/" + integrationIdA)
                        .header("Authorization", "Bearer " + wsB.token))
                .andExpect(status().isNotFound());
    }
}
