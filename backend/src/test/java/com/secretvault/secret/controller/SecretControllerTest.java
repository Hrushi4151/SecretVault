package com.secretvault.secret.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.project.dto.CreateProjectRequest;
import com.secretvault.secret.dto.CreateSecretRequest;
import com.secretvault.secret.dto.UpdateSecretRequest;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.workspace.dto.AddMemberRequest;
import com.secretvault.workspace.entity.WorkspaceRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecretControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static class TestContext {
        String token;
        String wsId;
        String projId;
        String envId;
    }

    private TestContext setupTestWorkspaceAndProject(String prefix) throws Exception {
        TestContext ctx = new TestContext();
        String ownerEmail = prefix + "_" + UUID.randomUUID() + "@example.com";
        RegisterRequest regReq = new RegisterRequest(ownerEmail, "Password123!Secure", "Secret Owner", "VaultCorp");
        MvcResult regRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated()).andReturn();
        ctx.token = objectMapper.readTree(regRes.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        MvcResult wsRes = mockMvc.perform(get("/api/v1/workspaces").header("Authorization", "Bearer " + ctx.token)).andReturn();
        ctx.wsId = objectMapper.readTree(wsRes.getResponse().getContentAsString()).path("data").get(0).path("id").asText();

        CreateProjectRequest projReq = new CreateProjectRequest("Core Security", "core-sec", "Sec Engine");
        MvcResult pRes = mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projReq)))
                .andExpect(status().isCreated()).andReturn();
        ctx.projId = objectMapper.readTree(pRes.getResponse().getContentAsString()).path("data").path("id").asText();

        // Get default production environment ID
        MvcResult envsRes = mockMvc.perform(get("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk()).andReturn();
        ctx.envId = objectMapper.readTree(envsRes.getResponse().getContentAsString()).path("data").get(0).path("id").asText();
        return ctx;
    }

    @Test
    @DisplayName("Complete Secret Lifecycle: Create, List Masked, Reveal v1, Update to v2, View Versions, Reveal Historical, Soft Delete")
    void testSecretLifecycle() throws Exception {
        TestContext ctx = setupTestWorkspaceAndProject("lifecycle");

        // 1. Create Secret
        CreateSecretRequest createReq = new CreateSecretRequest(
                "DATABASE_PASSWORD", "SuperSecurePassword123!", "Production PostgreSQL primary password"
        );
        MvcResult createRes = mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("DATABASE_PASSWORD"))
                .andExpect(jsonPath("$.data.maskedValue").value("••••••••••••••••"))
                .andExpect(jsonPath("$.data.currentVersionNumber").value(1))
                .andReturn();
        String secretId = objectMapper.readTree(createRes.getResponse().getContentAsString()).path("data").path("id").asText();

        // 2. List Secrets (Verify only metadata & masked value returned)
        mockMvc.perform(get("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("DATABASE_PASSWORD"))
                .andExpect(jsonPath("$.data[0].maskedValue").value("••••••••••••••••"))
                .andExpect(jsonPath("$.data[0].currentVersionNumber").value(1));

        // 3. Reveal Secret (Version 1)
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets/" + secretId + "/reveal")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("DATABASE_PASSWORD"))
                .andExpect(jsonPath("$.data.value").value("SuperSecurePassword123!"))
                .andExpect(jsonPath("$.data.versionNumber").value(1));

        // 4. Update Secret with new value (Rotate to Version 2)
        UpdateSecretRequest updateReq = new UpdateSecretRequest(
                "Rotated primary DB password", null, "NewRotatedPassword456!", "Annual key rotation"
        );
        mockMvc.perform(patch("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets/" + secretId)
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersionNumber").value(2))
                .andExpect(jsonPath("$.data.description").value("Rotated primary DB password"));

        // 5. List Secret Versions (should have 2 versions)
        mockMvc.perform(get("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets/" + secretId + "/versions")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].versionNumber").value(2))
                .andExpect(jsonPath("$.data[1].versionNumber").value(1));

        // 6. Reveal Historical Version 1
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets/" + secretId + "/reveal?version=1")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value("SuperSecurePassword123!"))
                .andExpect(jsonPath("$.data.versionNumber").value(1));

        // 7. Reveal Current Version 2
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets/" + secretId + "/reveal")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value("NewRotatedPassword456!"))
                .andExpect(jsonPath("$.data.versionNumber").value(2));

        // 8. Soft Delete Secret
        mockMvc.perform(delete("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets/" + secretId)
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk());

        // 9. Verify Secret is no longer returned in active list
        mockMvc.perform(get("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("RBAC Protection: VIEWER role cannot create, reveal, or delete secrets")
    void testViewerRolePermissions() throws Exception {
        TestContext ctx = setupTestWorkspaceAndProject("viewer_rbac");

        // Create a secret as OWNER
        CreateSecretRequest createReq = new CreateSecretRequest(
                "API_KEY", "secret_key_val", "Main API Key"
        );
        MvcResult createRes = mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated()).andReturn();
        String secretId = objectMapper.readTree(createRes.getResponse().getContentAsString()).path("data").path("id").asText();

        // Register Viewer
        String viewerEmail = "viewer_" + UUID.randomUUID() + "@example.com";
        RegisterRequest viewerReg = new RegisterRequest(viewerEmail, "Password123!Secure", "Viewer User", "Auditors");
        MvcResult viewerRegRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(viewerReg)))
                .andExpect(status().isCreated()).andReturn();
        String viewerToken = objectMapper.readTree(viewerRegRes.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        // Owner adds Viewer with VIEWER role
        AddMemberRequest addReq = new AddMemberRequest(viewerEmail, WorkspaceRole.VIEWER);
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/members")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isCreated());

        // 1. Viewer can list metadata
        mockMvc.perform(get("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        // 2. Viewer CANNOT create secret (403 Forbidden)
        CreateSecretRequest disallowedCreate = new CreateSecretRequest("TEST_KEY", "val", "desc");
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disallowedCreate)))
                .andExpect(status().isForbidden());

        // 3. Viewer CANNOT reveal secret (403 Forbidden)
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets/" + secretId + "/reveal")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());

        // 4. Viewer CANNOT delete secret (403 Forbidden)
        mockMvc.perform(delete("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets/" + secretId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Hierarchy IDOR Prevention: Secret queried under non-matching environment/project is rejected with 404")
    void testHierarchicalIdorPrevention() throws Exception {
        TestContext ctx = setupTestWorkspaceAndProject("idor");

        // Create Secret in Environment 1
        CreateSecretRequest createReq = new CreateSecretRequest("PAYMENT_KEY", "pay_live_secret", "Payments");
        MvcResult createRes = mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated()).andReturn();
        String secretId = objectMapper.readTree(createRes.getResponse().getContentAsString()).path("data").path("id").asText();

        // Create a Second Project
        CreateProjectRequest p2Req = new CreateProjectRequest("Project Two", "proj-two", "P2");
        MvcResult p2Res = mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p2Req)))
                .andExpect(status().isCreated()).andReturn();
        String p2Id = objectMapper.readTree(p2Res.getResponse().getContentAsString()).path("data").path("id").asText();

        // Attempt to access secret under Project 2's URL path -> MUST return 404 Not Found
        mockMvc.perform(get("/api/v1/workspaces/" + ctx.wsId + "/projects/" + p2Id + "/environments/" + ctx.envId + "/secrets/" + secretId)
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isNotFound());

        // Attempt to reveal secret under Project 2's URL path -> MUST return 404 Not Found
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + p2Id + "/environments/" + ctx.envId + "/secrets/" + secretId + "/reveal")
                        .header("Authorization", "Bearer " + ctx.token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Duplicate Secret Name in same environment returns 409 Conflict")
    void testDuplicateSecretName() throws Exception {
        TestContext ctx = setupTestWorkspaceAndProject("duplicate");

        CreateSecretRequest createReq = new CreateSecretRequest("SAME_KEY_NAME", "val1", "First");
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated());

        // Attempt to create with same key name again
        CreateSecretRequest dupReq = new CreateSecretRequest("SAME_KEY_NAME", "val2", "Second");
        mockMvc.perform(post("/api/v1/workspaces/" + ctx.wsId + "/projects/" + ctx.projId + "/environments/" + ctx.envId + "/secrets")
                        .header("Authorization", "Bearer " + ctx.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupReq)))
                .andExpect(status().isConflict());
    }
}
