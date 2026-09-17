package com.secretvault.workspace.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.workspace.dto.AddMemberRequest;
import com.secretvault.auth.dto.RegisterRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Workspace creation, retrieval, and member addition workflow")
    void testWorkspaceLifecycleWorkflow() throws Exception {
        // 1. Register Owner
        String ownerEmail = "owner_" + UUID.randomUUID() + "@example.com";
        RegisterRequest registerReq = new RegisterRequest(ownerEmail, "Password123!Secure", "Org Owner", "Cloud Native Corp");

        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String ownerToken = objectMapper.readTree(regResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 2. List Workspaces for Owner (should have Default Workspace)
        mockMvc.perform(get("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].role").value("OWNER"));

        // 3. Create a new custom workspace
        CreateWorkspaceRequest createWsReq = new CreateWorkspaceRequest("Production Clusters", "prod-clusters");
        MvcResult createWsResult = mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createWsReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Production Clusters"))
                .andExpect(jsonPath("$.data.slug").value("prod-clusters"))
                .andExpect(jsonPath("$.data.role").value("OWNER"))
                .andReturn();

        String workspaceId = objectMapper.readTree(createWsResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 4. Register a second user to add as member
        String memberEmail = "developer_" + UUID.randomUUID() + "@example.com";
        RegisterRequest memberRegReq = new RegisterRequest(memberEmail, "Password123!Secure", "Dev Engineer", "Other Org");
        MvcResult memberRegResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(memberRegReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String memberToken = objectMapper.readTree(memberRegResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 5. Owner adds developer to the newly created workspace
        com.secretvault.workspace.dto.AddMemberRequest addMemberReq = new com.secretvault.workspace.dto.AddMemberRequest(
                memberEmail,
                WorkspaceRole.DEVELOPER
        );

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addMemberReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(memberEmail))
                .andExpect(jsonPath("$.data.role").value("DEVELOPER"));

        // 6. Developer can now fetch the workspace details
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(workspaceId))
                .andExpect(jsonPath("$.data.role").value("DEVELOPER"));

        // 7. List members should now include 2 members (Owner + Developer)
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/members")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }
}
