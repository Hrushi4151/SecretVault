package com.secretvault.environment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.environment.dto.CreateEnvironmentRequest;
import com.secretvault.environment.dto.UpdateEnvironmentRequest;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.project.dto.CreateProjectRequest;
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
class EnvironmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Environment Lifecycle: Seeded Environments, Custom Creation, Update, and Deletion")
    void testEnvironmentLifecycle() throws Exception {
        // 1. Register Owner
        String ownerEmail = "env_owner_" + UUID.randomUUID() + "@example.com";
        RegisterRequest regReq = new RegisterRequest(ownerEmail, "Password123!Secure", "Env Owner", "InfraOps");
        MvcResult regRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated()).andReturn();
        String token = objectMapper.readTree(regRes.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        MvcResult wsRes = mockMvc.perform(get("/api/v1/workspaces").header("Authorization", "Bearer " + token)).andReturn();
        String wsId = objectMapper.readTree(wsRes.getResponse().getContentAsString()).path("data").get(0).path("id").asText();

        // 2. Create Project
        CreateProjectRequest projReq = new CreateProjectRequest("Gateway", "gateway", "API Gateway");
        MvcResult pRes = mockMvc.perform(post("/api/v1/workspaces/" + wsId + "/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projReq)))
                .andExpect(status().isCreated()).andReturn();
        String projId = objectMapper.readTree(pRes.getResponse().getContentAsString()).path("data").path("id").asText();

        // 3. List Environments (should have 3 default environments: development, staging, production)
        mockMvc.perform(get("/api/v1/workspaces/" + wsId + "/projects/" + projId + "/environments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));

        // 4. Create Custom Environment
        CreateEnvironmentRequest createEnv = new CreateEnvironmentRequest(
                "Sandbox EU", "sandbox-eu", EnvType.DEVELOPMENT, "EU Developer sandbox", false
        );
        MvcResult envRes = mockMvc.perform(post("/api/v1/workspaces/" + wsId + "/projects/" + projId + "/environments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createEnv)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Sandbox EU"))
                .andExpect(jsonPath("$.data.slug").value("sandbox-eu"))
                .andReturn();
        String envId = objectMapper.readTree(envRes.getResponse().getContentAsString()).path("data").path("id").asText();

        // 5. Get Environment by ID
        mockMvc.perform(get("/api/v1/workspaces/" + wsId + "/projects/" + projId + "/environments/" + envId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(envId))
                .andExpect(jsonPath("$.data.slug").value("sandbox-eu"));

        // 6. Update Environment
        UpdateEnvironmentRequest updateEnv = new UpdateEnvironmentRequest(
                "Sandbox EU Frankfurt", "Frankfurt AWS cluster", EnvType.DEVELOPMENT, true, null
        );
        mockMvc.perform(patch("/api/v1/workspaces/" + wsId + "/projects/" + projId + "/environments/" + envId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateEnv)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Sandbox EU Frankfurt"))
                .andExpect(jsonPath("$.data.isProtected").value(true));

        // 7. Delete Environment
        mockMvc.perform(delete("/api/v1/workspaces/" + wsId + "/projects/" + projId + "/environments/" + envId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 8. Verify Environment is gone
        mockMvc.perform(get("/api/v1/workspaces/" + wsId + "/projects/" + projId + "/environments/" + envId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Hierarchy Mismatch IDOR Prevention: Environment queried under wrong Project is rejected with 404")
    void testHierarchicalIdorPrevention() throws Exception {
        // Register Owner
        String ownerEmail = "idor_owner_" + UUID.randomUUID() + "@example.com";
        RegisterRequest regReq = new RegisterRequest(ownerEmail, "Password123!Secure", "IDOR Owner", "SecurityCorp");
        MvcResult regRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated()).andReturn();
        String token = objectMapper.readTree(regRes.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        MvcResult wsRes = mockMvc.perform(get("/api/v1/workspaces").header("Authorization", "Bearer " + token)).andReturn();
        String wsId = objectMapper.readTree(wsRes.getResponse().getContentAsString()).path("data").get(0).path("id").asText();

        // Create Project 1
        CreateProjectRequest p1Req = new CreateProjectRequest("Project Alpha", "proj-alpha", "P1");
        MvcResult p1Res = mockMvc.perform(post("/api/v1/workspaces/" + wsId + "/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p1Req)))
                .andExpect(status().isCreated()).andReturn();
        String p1Id = objectMapper.readTree(p1Res.getResponse().getContentAsString()).path("data").path("id").asText();
        String env1Id = objectMapper.readTree(p1Res.getResponse().getContentAsString())
                .path("data").path("environments").get(0).path("id").asText();

        // Create Project 2
        CreateProjectRequest p2Req = new CreateProjectRequest("Project Beta", "proj-beta", "P2");
        MvcResult p2Res = mockMvc.perform(post("/api/v1/workspaces/" + wsId + "/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p2Req)))
                .andExpect(status().isCreated()).andReturn();
        String p2Id = objectMapper.readTree(p2Res.getResponse().getContentAsString()).path("data").path("id").asText();

        // Query Project 1's environment under Project 2's URL path -> MUST FAIL with 404
        mockMvc.perform(get("/api/v1/workspaces/" + wsId + "/projects/" + p2Id + "/environments/" + env1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("RBAC Environment Permissions: DEVELOPER cannot create custom environments")
    void testDeveloperCannotCreateCustomEnvironment() throws Exception {
        // Register Owner
        String ownerEmail = "rbac_env_owner_" + UUID.randomUUID() + "@example.com";
        RegisterRequest regReq = new RegisterRequest(ownerEmail, "Password123!Secure", "Env Owner", "RBACCorp");
        MvcResult regRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated()).andReturn();
        String ownerToken = objectMapper.readTree(regRes.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        MvcResult wsRes = mockMvc.perform(get("/api/v1/workspaces").header("Authorization", "Bearer " + ownerToken)).andReturn();
        String wsId = objectMapper.readTree(wsRes.getResponse().getContentAsString()).path("data").get(0).path("id").asText();

        // Create Project
        CreateProjectRequest pReq = new CreateProjectRequest("Data Pipeline", "data-pipeline", "ETL");
        MvcResult pRes = mockMvc.perform(post("/api/v1/workspaces/" + wsId + "/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pReq)))
                .andExpect(status().isCreated()).andReturn();
        String pId = objectMapper.readTree(pRes.getResponse().getContentAsString()).path("data").path("id").asText();

        // Register Developer
        String devEmail = "dev_env_" + UUID.randomUUID() + "@example.com";
        RegisterRequest devReg = new RegisterRequest(devEmail, "Password123!Secure", "Dev User", "Other Org");
        MvcResult devRegRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(devReg)))
                .andExpect(status().isCreated()).andReturn();
        String devToken = objectMapper.readTree(devRegRes.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        // Owner adds Developer to workspace
        AddMemberRequest addReq = new AddMemberRequest(devEmail, WorkspaceRole.DEVELOPER);
        mockMvc.perform(post("/api/v1/workspaces/" + wsId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isCreated());

        // Developer attempts to create custom environment -> MUST FAIL with 403 Forbidden
        CreateEnvironmentRequest createEnv = new CreateEnvironmentRequest(
                "Dev Sandbox", "dev-sandbox", EnvType.DEVELOPMENT, "Dev only", false
        );
        mockMvc.perform(post("/api/v1/workspaces/" + wsId + "/projects/" + pId + "/environments")
                        .header("Authorization", "Bearer " + devToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createEnv)))
                .andExpect(status().isForbidden());
    }
}
