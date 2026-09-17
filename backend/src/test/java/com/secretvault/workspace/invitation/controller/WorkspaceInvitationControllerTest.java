package com.secretvault.workspace.invitation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.workspace.dto.CreateWorkspaceRequest;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.invitation.dto.AcceptInvitationRequest;
import com.secretvault.workspace.invitation.dto.CreateInvitationRequest;
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
class WorkspaceInvitationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Complete Invitation flow: Create, List Pending, Accept by new user, Replay rejection")
    void testInvitationLifecycle() throws Exception {
        // 1. Register Inviter (Owner)
        String ownerEmail = "owner_inv_" + UUID.randomUUID() + "@example.com";
        RegisterRequest ownerReg = new RegisterRequest(ownerEmail, "Password123!Secure", "Owner Inviter", "Acme Labs");
        MvcResult ownerRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ownerReg)))
                .andExpect(status().isCreated())
                .andReturn();
        String ownerToken = objectMapper.readTree(ownerRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 2. Create Workspace
        CreateWorkspaceRequest createWsReq = new CreateWorkspaceRequest("Security Workspace", "sec-ws-" + UUID.randomUUID().toString().substring(0, 8));
        MvcResult wsRes = mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createWsReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String workspaceId = objectMapper.readTree(wsRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 3. Create Invitation
        String inviteeEmail = "invitee_" + UUID.randomUUID() + "@example.com";
        CreateInvitationRequest inviteReq = new CreateInvitationRequest(inviteeEmail, WorkspaceRole.DEVELOPER, 7);
        MvcResult inviteRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inviteReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(inviteeEmail))
                .andExpect(jsonPath("$.data.role").value("DEVELOPER"))
                .andExpect(jsonPath("$.data.rawToken", startsWith("inv_")))
                .andReturn();

        String rawToken = objectMapper.readTree(inviteRes.getResponse().getContentAsString())
                .path("data").path("rawToken").asText();

        // 4. List Pending Invitations
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].email").value(inviteeEmail));

        // 5. Register Invitee as a new platform user
        RegisterRequest inviteeReg = new RegisterRequest(inviteeEmail, "Password123!Secure", "Invited User", "New Org");
        MvcResult inviteeRegRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inviteeReg)))
                .andExpect(status().isCreated())
                .andReturn();
        String inviteeToken = objectMapper.readTree(inviteeRegRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 6. Invitee accepts the invitation using rawToken
        AcceptInvitationRequest acceptReq = new AcceptInvitationRequest(rawToken);
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header("Authorization", "Bearer " + inviteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(acceptReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(workspaceId))
                .andExpect(jsonPath("$.data.role").value("DEVELOPER"));

        // 7. Token replay rejection: Second attempt to accept same token must fail
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .header("Authorization", "Bearer " + inviteeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(acceptReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
