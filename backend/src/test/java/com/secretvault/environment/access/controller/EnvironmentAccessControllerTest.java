package com.secretvault.environment.access.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.environment.access.dto.GrantEnvironmentAccessRequest;
import com.secretvault.environment.access.dto.UpdateEnvironmentAccessRequest;
import com.secretvault.environment.access.entity.PermissionLevel;
import com.secretvault.project.dto.CreateProjectRequest;
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
class EnvironmentAccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Environment Access Scoping: Grant, List, Update, and Revoke")
    void testEnvironmentAccessWorkflow() throws Exception {
        // 1. Register Owner
        String ownerEmail = "env_owner_" + UUID.randomUUID() + "@example.com";
        RegisterRequest ownerReg = new RegisterRequest(ownerEmail, "Password123!Secure", "Env Owner", "Infra Corp");
        MvcResult ownerRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ownerReg)))
                .andExpect(status().isCreated())
                .andReturn();
        String ownerToken = objectMapper.readTree(ownerRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 2. Create Workspace
        CreateWorkspaceRequest wsReq = new CreateWorkspaceRequest("Infra Workspace", "infra-ws-" + UUID.randomUUID().toString().substring(0, 8));
        MvcResult wsRes = mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wsReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String workspaceId = objectMapper.readTree(wsRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 3. Create Project (automatically creates development, staging, production environments)
        CreateProjectRequest projReq = new CreateProjectRequest("Gateway Service", "gw-service", "API gateway");
        MvcResult projRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId = objectMapper.readTree(projRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Fetch environments to get production env id
        MvcResult envListRes = mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String envId = objectMapper.readTree(envListRes.getResponse().getContentAsString())
                .path("data").get(0).path("id").asText();

        // 4. Register Member and add to Workspace
        String memberEmail = "env_dev_" + UUID.randomUUID() + "@example.com";
        RegisterRequest memReg = new RegisterRequest(memberEmail, "Password123!Secure", "Dev User", "Infra Corp");
        MvcResult memRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(memReg)))
                .andExpect(status().isCreated())
                .andReturn();
        String memberUserId = objectMapper.readTree(memRes.getResponse().getContentAsString())
                .path("data").path("user").path("id").asText();
        String memberToken = objectMapper.readTree(memRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        AddMemberRequest addMemReq = new AddMemberRequest(memberEmail, WorkspaceRole.DEVELOPER);
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addMemReq)))
                .andExpect(status().isCreated());

        // 5. Grant Scoped Environment Access
        GrantEnvironmentAccessRequest grantReq = new GrantEnvironmentAccessRequest(
                UUID.fromString(memberUserId), PermissionLevel.WRITE);
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + envId + "/access")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grantReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(memberUserId))
                .andExpect(jsonPath("$.data.permissionLevel").value("WRITE"))
                .andExpect(jsonPath("$.data.effectivePermission").value("WRITE"));

        // 6. List Environment Access Grants
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + envId + "/access")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].email").value(memberEmail));

        // 7. Update Environment Access to READ
        UpdateEnvironmentAccessRequest updateReq = new UpdateEnvironmentAccessRequest(PermissionLevel.READ);
        mockMvc.perform(patch("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + envId + "/access/" + memberUserId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissionLevel").value("READ"))
                .andExpect(jsonPath("$.data.effectivePermission").value("READ"));

        // 8. Revoke Environment Access
        mockMvc.perform(delete("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/environments/" + envId + "/access/" + memberUserId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }
}
