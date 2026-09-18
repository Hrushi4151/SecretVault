package com.secretvault.secret.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.environment.dto.CreateEnvironmentRequest;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.project.dto.CreateProjectRequest;
import com.secretvault.secret.dto.CreateSecretRequest;
import com.secretvault.secret.dto.RollbackSecretRequest;
import com.secretvault.secret.dto.SecretVersionTagRequest;
import com.secretvault.secret.dto.UpdateSecretRequest;
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
class SecretVersionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private record TestContext(String token, UUID userId, UUID wsId, UUID projId, UUID envId) {
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
        UUID envId = UUID.fromString(objectMapper.readTree(envsRes).path("data").get(0).path("id").asText());

        return new TestContext(token, userId, wsId, projId, envId);
    }

    @Test
    @DisplayName("End-to-End: Version history, tags, diff comparison, value diff, and rollback-as-new-version")
    void testVersionLifecycleAndRollbackE2E() throws Exception {
        TestContext ctx = setupContext();

        // 1. Create Secret (v1)
        CreateSecretRequest createReq = new CreateSecretRequest("PAYMENT_GATEWAY_KEY", "pk_live_initial_version_1", "Stripe key");
        String createRes = mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID secretId = UUID.fromString(objectMapper.readTree(createRes).get("data").get("id").asText());

        // 2. Update Secret (v2)
        UpdateSecretRequest updateReq1 = new UpdateSecretRequest(null, null, "pk_live_updated_version_2", "Rotated stripe key");
        mockMvc.perform(patch("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets/" + secretId)
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq1)))
                .andExpect(status().isOk());

        // 3. Update Secret (v3)
        UpdateSecretRequest updateReq2 = new UpdateSecretRequest(null, null, "pk_live_final_version_3", "Final stripe key");
        mockMvc.perform(patch("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets/" + secretId)
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq2)))
                .andExpect(status().isOk());

        // 4. Get Paginated Versions
        mockMvc.perform(get("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets/" + secretId + "/versions")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(3)))
                .andExpect(jsonPath("$.data.content[0].versionNumber").value(3))
                .andExpect(jsonPath("$.data.content[0].isCurrent").value(true))
                .andExpect(jsonPath("$.data.content[1].versionNumber").value(2))
                .andExpect(jsonPath("$.data.content[2].versionNumber").value(1));

        // 5. Add Tag to v1
        SecretVersionTagRequest tagReq = new SecretVersionTagRequest("stable-v1");
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets/" + secretId + "/versions/1/tags")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tagReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("stable-v1"));

        // 6. List Tags on v1
        mockMvc.perform(get("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets/" + secretId + "/versions/1/tags")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("stable-v1"));

        // 7. Metadata-level compare v1 vs v3
        mockMvc.perform(get("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets/" + secretId + "/versions/compare?from=1&to=3")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isEqual").value(false))
                .andExpect(jsonPath("$.data.diffType").value("MODIFIED"));

        // 8. Value-level diff v1 vs v3
        mockMvc.perform(get("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets/" + secretId + "/versions/diff?from=1&to=3")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isEqual").value(false))
                .andExpect(jsonPath("$.data.diffLines", hasSize(greaterThan(0))));

        // 9. Rollback to v1 (creates v4)
        RollbackSecretRequest rollbackReq = new RollbackSecretRequest(1, 3, "Rollback to stable v1");
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets/" + secretId + "/rollback")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rollbackReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.versionNumber").value(4))
                .andExpect(jsonPath("$.data.versionType").value("ROLLBACK"))
                .andExpect(jsonPath("$.data.isCurrent").value(true));

        // 10. Reveal v4 and confirm it matches original v1 plaintext
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets/" + secretId + "/versions/4/reveal")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value("pk_live_initial_version_1"))
                .andExpect(jsonPath("$.data.versionNumber").value(4));
    }
}
