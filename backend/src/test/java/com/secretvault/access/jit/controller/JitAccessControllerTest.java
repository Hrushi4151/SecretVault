package com.secretvault.access.jit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.jit.dto.ApproveJitRequest;
import com.secretvault.access.jit.dto.JitAccessRequestResponse;
import com.secretvault.access.jit.dto.RejectJitRequest;
import com.secretvault.access.jit.dto.SubmitJitRequest;
import com.secretvault.access.jit.service.JitAccessService;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.auth.dto.AuthResponse;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.auth.service.AuthService;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.repository.SecretRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JitAccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Autowired
    private SecretRepository secretRepository;

    @Autowired
    private JitAccessService jitAccessService;

    private String ownerToken;
    private UUID ownerId;
    private UUID workspaceId;
    private String developerToken;
    private UUID developerId;
    private String devEmail;
    private UUID projectId;
    private UUID environmentId;
    private UUID secretId;

    @BeforeEach
    void setUp() {
        // Register Owner
        String ownerEmail = "jit_owner_" + UUID.randomUUID() + "@example.com";
        AuthResponse ownerAuth = authService.register(new RegisterRequest(ownerEmail, "Password123!", "JIT Owner", "JIT Org"));
        ownerToken = ownerAuth.accessToken();
        ownerId = ownerAuth.user().id();
        workspaceId = ownerAuth.activeWorkspace().id();

        // Register Developer
        devEmail = "jit_dev_" + UUID.randomUUID() + "@example.com";
        AuthResponse devAuth = authService.register(new RegisterRequest(devEmail, "Password123!", "JIT Dev", "Dev Org"));
        developerToken = devAuth.accessToken();
        developerId = devAuth.user().id();

        // Add developer to owner's workspace
        workspaceService.addMember(workspaceId, new AddMemberRequest(devEmail, WorkspaceRole.DEVELOPER), ownerId);

        // Seed Project, Environment, Secret
        Project project = new Project(workspaceId, "Payment API", "payment-api-" + UUID.randomUUID(), "Core Payment", ownerId);
        project = projectRepository.save(project);
        projectId = project.getId();

        Environment env = new Environment(projectId, "Production", "prod", EnvType.PRODUCTION, "Prod Enclave", true, ownerId);
        env = environmentRepository.save(env);
        environmentId = env.getId();

        Secret secret = new Secret(environmentId, "STRIPE_SECRET_KEY", "Stripe Key", ownerId);
        secret = secretRepository.save(secret);
        secretId = secret.getId();
    }

    @Test
    @DisplayName("POST /access/jit/requests: Successfully submits JIT temporary access request")
    void testSubmitJitRequestEndpointSuccess() throws Exception {
        SubmitJitRequest req = new SubmitJitRequest(
                projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL, 30, "Investigating checkout failure"
        );

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/access/jit/requests", workspaceId)
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.permissionCode").value("secret.reveal"))
                .andExpect(jsonPath("$.data.durationMinutes").value(30))
                .andExpect(jsonPath("$.data.userEmail").value(devEmail));
    }

    @Test
    @DisplayName("POST /access/jit/requests/{id}/approve: Approves JIT request and grants temporary elevation")
    void testApproveJitRequestEndpointSuccess() throws Exception {
        SubmitJitRequest req = new SubmitJitRequest(
                projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL, 45, "Incident emergency investigation"
        );
        JitAccessRequestResponse created = jitAccessService.submitRequest(workspaceId, req, developerId);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/access/jit/requests/{requestId}/approve", workspaceId, created.id())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApproveJitRequest("Approved for incident"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty());
    }

    @Test
    @DisplayName("Anti-Self-Approval: Requester attempting to approve own JIT request is blocked with 403")
    void testAntiSelfApprovalBlocked() throws Exception {
        SubmitJitRequest req = new SubmitJitRequest(
                projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL, 30, "Owner self-request"
        );
        JitAccessRequestResponse created = jitAccessService.submitRequest(workspaceId, req, ownerId);

        // Owner tries to approve their own request
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/access/jit/requests/{requestId}/approve", workspaceId, created.id())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApproveJitRequest("Self approval"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /access/jit/requests/{id}/reject: Rejects pending JIT request")
    void testRejectJitRequestEndpointSuccess() throws Exception {
        SubmitJitRequest req = new SubmitJitRequest(
                projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL, 30, "Request to be rejected"
        );
        JitAccessRequestResponse created = jitAccessService.submitRequest(workspaceId, req, developerId);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/access/jit/requests/{requestId}/reject", workspaceId, created.id())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RejectJitRequest("Not justified"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.reviewerNotes").value("Not justified"));
    }

    @Test
    @DisplayName("POST /access/jit/requests/{id}/cancel: Requester can cancel their pending request")
    void testCancelJitRequestEndpointSuccess() throws Exception {
        SubmitJitRequest req = new SubmitJitRequest(
                projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL, 30, "Accidental request"
        );
        JitAccessRequestResponse created = jitAccessService.submitRequest(workspaceId, req, developerId);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/access/jit/requests/{requestId}/cancel", workspaceId, created.id())
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /access/jit/requests/{id}/revoke: Revokes active JIT temporary elevation")
    void testRevokeJitRequestEndpointSuccess() throws Exception {
        SubmitJitRequest req = new SubmitJitRequest(
                projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL, 30, "Grant to revoke"
        );
        JitAccessRequestResponse created = jitAccessService.submitRequest(workspaceId, req, developerId);
        jitAccessService.approveRequest(workspaceId, created.id(), new ApproveJitRequest("Approved"), ownerId);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/access/jit/requests/{requestId}/revoke", workspaceId, created.id())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /access/jit/active: Retrieves active non-expired JIT grants for caller")
    void testGetActiveGrantsEndpoint() throws Exception {
        SubmitJitRequest req = new SubmitJitRequest(
                projectId, environmentId, secretId, AccessPermission.SECRET_REVEAL, 30, "Active grant for dev"
        );
        JitAccessRequestResponse created = jitAccessService.submitRequest(workspaceId, req, developerId);
        jitAccessService.approveRequest(workspaceId, created.id(), new ApproveJitRequest("Approved"), ownerId);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/access/jit/active", workspaceId)
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].status").value("APPROVED"));
    }
}
