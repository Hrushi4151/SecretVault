package com.secretvault.project.access.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.project.access.dto.GrantProjectAccessRequest;
import com.secretvault.project.access.dto.UpdateProjectAccessRequest;
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
class ProjectAccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Project Access Scoping: Grant, List, Update, and Revoke")
    void testProjectAccessWorkflow() throws Exception {
        // 1. Register Owner
        String ownerEmail = "proj_owner_" + UUID.randomUUID() + "@example.com";
        RegisterRequest ownerReg = new RegisterRequest(ownerEmail, "Password123!Secure", "Project Owner", "Global FinTech");
        MvcResult ownerRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ownerReg)))
                .andExpect(status().isCreated())
                .andReturn();
        String ownerToken = objectMapper.readTree(ownerRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 2. Create Workspace
        CreateWorkspaceRequest wsReq = new CreateWorkspaceRequest("FinTech Hub", "fintech-hub-" + UUID.randomUUID().toString().substring(0, 8));
        MvcResult wsRes = mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wsReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String workspaceId = objectMapper.readTree(wsRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 3. Create Project
        CreateProjectRequest projReq = new CreateProjectRequest("Ledger Core", "ledger-core", "Core banking ledger");
        MvcResult projRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId = objectMapper.readTree(projRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 4. Register Member and add to Workspace
        String memberEmail = "member_dev_" + UUID.randomUUID() + "@example.com";
        RegisterRequest memReg = new RegisterRequest(memberEmail, "Password123!Secure", "Fin Developer", "Global FinTech");
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

        // 5. Grant Scoped Project Access
        GrantProjectAccessRequest grantReq = new GrantProjectAccessRequest(UUID.fromString(memberUserId), WorkspaceRole.DEVELOPER);
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grantReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(memberUserId))
                .andExpect(jsonPath("$.data.projectRole").value("DEVELOPER"))
                .andExpect(jsonPath("$.data.effectiveRole").value("DEVELOPER"));

        // 6. List Project Members
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].email").value(memberEmail));

        // 7. Update Project Access Role to VIEWER
        UpdateProjectAccessRequest updateReq = new UpdateProjectAccessRequest(WorkspaceRole.VIEWER);
        mockMvc.perform(patch("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/members/" + memberUserId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectRole").value("VIEWER"))
                .andExpect(jsonPath("$.data.effectiveRole").value("VIEWER"));

        // 8. Revoke Project Access
        mockMvc.perform(delete("/api/v1/workspaces/" + workspaceId + "/projects/" + projectId + "/members/" + memberUserId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }
}
