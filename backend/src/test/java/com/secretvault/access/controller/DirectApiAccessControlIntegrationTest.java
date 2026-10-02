package com.secretvault.access.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.environment.dto.CreateEnvironmentRequest;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.project.access.dto.UpdateProjectAccessRequest;
import com.secretvault.project.dto.CreateProjectRequest;
import com.secretvault.project.dto.UpdateProjectRequest;
import com.secretvault.secret.dto.CreateSecretRequest;
import com.secretvault.secret.dto.UpdateSecretRequest;
import com.secretvault.workspace.dto.AddMemberRequest;
import com.secretvault.workspace.entity.WorkspaceRole;
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

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-End Direct REST API Authorization and Scope Enforcement Integration Tests.
 * Tests full Spring Boot MVC security pipeline, principal validation, role hierarchies,
 * IDOR isolation, and mass-assignment protection.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DirectApiAccessControlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String ownerToken;
    private String adminToken;
    private String devToken;
    private String viewerToken;
    private String attackerToken;

    private String devUserId;
    private String viewerUserId;

    private String workspaceId;
    private String projectId;
    private String devEnvId;
    private String prodEnvId;
    private String secretId;

    private String attackerWorkspaceId;

    @BeforeEach
    void setupInfrastructure() throws Exception {
        // 1. Register USER_A (OWNER)
        String ownerEmail = "owner_" + UUID.randomUUID() + "@corp.com";
        ownerToken = registerUser(ownerEmail, "Owner User", "Security Enterprise");

        // Get default workspace
        workspaceId = getFirstWorkspaceId(ownerToken);

        // 2. Register USER_B (ADMIN)
        String adminEmail = "admin_" + UUID.randomUUID() + "@corp.com";
        adminToken = registerUser(adminEmail, "Admin User", "Admin Enterprise");
        addMemberToWorkspace(ownerToken, workspaceId, adminEmail, WorkspaceRole.ADMIN);

        // 3. Register USER_C (DEVELOPER)
        String devEmail = "dev_" + UUID.randomUUID() + "@corp.com";
        devToken = registerUser(devEmail, "Dev User", "Dev Enterprise");
        devUserId = addMemberToWorkspace(ownerToken, workspaceId, devEmail, WorkspaceRole.DEVELOPER);

        // 4. Register USER_D (VIEWER)
        String viewerEmail = "viewer_" + UUID.randomUUID() + "@corp.com";
        viewerToken = registerUser(viewerEmail, "Viewer User", "Viewer Enterprise");
        viewerUserId = addMemberToWorkspace(ownerToken, workspaceId, viewerEmail, WorkspaceRole.VIEWER);

        // 5. Register ATTACKER in an isolated tenant
        String attackerEmail = "attacker_" + UUID.randomUUID() + "@evil.com";
        attackerToken = registerUser(attackerEmail, "Attacker User", "Evil Corp");
        attackerWorkspaceId = getFirstWorkspaceId(attackerToken);

        // 6. Provision Project with seeded environments
        CreateProjectRequest projReq = new CreateProjectRequest("Payment Service", "payment-service", "Payment processor");
        MvcResult projResult = mockMvc.perform(post("/api/v1/workspaces/{wId}/projects", workspaceId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(projReq)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode projJson = objectMapper.readTree(projResult.getResponse().getContentAsString()).path("data");
        projectId = projJson.path("id").asText();

        // Extract Development and Production environment IDs
        JsonNode envs = projJson.path("environments");
        for (JsonNode env : envs) {
            if ("DEVELOPMENT".equalsIgnoreCase(env.path("envType").asText()) || "development".equalsIgnoreCase(env.path("slug").asText())) {
                devEnvId = env.path("id").asText();
            } else if ("PRODUCTION".equalsIgnoreCase(env.path("envType").asText()) || "production".equalsIgnoreCase(env.path("slug").asText())) {
                prodEnvId = env.path("id").asText();
            }
        }

        // 7. Create a Secret in Development environment
        CreateSecretRequest secReq = new CreateSecretRequest(
                "STRIPE_API_KEY",
                "sk_test_123456789",
                "Stripe test credentials"
        );
        MvcResult secResult = mockMvc.perform(post("/api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets",
                        workspaceId, projectId, devEnvId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secReq)))
                .andExpect(status().isCreated())
                .andReturn();

        secretId = objectMapper.readTree(secResult.getResponse().getContentAsString())
                .path("data").path("id").asText();
    }

    private String registerUser(String email, String name, String org) throws Exception {
        RegisterRequest reg = new RegisterRequest(email, "Password123!Secure", name, org);
        MvcResult res = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    private String getFirstWorkspaceId(String token) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").get(0).path("id").asText();
    }

    private String addMemberToWorkspace(String token, String wsId, String email, WorkspaceRole role) throws Exception {
        AddMemberRequest req = new AddMemberRequest(email, role);
        MvcResult res = mockMvc.perform(post("/api/v1/workspaces/{wId}/members", wsId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("userId").asText();
    }

    @Test
    @DisplayName("Direct API: Project management is strictly allowed for OWNER/ADMIN and denied for DEVELOPER/VIEWER")
    void testDirectApiProjectManagementAuthorization() throws Exception {
        UpdateProjectRequest updateReq = new UpdateProjectRequest("Payment Service Renamed", "Updated description", null);

        // 1. OWNER can update project -> 200 OK
        mockMvc.perform(patch("/api/v1/workspaces/{wId}/projects/{pId}", workspaceId, projectId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Payment Service Renamed"));

        // 2. ADMIN can update project -> 200 OK
        mockMvc.perform(patch("/api/v1/workspaces/{wId}/projects/{pId}", workspaceId, projectId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        // 3. DEVELOPER cannot modify project settings -> 403 Forbidden
        mockMvc.perform(patch("/api/v1/workspaces/{wId}/projects/{pId}", workspaceId, projectId)
                        .header("Authorization", "Bearer " + devToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        // 4. VIEWER cannot modify project settings -> 403 Forbidden
        mockMvc.perform(patch("/api/v1/workspaces/{wId}/projects/{pId}", workspaceId, projectId)
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct API: Custom Environment provisioning restricted to OWNER/ADMIN")
    void testDirectApiEnvironmentManagementAuthorization() throws Exception {
        CreateEnvironmentRequest envReq = new CreateEnvironmentRequest("Sandbox", "sandbox", EnvType.DEVELOPMENT, "Isolated testing", false);

        // 1. OWNER creates environment -> 201 Created
        mockMvc.perform(post("/api/v1/workspaces/{wId}/projects/{pId}/environments", workspaceId, projectId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(envReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug").value("sandbox"));

        // 2. DEVELOPER cannot create custom environment -> 403 Forbidden
        CreateEnvironmentRequest devEnvReq = new CreateEnvironmentRequest("Dev Tier 2", "dev-tier-2", EnvType.DEVELOPMENT, "Dev tier", false);
        mockMvc.perform(post("/api/v1/workspaces/{wId}/projects/{pId}/environments", workspaceId, projectId)
                        .header("Authorization", "Bearer " + devToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(devEnvReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct API: Secret Metadata vs Secret Reveal authorization separation")
    void testDirectApiSecretRevealAuthorization() throws Exception {
        // 1. VIEWER can read secret metadata -> 200 OK
        mockMvc.perform(get("/api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{sId}",
                        workspaceId, projectId, devEnvId, secretId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("STRIPE_API_KEY"));

        // 2. VIEWER cannot reveal plaintext secret -> 403 Forbidden
        mockMvc.perform(post("/api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{sId}/reveal",
                        workspaceId, projectId, devEnvId, secretId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());

        // 3. DEVELOPER can reveal plaintext secret -> 200 OK
        mockMvc.perform(post("/api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{sId}/reveal",
                        workspaceId, projectId, devEnvId, secretId)
                        .header("Authorization", "Bearer " + devToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value("sk_test_123456789"));
    }

    @Test
    @DisplayName("Direct API: Scoped Project Restriction (DEVELOPER + Project READ -> Denies Secret Mutation)")
    void testDirectApiScopedProjectRestriction() throws Exception {
        // Restrict DEVELOPER to VIEWER (READ) role on Project 1
        mockMvc.perform(post("/api/v1/workspaces/{wId}/projects/{pId}/members", workspaceId, projectId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.secretvault.project.access.dto.GrantProjectAccessRequest(
                                UUID.fromString(devUserId), WorkspaceRole.VIEWER
                        ))))
                .andExpect(status().isCreated());

        // DEVELOPER now attempts to update secret in Project 1 -> 403 Forbidden!
        UpdateSecretRequest updateSecReq = new UpdateSecretRequest("Updated description", null, "sk_test_new_value", "Updated key");
        mockMvc.perform(patch("/api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{sId}",
                        workspaceId, projectId, devEnvId, secretId)
                        .header("Authorization", "Bearer " + devToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateSecReq)))
                .andExpect(status().isForbidden());

        // DEVELOPER attempts to reveal secret in Project 1 -> 403 Forbidden!
        mockMvc.perform(post("/api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{sId}/reveal",
                        workspaceId, projectId, devEnvId, secretId)
                        .header("Authorization", "Bearer " + devToken))
                .andExpect(status().isForbidden());

        // But DEVELOPER can still read secret metadata -> 200 OK
        mockMvc.perform(get("/api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{sId}",
                        workspaceId, projectId, devEnvId, secretId)
                        .header("Authorization", "Bearer " + devToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Direct API IDOR: Attacker cannot access, modify, or reveal resources across tenant boundaries")
    void testDirectApiIdorProtection() throws Exception {
        // 1. Attacker attempts to list secrets in Workspace 1 -> 403 Forbidden
        mockMvc.perform(get("/api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets",
                        workspaceId, projectId, devEnvId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isForbidden());

        // 2. Attacker attempts to reveal Secret in Workspace 1 -> 403 Forbidden
        mockMvc.perform(post("/api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{sId}/reveal",
                        workspaceId, projectId, devEnvId, secretId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isForbidden());

        // 3. Attacker attempts to delete Project in Workspace 1 -> 403 Forbidden
        mockMvc.perform(delete("/api/v1/workspaces/{wId}/projects/{pId}", workspaceId, projectId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Direct API Invariant: Feature Branching strictly forbidden in Production environment")
    void testDirectApiBranchingInProductionBlocked() throws Exception {
        // Create secret in Production environment
        CreateSecretRequest prodSecReq = new CreateSecretRequest(
                "PROD_DATABASE_URL",
                "postgres://prod-db.internal:5432/secrets",
                "Prod DB"
        );
        MvcResult prodSecRes = mockMvc.perform(post("/api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets",
                        workspaceId, projectId, prodEnvId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodSecReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String prodSecId = objectMapper.readTree(prodSecRes.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Attempt branching on Production secret -> 400 Bad Request with BRANCHES_NOT_ALLOWED_FOR_ENVIRONMENT
        com.secretvault.secret.dto.CreateBranchRequest branchReq =
                new com.secretvault.secret.dto.CreateBranchRequest("feature-hotfix", 1, "Hotfix attempt in production");
        mockMvc.perform(post("/api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{sId}/branches",
                        workspaceId, projectId, prodEnvId, prodSecId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(branchReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BRANCHES_NOT_ALLOWED_FOR_ENVIRONMENT"));
    }
}
