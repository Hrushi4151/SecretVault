package com.secretvault.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.project.dto.CreateProjectRequest;
import com.secretvault.provider.adapter.ProviderAdapter;
import com.secretvault.provider.adapter.ProviderAdapterRegistry;
import com.secretvault.provider.dto.CreateProviderIntegrationRequest;
import com.secretvault.provider.dto.CreateResourceMappingRequest;
import com.secretvault.provider.model.*;
import com.secretvault.secret.dto.CreateSecretRequest;
import com.secretvault.sync.dto.UpdateDriftStatusRequest;
import com.secretvault.sync.model.DriftStatus;
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
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DriftControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProviderAdapterRegistry adapterRegistry;

    private ProviderAdapter mockVercelAdapter;

    private static class TestContext {
        String token;
        String wsId;
        String projId;
        String envId;
        String integId;
        String mapId;
    }

    @BeforeEach
    void setUp() {
        mockVercelAdapter = Mockito.mock(ProviderAdapter.class);
        when(mockVercelAdapter.getProviderType()).thenReturn(ProviderType.VERCEL);
        when(mockVercelAdapter.getCapabilities()).thenReturn(Set.of(
                ProviderCapability.VALIDATE_CONNECTION,
                ProviderCapability.READ_SECRET_METADATA,
                ProviderCapability.WRITE_SECRETS,
                ProviderCapability.DELETE_SECRETS
        ));
        when(adapterRegistry.getAdapter(ProviderType.VERCEL)).thenReturn(mockVercelAdapter);
    }

    private TestContext setupTestWorkspace(String prefix) throws Exception {
        TestContext ctx = new TestContext();
        String ownerEmail = prefix + "_" + UUID.randomUUID() + "@example.com";
        RegisterRequest regReq = new RegisterRequest(ownerEmail, "Password123!Secure", "Sync Owner", "SyncCorp");
        MvcResult regRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated()).andReturn();
        ctx.token = objectMapper.readTree(regRes.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        MvcResult wsRes = mockMvc.perform(get("/api/v1/workspaces").header("Authorization", "Bearer " + ctx.token)).andReturn();
        ctx.wsId = objectMapper.readTree(wsRes.getResponse().getContentAsString()).path("data").get(0).path("id").asText();

        CreateProjectRequest projReq = new CreateProjectRequest("App Project", "app-proj", "Application backend");
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

        // Create Integration
        when(mockVercelAdapter.validateConnection(any(), anyString()))
                .thenReturn(ProviderValidationResult.success("Test Team", "team_123", Set.of()));
        CreateProviderIntegrationRequest integReq = new CreateProviderIntegrationRequest(
                ProviderType.VERCEL, "Vercel Prod Integration", "ver_tok_abc_999", Map.of("teamId", "team_123")
        );
        MvcResult integRes = mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/integrations")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(integReq)))
                .andExpect(status().isCreated()).andReturn();
        ctx.integId = objectMapper.readTree(integRes.getResponse().getContentAsString()).path("data").path("id").asText();

        // Create Mapping
        CreateResourceMappingRequest mapReq = new CreateResourceMappingRequest(
                UUID.fromString(ctx.projId), UUID.fromString(ctx.envId),
                ProviderResourceType.PROJECT, "prj_vercel_123", "prj_vercel_123", "production",
                Map.of(), true
        );
        MvcResult mapRes = mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/integrations/" + ctx.integId + "/mappings")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mapReq)))
                .andExpect(status().isCreated()).andReturn();
        ctx.mapId = objectMapper.readTree(mapRes.getResponse().getContentAsString()).path("data").path("id").asText();

        return ctx;
    }

    @Test
    @DisplayName("GET /api/v1/workspaces/{workspaceId}/drift lists detected drifts after dry-run")
    void testDriftQueryAndStatusUpdate() throws Exception {
        TestContext ctx = setupTestWorkspace("drift_query_test");

        // Create a secret in SecretVault
        CreateSecretRequest secReq = new CreateSecretRequest("POSTGRES_PASSWORD", "SuperSecretPass123!", "DB password");
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secReq)))
                .andExpect(status().isCreated());

        // Mock Provider listing: empty list -> MISSING_FROM_PROVIDER drift
        when(mockVercelAdapter.validateConnection(any(), anyString()))
                .thenReturn(ProviderValidationResult.success("Test Team", "team_123", Set.of()));
        when(mockVercelAdapter.listSecrets(any(), anyString(), any()))
                .thenReturn(Collections.emptyList());

        // Run Dry-Run to detect drift
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/sync/dry-run")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.driftCount", greaterThanOrEqualTo(1)));

        // Query Drift Records
        MvcResult driftRes = mockMvc.perform(get("/api/v1/workspaces/" + ctx.wsId + "/drift")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.content[0].secretName", equalTo("POSTGRES_PASSWORD")))
                .andExpect(jsonPath("$.data.content[0].driftType", equalTo("MISSING_FROM_PROVIDER")))
                .andReturn();

        String driftId = objectMapper.readTree(driftRes.getResponse().getContentAsString())
                .path("data").path("content").get(0).path("id").asText();

        // Get Drift By ID
        mockMvc.perform(get("/api/v1/workspaces/" + ctx.wsId + "/drift/" + driftId)
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", equalTo(driftId)));

        // Update Drift Status to ACKNOWLEDGED
        UpdateDriftStatusRequest updateReq = new UpdateDriftStatusRequest(DriftStatus.ACKNOWLEDGED, "Investigating sync mismatch");
        mockMvc.perform(patch("/api/v1/workspaces/" + ctx.wsId + "/drift/" + driftId + "/status")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", equalTo("ACKNOWLEDGED")));
    }
}
