package com.secretvault.access.privileged;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.privileged.dto.ApprovePrivilegedRequest;
import com.secretvault.access.privileged.dto.CreatePrivilegedAccessRequest;
import com.secretvault.access.privileged.model.ApprovalDecision;
import com.secretvault.access.privileged.model.PrivilegedAction;
import com.secretvault.access.privileged.model.PrivilegedPolicyScope;
import com.secretvault.access.privileged.model.PrivilegedRequestStatus;
import com.secretvault.auth.dto.AuthResponse;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.auth.service.AuthService;
import com.secretvault.workspace.dto.AddMemberRequest;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.service.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PrivilegedAccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private WorkspaceService workspaceService;

    private String ownerToken;
    private UUID ownerId;
    private UUID workspaceId;
    private String devToken;
    private UUID devId;
    private String adminToken;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        // Register Owner
        String ownerEmail = "pa_owner_" + UUID.randomUUID() + "@example.com";
        AuthResponse ownerAuth = authService.register(new RegisterRequest(ownerEmail, "Password123!Secure", "PA Owner", "PA Org"));
        ownerToken = ownerAuth.accessToken();
        ownerId = ownerAuth.user().id();
        workspaceId = ownerAuth.activeWorkspace().id();

        // Register Developer
        String devEmail = "pa_dev_" + UUID.randomUUID() + "@example.com";
        AuthResponse devAuth = authService.register(new RegisterRequest(devEmail, "Password123!Secure", "PA Dev", "Dev Org"));
        devToken = devAuth.accessToken();
        devId = devAuth.user().id();

        // Register Admin / Approver
        String adminEmail = "pa_admin_" + UUID.randomUUID() + "@example.com";
        AuthResponse adminAuth = authService.register(new RegisterRequest(adminEmail, "Password123!Secure", "PA Admin", "Admin Org"));
        adminToken = adminAuth.accessToken();
        adminId = adminAuth.user().id();

        // Add Developer and Admin to workspace
        workspaceService.addMember(workspaceId, new AddMemberRequest(devEmail, WorkspaceRole.DEVELOPER), ownerId);
        workspaceService.addMember(workspaceId, new AddMemberRequest(adminEmail, WorkspaceRole.ADMIN), ownerId);
    }

    @Test
    @DisplayName("POST /privileged-access/requests returns 201 Created with Cache-Control: no-store")
    void createRequestEndpoint() throws Exception {
        CreatePrivilegedAccessRequest body = new CreatePrivilegedAccessRequest(
                PrivilegedAction.SECRET_REVEAL,
                PrivilegedPolicyScope.WORKSPACE,
                null, null, null,
                "secret.reveal",
                30,
                "Investigating payment gateway outage in production environment",
                devId,
                null
        );

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/privileged-access/requests", workspaceId)
                        .header("Authorization", "Bearer " + devToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.requiredQuorum").value(1))
                .andExpect(jsonPath("$.data.currentApprovalsCount").value(0));
    }

    @Test
    @DisplayName("GET /privileged-access/requests lists workspace requests")
    void listRequestsEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/privileged-access/requests", workspaceId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("POST /privileged-access/requests/{id}/approve records approval decision")
    void approveEndpoint() throws Exception {
        // Submit request by dev
        CreatePrivilegedAccessRequest body = new CreatePrivilegedAccessRequest(
                PrivilegedAction.SECRET_REVEAL,
                PrivilegedPolicyScope.WORKSPACE,
                null, null, null,
                "secret.reveal",
                30,
                "Investigating payment gateway outage in production environment",
                devId,
                null
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/privileged-access/requests", workspaceId)
                        .header("Authorization", "Bearer " + devToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        String createJson = createResult.getResponse().getContentAsString();
        String requestId = objectMapper.readTree(createJson).path("data").path("id").asText();

        // Admin approves request
        ApprovePrivilegedRequest approveReq = new ApprovePrivilegedRequest(ApprovalDecision.APPROVED, "Approved by Security Admin", null);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/privileged-access/requests/{requestId}/approve", workspaceId, requestId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.currentApprovalsCount").value(1));
    }

    @Test
    @DisplayName("GET /privileged-access/policies returns workspace policies")
    void listPoliciesEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/privileged-access/policies", workspaceId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data").isArray());
    }
}
