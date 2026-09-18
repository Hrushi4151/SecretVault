package com.secretvault.secret.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.environment.dto.CreateEnvironmentRequest;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.project.dto.CreateProjectRequest;
import com.secretvault.secret.dto.CreateSecretRequest;
import com.secretvault.secret.dto.ExecutePromotionRequest;
import com.secretvault.secret.dto.PromotionPreviewRequest;
import com.secretvault.workspace.dto.CreateWorkspaceRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecretPromotionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private record TestContext(String token, UUID userId, UUID wsId, UUID projId, UUID devEnvId, UUID stagingEnvId) {
    }

    private TestContext setupContext() throws Exception {
        String suffix = UUID.randomUUID().toString();
        RegisterRequest registerReq = new RegisterRequest("user_" + suffix + "@example.com", "Password123!", "User " + suffix, "Org " + suffix);
        String authRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(authRes).path("data").path("accessToken").asText();
        UUID userId = UUID.fromString(objectMapper.readTree(authRes).path("data").path("user").path("id").asText());

        CreateWorkspaceRequest wsReq = new CreateWorkspaceRequest("WS " + suffix, "ws-" + suffix);
        String wsRes = mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wsReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID wsId = UUID.fromString(objectMapper.readTree(wsRes).get("data").get("id").asText());

        CreateProjectRequest projReq = new CreateProjectRequest("App " + suffix, "app-" + suffix, "App project");
        String projRes = mockMvc.perform(post("/api/v1/workspaces/" + wsId + "/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID projId = UUID.fromString(objectMapper.readTree(projRes).get("data").get("id").asText());

        String envsRes = mockMvc.perform(get("/api/v1/workspaces/" + wsId + "/projects/" + projId + "/environments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID devEnvId = UUID.fromString(objectMapper.readTree(envsRes).path("data").get(0).path("id").asText());
        UUID stagingEnvId = UUID.fromString(objectMapper.readTree(envsRes).path("data").get(1).path("id").asText());

        return new TestContext(token, userId, wsId, projId, devEnvId, stagingEnvId);
    }

    @Test
    @DisplayName("End-to-End: Promotion preview and atomic cross-environment execution")
    void testPromotionLifecycleE2E() throws Exception {
        TestContext ctx = setupContext();

        // 1. Create secret in Development
        CreateSecretRequest createReq = new CreateSecretRequest("SENTRY_DSN", "https://key@sentry.io/12345", "Sentry tracking");
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.devEnvId + "/secrets")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated());

        // 2. Preview promotion to Staging
        PromotionPreviewRequest previewReq = new PromotionPreviewRequest(ctx.stagingEnvId, null);
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.devEnvId + "/promote/preview")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCandidates").value(1))
                .andExpect(jsonPath("$.data.addedCount").value(1))
                .andExpect(jsonPath("$.data.items[0].secretName").value("SENTRY_DSN"))
                .andExpect(jsonPath("$.data.items[0].status").value("ADDED"));

        // 3. Execute promotion to Staging
        ExecutePromotionRequest execReq = new ExecutePromotionRequest(ctx.stagingEnvId, null, null, "Promote Sentry config");
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.devEnvId + "/promote")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(execReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.promotedCount").value(1))
                .andExpect(jsonPath("$.data.promotedSecrets[0].action").value("CREATED"))
                .andExpect(jsonPath("$.data.promotedSecrets[0].newVersionNumber").value(1));

        // 4. List secrets in Staging and confirm SENTRY_DSN exists
        mockMvc.perform(get("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.stagingEnvId + "/secrets")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("SENTRY_DSN"));
    }
}
