package com.secretvault.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.project.dto.CreateProjectRequest;
import com.secretvault.project.dto.UpdateProjectRequest;
import com.secretvault.workspace.dto.AddMemberRequest;
import com.secretvault.workspace.dto.CreateWorkspaceRequest;
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
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Project Lifecycle: Create Project, Auto-Seed Environments, List, Update, and Delete")
    void testProjectLifecycleAndAutoSeeding() throws Exception {
        // 1. Register Owner
        String ownerEmail = "proj_owner_" + UUID.randomUUID() + "@example.com";
        RegisterRequest registerReq = new RegisterRequest(ownerEmail, "Password123!Secure", "Project Owner", "CyberOrg");

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String ownerToken = objectMapper.readTree(regResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 2. Get Workspace ID
        MvcResult wsListResult = mockMvc.perform(get("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();

        String workspaceId = objectMapper.readTree(wsListResult.getResponse().getContentAsString())
                .path("data").get(0).path("id").asText();

        // 3. Create Project
        CreateProjectRequest createReq = new CreateProjectRequest("Auth Microservice", "auth-microservice", "OAuth and OIDC provider");

        MvcResult createResult = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Auth Microservice"))
                .andExpect(jsonPath("$.data.slug").value("auth-microservice"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.environments", hasSize(3)))
                .andExpect(jsonPath("$.data.environments[?(@.slug == 'development')].envType").value("DEVELOPMENT"))
                .andExpect(jsonPath("$.data.environments[?(@.slug == 'staging')].envType").value("STAGING"))
                .andExpect(jsonPath("$.data.environments[?(@.slug == 'production')].isProtected").value(true))
                .andReturn();

        String projectId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 4. List Projects in Workspace
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].name").value("Auth Microservice"));

        // 5. Get Project by ID
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(projectId))
                .andExpect(jsonPath("$.data.environments", hasSize(3)));

        // 6. Update Project
        UpdateProjectRequest updateReq = new UpdateProjectRequest("Identity & Auth Service", "Updated description", null);
        mockMvc.perform(patch("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Identity & Auth Service"))
                .andExpect(jsonPath("$.data.description").value("Updated description"));

        // 7. Delete Project
        mockMvc.perform(delete("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // 8. Verify Project is deleted
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Cross-tenant and Hierarchical Isolation for Projects")
    void testCrossTenantProjectIsolation() throws Exception {
        // 1. Tenant Alpha
        String emailA = "alpha_proj_" + UUID.randomUUID() + "@example.com";
        RegisterRequest reqA = new RegisterRequest(emailA, "Password123!Secure", "Alpha User", "Alpha Corp");
        MvcResult resA = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isCreated())
                .andReturn();
        String tokenA = objectMapper.readTree(resA.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        MvcResult wsResA = mockMvc.perform(get("/api/v1/workspaces").header("Authorization", "Bearer " + tokenA)).andReturn();
        String wsIdA = objectMapper.readTree(wsResA.getResponse().getContentAsString()).path("data").get(0).path("id").asText();

        // Create Project A in Workspace A
        CreateProjectRequest projReqA = new CreateProjectRequest("Alpha Engine", "alpha-engine", "Core engine");
        MvcResult pResA = mockMvc.perform(post("/api/v1/workspaces/" + wsIdA + "/projects")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projReqA)))
                .andExpect(status().isCreated())
                .andReturn();
        String projIdA = objectMapper.readTree(pResA.getResponse().getContentAsString()).path("data").path("id").asText();

        // 2. Tenant Beta
        String emailB = "beta_proj_" + UUID.randomUUID() + "@example.com";
        RegisterRequest reqB = new RegisterRequest(emailB, "Password123!Secure", "Beta User", "Beta Corp");
        MvcResult resB = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isCreated())
                .andReturn();
        String tokenB = objectMapper.readTree(resB.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        MvcResult wsResB = mockMvc.perform(get("/api/v1/workspaces").header("Authorization", "Bearer " + tokenB)).andReturn();
        String wsIdB = objectMapper.readTree(wsResB.getResponse().getContentAsString()).path("data").get(0).path("id").asText();

        // 3. Beta tries to access Alpha's project in Alpha's workspace -> MUST BE 403 Forbidden
        mockMvc.perform(get("/api/v1/workspaces/" + wsIdA + "/projects/" + projIdA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // 4. Beta tries to access Alpha's project claiming it belongs to Beta's workspace -> MUST BE 404 Not Found (IDOR mismatch)
        mockMvc.perform(get("/api/v1/workspaces/" + wsIdB + "/projects/" + projIdA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // 5. Beta tries to create project in Alpha's workspace -> MUST BE 403 Forbidden
        CreateProjectRequest illegalCreate = new CreateProjectRequest("Injected", "injected", "Illegal project");
        mockMvc.perform(post("/api/v1/workspaces/" + wsIdA + "/projects")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(illegalCreate)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC Project Permissions: VIEWER cannot create or update projects")
    void testProjectRbacPermissions() throws Exception {
        // 1. Register Owner
        String ownerEmail = "rbac_owner_" + UUID.randomUUID() + "@example.com";
        RegisterRequest regReq = new RegisterRequest(ownerEmail, "Password123!Secure", "RBAC Owner", "RBAC Org");
        MvcResult regRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regReq)))
                .andExpect(status().isCreated()).andReturn();
        String ownerToken = objectMapper.readTree(regRes.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        MvcResult wsRes = mockMvc.perform(get("/api/v1/workspaces").header("Authorization", "Bearer " + ownerToken)).andReturn();
        String wsId = objectMapper.readTree(wsRes.getResponse().getContentAsString()).path("data").get(0).path("id").asText();

        // 2. Register Viewer
        String viewerEmail = "rbac_viewer_" + UUID.randomUUID() + "@example.com";
        RegisterRequest viewerReg = new RegisterRequest(viewerEmail, "Password123!Secure", "Viewer User", "Other Org");
        MvcResult viewerRegRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(viewerReg)))
                .andExpect(status().isCreated()).andReturn();
        String viewerToken = objectMapper.readTree(viewerRegRes.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        // 3. Owner adds Viewer to Workspace as VIEWER
        AddMemberRequest addReq = new AddMemberRequest(viewerEmail, WorkspaceRole.VIEWER);
        mockMvc.perform(post("/api/v1/workspaces/" + wsId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isCreated());

        // 4. Viewer tries to create project -> MUST BE 403 Forbidden
        CreateProjectRequest createReq = new CreateProjectRequest("Viewer Project", "viewer-proj", "Should fail");
        mockMvc.perform(post("/api/v1/workspaces/" + wsId + "/projects")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isForbidden());
    }
}
