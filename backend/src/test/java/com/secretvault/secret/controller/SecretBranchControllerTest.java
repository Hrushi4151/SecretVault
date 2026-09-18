package com.secretvault.secret.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.environment.dto.CreateEnvironmentRequest;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.project.dto.CreateProjectRequest;
import com.secretvault.secret.dto.BranchCommitRequest;
import com.secretvault.secret.dto.BranchMergeRequest;
import com.secretvault.secret.dto.CreateBranchRequest;
import com.secretvault.secret.dto.CreateSecretRequest;
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
class SecretBranchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private record TestContext(String token, UUID userId, UUID wsId, UUID projId, UUID devEnvId, UUID stagingEnvId, UUID prodEnvId) {
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
        
        JsonNode envs = objectMapper.readTree(envsRes).path("data");
        UUID devEnvId = null;
        UUID stagingEnvId = null;
        UUID prodEnvId = null;

        for (JsonNode env : envs) {
            String type = env.path("envType").asText();
            if ("DEVELOPMENT".equals(type)) devEnvId = UUID.fromString(env.path("id").asText());
            if ("STAGING".equals(type)) stagingEnvId = UUID.fromString(env.path("id").asText());
            if ("PRODUCTION".equals(type)) prodEnvId = UUID.fromString(env.path("id").asText());
        }

        return new TestContext(token, userId, wsId, projId, devEnvId, stagingEnvId, prodEnvId);
    }

    @Test
    @DisplayName("End-to-End: Branch creation, branch commit, compare, and 3-way merge into main on DEVELOPMENT")
    void testBranchAndMergeE2E() throws Exception {
        TestContext ctx = setupContext();

        // 1. Create Secret (v1 on main)
        CreateSecretRequest createReq = new CreateSecretRequest("REDIS_URL", "redis://localhost:6379", "Cache URL");
        String createRes = mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.devEnvId + "/secrets")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID secretId = UUID.fromString(objectMapper.readTree(createRes).get("data").get("id").asText());

        // 2. Create feature branch from v1
        CreateBranchRequest branchReq = new CreateBranchRequest("feature/redis-cluster", 1, "Clustered Redis setup");
        String branchRes = mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.devEnvId + "/secrets/" + secretId + "/branches")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(branchReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("feature/redis-cluster"))
                .andReturn().getResponse().getContentAsString();
        UUID branchId = UUID.fromString(objectMapper.readTree(branchRes).get("data").get("id").asText());

        // 3. Commit to feature branch (v2)
        BranchCommitRequest commitReq = new BranchCommitRequest("redis://cluster-node-1:6379,redis://cluster-node-2:6379", 1, "Enabled cluster mode");
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.devEnvId + "/secrets/" + secretId + "/branches/" + branchId + "/versions")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commitReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.versionNumber").value(2))
                .andExpect(jsonPath("$.data.versionType").value("BRANCH_COMMIT"));

        // 4. Compare branch with main
        mockMvc.perform(get("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.devEnvId + "/secrets/" + secretId + "/branches/" + branchId + "/compare")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasDiverged").value(true))
                .andExpect(jsonPath("$.data.canAutoMerge").value(true));

        // 5. Execute 3-way merge into main
        BranchMergeRequest mergeReq = new BranchMergeRequest(1, 2, "Merge redis cluster config");
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.devEnvId + "/secrets/" + secretId + "/branches/" + branchId + "/merge")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mergeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MERGED"))
                .andExpect(jsonPath("$.data.mergeVersionNumber").value(3));

        // 6. Reveal main and confirm it has the merged clustered redis URL
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.devEnvId + "/secrets/" + secretId + "/reveal")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value("redis://cluster-node-1:6379,redis://cluster-node-2:6379"))
                .andExpect(jsonPath("$.data.versionNumber").value(3));
    }

    @Test
    @DisplayName("Security Hardening: Staging and Production MUST reject branch creation with BRANCHES_NOT_ALLOWED_FOR_ENVIRONMENT")
    void testBranchCreation_RejectedInStagingAndProduction() throws Exception {
        TestContext ctx = setupContext();

        // 1. Create Secret in Production
        CreateSecretRequest createProdSecret = new CreateSecretRequest("PROD_DB", "postgres://prod:5432/db", "Prod DB");
        String prodSecretRes = mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.prodEnvId + "/secrets")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createProdSecret)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID prodSecretId = UUID.fromString(objectMapper.readTree(prodSecretRes).get("data").get("id").asText());

        // 2. Attempt branch creation on Production -> MUST RETURN 400 with BRANCHES_NOT_ALLOWED_FOR_ENVIRONMENT
        CreateBranchRequest prodBranchReq = new CreateBranchRequest("feature/prod-bypass", 1, "Illegal branch in prod");
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.prodEnvId + "/secrets/" + prodSecretId + "/branches")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodBranchReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BRANCHES_NOT_ALLOWED_FOR_ENVIRONMENT"))
                .andExpect(jsonPath("$.message").value(containsString("only permitted in DEVELOPMENT environments")));

        // 3. Create Secret in Staging
        CreateSecretRequest createStgSecret = new CreateSecretRequest("STG_DB", "postgres://stg:5432/db", "Stg DB");
        String stgSecretRes = mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.stagingEnvId + "/secrets")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createStgSecret)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID stgSecretId = UUID.fromString(objectMapper.readTree(stgSecretRes).get("data").get("id").asText());

        // 4. Attempt branch creation on Staging -> MUST RETURN 400 with BRANCHES_NOT_ALLOWED_FOR_ENVIRONMENT
        CreateBranchRequest stgBranchReq = new CreateBranchRequest("feature/stg-bypass", 1, "Illegal branch in staging");
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.stagingEnvId + "/secrets/" + stgSecretId + "/branches")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stgBranchReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BRANCHES_NOT_ALLOWED_FOR_ENVIRONMENT"))
                .andExpect(jsonPath("$.message").value(containsString("only permitted in DEVELOPMENT environments")));
    }
}
