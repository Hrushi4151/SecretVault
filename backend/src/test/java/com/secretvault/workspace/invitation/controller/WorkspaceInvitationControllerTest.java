package com.secretvault.workspace.invitation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.workspace.dto.CreateWorkspaceRequest;
import com.secretvault.workspace.dto.UpdateMemberRoleRequest;
import com.secretvault.workspace.entity.WorkspaceRole;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkspaceInvitationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Complete End-to-End In-App Invitation Lifecycle: Lookup -> Invite -> In-App Delivery -> Security Check -> In-App Accept -> Role Change -> Removal")
    void testInAppInvitationAndRbacLifecycle() throws Exception {
        // 1. Register Inviter (User A - Owner)
        String ownerEmail = "owner_" + UUID.randomUUID() + "@example.com";
        RegisterRequest ownerReg = new RegisterRequest(ownerEmail, "Password123!Secure", "Owner User A", "Acme Labs");
        MvcResult ownerRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ownerReg)))
                .andExpect(status().isCreated())
                .andReturn();
        String ownerToken = objectMapper.readTree(ownerRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // 2. Create Workspace
        CreateWorkspaceRequest createWsReq = new CreateWorkspaceRequest("Production Vault", "prod-vault-" + UUID.randomUUID().toString().substring(0, 8));
        MvcResult wsRes = mockMvc.perform(post("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createWsReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String workspaceId = objectMapper.readTree(wsRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 3. Register Invitee (User B - Developer) BEFORE invitation
        String inviteeEmail = "developer_b_" + UUID.randomUUID() + "@example.com";
        RegisterRequest inviteeReg = new RegisterRequest(inviteeEmail, "Password123!Secure", "Developer User B", "Beta Org");
        MvcResult inviteeRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inviteeReg)))
                .andExpect(status().isCreated())
                .andReturn();
        String inviteeToken = objectMapper.readTree(inviteeRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
        String inviteeUserId = objectMapper.readTree(inviteeRes.getResponse().getContentAsString())
                .path("data").path("user").path("id").asText();

        // 4. User A performs debounced lookup on User B's email
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/invitations/lookup")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("email", inviteeEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.user.name").value("Developer User B"))
                .andExpect(jsonPath("$.data.user.email").value(inviteeEmail))
                .andExpect(jsonPath("$.data.isMember").value(false))
                .andExpect(jsonPath("$.data.hasPendingInvitation").value(false));

        // 5. User A creates Invitation for User B with DEVELOPER role
        CreateInvitationRequest inviteReq = new CreateInvitationRequest(inviteeEmail, WorkspaceRole.DEVELOPER, 7);
        MvcResult createInvRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inviteReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value(inviteeEmail))
                .andExpect(jsonPath("$.data.role").value("DEVELOPER"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        String invitationId = objectMapper.readTree(createInvRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 6. User A lookups User B again -> now shows hasPendingInvitation: true
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/invitations/lookup")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("email", inviteeEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasPendingInvitation").value(true));

        // 7. Duplicate Invitation attempt is rejected with 409 Conflict
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inviteReq)))
                .andExpect(status().isConflict());

        // 8. User B checks In-App Pending Invitations (`GET /api/v1/invitations/me`)
        mockMvc.perform(get("/api/v1/invitations/me")
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.items[0].id").value(invitationId))
                .andExpect(jsonPath("$.data.items[0].workspaceId").value(workspaceId))
                .andExpect(jsonPath("$.data.items[0].workspaceName").value("Production Vault"))
                .andExpect(jsonPath("$.data.items[0].role").value("DEVELOPER"))
                .andExpect(jsonPath("$.data.items[0].invitedBy.name").value("Owner User A"));

        // 9. Register Malicious Attacker (User C) to test Target Identity Enforcement
        String attackerEmail = "attacker_" + UUID.randomUUID() + "@example.com";
        RegisterRequest attackerReg = new RegisterRequest(attackerEmail, "Password123!Secure", "Attacker C", "Evil Corp");
        MvcResult attackerRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attackerReg)))
                .andExpect(status().isCreated())
                .andReturn();
        String attackerToken = objectMapper.readTree(attackerRes.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // Attacker C attempts to accept User B's invitation -> 403 Forbidden!
        mockMvc.perform(post("/api/v1/invitations/" + invitationId + "/accept")
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isForbidden());

        // 10. Legitimate User B accepts the invitation in-app directly
        mockMvc.perform(post("/api/v1/invitations/" + invitationId + "/accept")
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(workspaceId))
                .andExpect(jsonPath("$.data.role").value("DEVELOPER"));

        // 11. Replay attempt: User B tries to accept again -> 400 Bad Request
        mockMvc.perform(post("/api/v1/invitations/" + invitationId + "/accept")
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isBadRequest());

        // 12. User B's pending invitations list is now empty
        mockMvc.perform(get("/api/v1/invitations/me")
                        .header("Authorization", "Bearer " + inviteeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));

        // 13. User A lists workspace members -> User B is present with DEVELOPER role
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/members")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[?(@.userId == '" + inviteeUserId + "')].role").value("DEVELOPER"));

        // 14. User A lookups User B -> shows isMember: true
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/invitations/lookup")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("email", inviteeEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isMember").value(true));

        // 15. User A updates User B's role to VIEWER
        UpdateMemberRoleRequest roleUpdate = new UpdateMemberRoleRequest(WorkspaceRole.VIEWER);
        mockMvc.perform(patch("/api/v1/workspaces/" + workspaceId + "/members/" + inviteeUserId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roleUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("VIEWER"));

        // 16. User A removes User B from workspace
        mockMvc.perform(delete("/api/v1/workspaces/" + workspaceId + "/members/" + inviteeUserId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // 17. User B is no longer a member
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/members")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }
}
