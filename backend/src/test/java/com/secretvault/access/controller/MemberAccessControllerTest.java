package com.secretvault.access.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.dto.UpdateMemberAccessRequest;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.environment.access.entity.PermissionLevel;
import com.secretvault.project.dto.CreateProjectRequest;
import com.secretvault.workspace.dto.AddMemberRequest;
import com.secretvault.workspace.dto.CreateWorkspaceRequest;
import com.secretvault.workspace.entity.WorkspaceRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberAccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("End-to-End Member Project -> Environment Access Configuration & Effective Permission Inspection")
    void testMemberProjectEnvironmentAccessLifecycle() throws Exception {
        // 1. Register Owner
        String ownerEmail = "owner_" + UUID.randomUUID() + "@acme.com";
        RegisterRequest ownerReg = new RegisterRequest(ownerEmail, "Password123!Secure", "Acme Admin", "Acme Corp");
        MvcResult ownerRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ownerReg)))
                .andExpect(status().isCreated())
                .andReturn();
        String ownerToken = objectMapper.readTree(ownerRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 2. Create Workspace
        CreateWorkspaceRequest wsReq = new CreateWorkspaceRequest("Acme Core", "acme-core-" + UUID.randomUUID().toString().substring(0, 8));
        MvcResult wsRes = mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wsReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String workspaceId = objectMapper.readTree(wsRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 3. Register Member (Rahul - Developer)
        String rahulEmail = "rahul_" + UUID.randomUUID() + "@acme.com";
        RegisterRequest rahulReg = new RegisterRequest(rahulEmail, "Password123!Secure", "Rahul Sharma", "Acme Corp");
        MvcResult rahulRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rahulReg)))
                .andExpect(status().isCreated())
                .andReturn();
        String rahulToken = objectMapper.readTree(rahulRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
        String rahulUserId = objectMapper.readTree(rahulRes.getResponse().getContentAsString())
                .path("data").path("user").path("id").asText();

        // 4. Enroll Rahul in Workspace with DEVELOPER standing role
        AddMemberRequest addMemReq = new AddMemberRequest(rahulEmail, WorkspaceRole.DEVELOPER);
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addMemReq)))
                .andExpect(status().isCreated());

        // 5. Create 3 Projects: E-Commerce, Payment API, Admin Portal (each auto-seeds Development, Staging, Production)
        CreateProjectRequest proj1Req = new CreateProjectRequest("E-Commerce", "e-comm-" + UUID.randomUUID().toString().substring(0, 6), "E-Commerce App");
        MvcResult proj1Res = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proj1Req)))
                .andExpect(status().isCreated())
                .andReturn();
        String proj1Id = objectMapper.readTree(proj1Res.getResponse().getContentAsString()).path("data").path("id").asText();

        CreateProjectRequest proj2Req = new CreateProjectRequest("Payment API", "payment-" + UUID.randomUUID().toString().substring(0, 6), "Payment Gateway");
        MvcResult proj2Res = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proj2Req)))
                .andExpect(status().isCreated())
                .andReturn();
        String proj2Id = objectMapper.readTree(proj2Res.getResponse().getContentAsString()).path("data").path("id").asText();

        CreateProjectRequest proj3Req = new CreateProjectRequest("Admin Portal", "admin-portal-" + UUID.randomUUID().toString().substring(0, 6), "Admin Dashboard");
        MvcResult proj3Res = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proj3Req)))
                .andExpect(status().isCreated())
                .andReturn();
        String proj3Id = objectMapper.readTree(proj3Res.getResponse().getContentAsString()).path("data").path("id").asText();

        // 6. Inspect Rahul's Initial Member Access Matrix
        MvcResult initialMatrixRes = mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/members/" + rahulUserId + "/access")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member.fullName").value("Rahul Sharma"))
                .andExpect(jsonPath("$.data.member.workspaceRole").value("DEVELOPER"))
                .andExpect(jsonPath("$.data.projects", hasSize(3)))
                .andReturn();

        // Extract environment IDs from projects matching their projectIds
        var projectsNode = objectMapper.readTree(initialMatrixRes.getResponse().getContentAsString()).path("data").path("projects");
        com.fasterxml.jackson.databind.JsonNode proj1Node = null;
        com.fasterxml.jackson.databind.JsonNode proj2Node = null;
        com.fasterxml.jackson.databind.JsonNode proj3Node = null;
        for (com.fasterxml.jackson.databind.JsonNode pNode : projectsNode) {
            String pId = pNode.path("projectId").asText();
            if (pId.equals(proj1Id)) proj1Node = pNode;
            else if (pId.equals(proj2Id)) proj2Node = pNode;
            else if (pId.equals(proj3Id)) proj3Node = pNode;
        }
        assertNotNull(proj1Node);
        assertNotNull(proj2Node);
        assertNotNull(proj3Node);

        String env1DevId = proj1Node.path("environments").get(0).path("environmentId").asText();
        String env1StagingId = proj1Node.path("environments").get(1).path("environmentId").asText();
        String env1ProdId = proj1Node.path("environments").get(2).path("environmentId").asText();

        String env2DevId = proj2Node.path("environments").get(0).path("environmentId").asText();
        String env2StagingId = proj2Node.path("environments").get(1).path("environmentId").asText();
        String env2ProdId = proj2Node.path("environments").get(2).path("environmentId").asText();

        String env3DevId = proj3Node.path("environments").get(0).path("environmentId").asText();
        String env3ProdId = proj3Node.path("environments").get(2).path("environmentId").asText();

        // 7. Configure Multi-Project Access Matrix:
        //    E-Commerce: Project READ (VIEWER), Dev READ, Staging READ, Prod null
        //    Payment API: Project WRITE (DEVELOPER), Dev WRITE, Staging WRITE, Prod READ
        //    Admin Portal: Project READ (VIEWER), Dev READ, Prod READ
        UpdateMemberAccessRequest updateReq = new UpdateMemberAccessRequest(List.of(
                new UpdateMemberAccessRequest.ProjectAccessConfig(
                        UUID.fromString(proj1Id),
                        WorkspaceRole.VIEWER,
                        List.of(
                                new UpdateMemberAccessRequest.EnvironmentAccessConfig(UUID.fromString(env1DevId), PermissionLevel.READ),
                                new UpdateMemberAccessRequest.EnvironmentAccessConfig(UUID.fromString(env1StagingId), PermissionLevel.READ)
                        )
                ),
                new UpdateMemberAccessRequest.ProjectAccessConfig(
                        UUID.fromString(proj2Id),
                        WorkspaceRole.DEVELOPER,
                        List.of(
                                new UpdateMemberAccessRequest.EnvironmentAccessConfig(UUID.fromString(env2DevId), PermissionLevel.WRITE),
                                new UpdateMemberAccessRequest.EnvironmentAccessConfig(UUID.fromString(env2StagingId), PermissionLevel.WRITE),
                                new UpdateMemberAccessRequest.EnvironmentAccessConfig(UUID.fromString(env2ProdId), PermissionLevel.READ)
                        )
                ),
                new UpdateMemberAccessRequest.ProjectAccessConfig(
                        UUID.fromString(proj3Id),
                        WorkspaceRole.VIEWER,
                        List.of(
                                new UpdateMemberAccessRequest.EnvironmentAccessConfig(UUID.fromString(env3DevId), PermissionLevel.READ),
                                new UpdateMemberAccessRequest.EnvironmentAccessConfig(UUID.fromString(env3ProdId), PermissionLevel.READ)
                        )
                )
        ));

        mockMvc.perform(put("/api/v1/workspaces/" + workspaceId + "/members/" + rahulUserId + "/access")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects", hasSize(3)));

        // 8. Verify Persisted Access Matrix via GET
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/members/" + rahulUserId + "/access")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member.workspaceRole").value("DEVELOPER"));

        // 9. Inspect Effective Permissions and Lineage for Rahul on Payment API Development Environment
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/access/effective")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("userId", rahulUserId)
                        .param("projectId", proj2Id)
                        .param("environmentId", env2DevId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.permissionCode == 'secret.read')].granted").value(true))
                .andExpect(jsonPath("$.data[?(@.permissionCode == 'secret.reveal')].granted").value(true))
                .andExpect(jsonPath("$.data[?(@.permissionCode == 'secret.create')].granted").value(true));

        // 10. Inspect Effective Permissions for Rahul on E-Commerce (VIEWER project role)
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/access/effective")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("userId", rahulUserId)
                        .param("projectId", proj1Id)
                        .param("environmentId", env1DevId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.permissionCode == 'secret.read')].granted").value(true))
                .andExpect(jsonPath("$.data[?(@.permissionCode == 'secret.reveal')].granted").value(false))
                .andExpect(jsonPath("$.data[?(@.permissionCode == 'secret.create')].granted").value(false));

        // 11. Security Test: Unauthorized Rahul cannot configure access for other members
        mockMvc.perform(put("/api/v1/workspaces/" + workspaceId + "/members/" + rahulUserId + "/access")
                        .header("Authorization", "Bearer " + rahulToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());
    }
}
