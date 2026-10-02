package com.secretvault.project.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.grant.dto.CreateAccessGrantRequest;
import com.secretvault.access.jit.dto.ApproveJitRequest;
import com.secretvault.access.jit.dto.SubmitJitRequest;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.environment.access.dto.GrantEnvironmentAccessRequest;
import com.secretvault.environment.access.entity.PermissionLevel;
import com.secretvault.project.access.dto.GrantProjectAccessRequest;
import com.secretvault.project.dto.CreateProjectRequest;
import com.secretvault.workspace.dto.AddMemberRequest;
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

import java.time.Duration;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class UserProjectDiscoveryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("End-to-End User Workspace & Project Discovery with IDOR Protection")
    void testUserWorkspaceAndProjectDiscoveryFlow() throws Exception {
        // 1. Register Workspace Owner
        String ownerEmail = "owner_discovery_" + UUID.randomUUID() + "@example.com";
        RegisterRequest ownerReg = new RegisterRequest(ownerEmail, "Password123!Secure", "Acme Owner", "Acme Corp");
        MvcResult ownerRegResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ownerReg)))
                .andExpect(status().isCreated())
                .andReturn();

        String ownerToken = objectMapper.readTree(ownerRegResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        // Get default workspace
        MvcResult wsListResult = mockMvc.perform(get("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String workspaceId = objectMapper.readTree(wsListResult.getResponse().getContentAsString())
                .path("data").get(0).path("id").asText();

        // 2. Register Rahul (Developer)
        String rahulEmail = "rahul_" + UUID.randomUUID() + "@example.com";
        RegisterRequest rahulReg = new RegisterRequest(rahulEmail, "Password123!Secure", "Rahul Dev", "Rahul Independent");
        MvcResult rahulRegResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rahulReg)))
                .andExpect(status().isCreated())
                .andReturn();

        String rahulToken = objectMapper.readTree(rahulRegResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
        String rahulUserId = objectMapper.readTree(rahulRegResult.getResponse().getContentAsString())
                .path("data").path("user").path("id").asText();

        // 3. Add Rahul to Acme Workspace as DEVELOPER
        AddMemberRequest addMemberReq = new AddMemberRequest(rahulEmail, WorkspaceRole.DEVELOPER);
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addMemberReq)))
                .andExpect(status().isCreated());

        // 4. Verify Rahul can discover Acme Workspace in My Workspaces
        mockMvc.perform(get("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + rahulToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(workspaceId)));

        // 5. Owner creates 3 Projects: E-Commerce, Payment API, Admin Portal
        CreateProjectRequest ecommerceReq = new CreateProjectRequest("E-Commerce", "e-commerce", "Online store");
        MvcResult ecomResult = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ecommerceReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String ecommerceId = objectMapper.readTree(ecomResult.getResponse().getContentAsString()).path("data").path("id").asText();

        CreateProjectRequest paymentReq = new CreateProjectRequest("Payment API", "payment-api", "Processing engine");
        MvcResult payResult = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String paymentId = objectMapper.readTree(payResult.getResponse().getContentAsString()).path("data").path("id").asText();

        CreateProjectRequest adminPortalReq = new CreateProjectRequest("Admin Portal", "admin-portal", "Internal tools");
        MvcResult adminResult = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminPortalReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String adminPortalId = objectMapper.readTree(adminResult.getResponse().getContentAsString()).path("data").path("id").asText();

        // 6. Configure Rahul's Scoped Access:
        //    E-Commerce  -> VIEWER (READ)
        //    Payment API -> DEVELOPER (WRITE)
        //    Admin Portal -> NO ACCESS (Unassigned)
        GrantProjectAccessRequest grantEcom = new GrantProjectAccessRequest(UUID.fromString(rahulUserId), WorkspaceRole.VIEWER);
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects/" + ecommerceId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grantEcom)))
                .andExpect(status().isCreated());

        GrantProjectAccessRequest grantPay = new GrantProjectAccessRequest(UUID.fromString(rahulUserId), WorkspaceRole.DEVELOPER);
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects/" + paymentId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grantPay)))
                .andExpect(status().isCreated());

        // 7. Test Rahul's Project Discovery:
        //    Rahul MUST see E-Commerce and Payment API.
        //    Rahul MUST NOT see Admin Portal.
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + rahulToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].id", hasItems(ecommerceId, paymentId)))
                .andExpect(jsonPath("$.data[*].id", not(hasItem(adminPortalId))))
                .andExpect(jsonPath("$.data[*].name", hasItems("E-Commerce", "Payment API")))
                .andExpect(jsonPath("$.data[*].name", not(hasItem("Admin Portal"))));

        // 8. Test IDOR Protection on Project Level:
        //    Rahul attempting direct GET /workspaces/{id}/projects/{adminPortalId} MUST be rejected with 403 FORBIDDEN
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects/" + adminPortalId)
                        .header("Authorization", "Bearer " + rahulToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 9. Test IDOR Protection on Environment Level:
        //    Rahul attempting direct GET /workspaces/{id}/projects/{adminPortalId}/environments MUST be rejected with 403 FORBIDDEN
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects/" + adminPortalId + "/environments")
                        .header("Authorization", "Bearer " + rahulToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 10. Test Owner sees all 3 projects
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[*].id", hasItems(ecommerceId, paymentId, adminPortalId)));

        // 11. Test Granular JIT Elevation Dynamic Discovery:
        //     Rahul submits JIT request for Admin Portal development environment, Owner approves it.
        //     Admin Portal MUST dynamically become visible in Rahul's project list.
        MvcResult adminEnvsResult = mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects/" + adminPortalId + "/environments")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String adminDevEnvId = objectMapper.readTree(adminEnvsResult.getResponse().getContentAsString())
                .path("data").get(0).path("id").asText();

        SubmitJitRequest jitReq = new SubmitJitRequest(
                UUID.fromString(adminPortalId),
                UUID.fromString(adminDevEnvId),
                null,
                AccessPermission.SECRET_READ,
                120,
                "Emergency access for outage debugging"
        );
        MvcResult jitSubmitResult = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/jit/requests")
                        .header("Authorization", "Bearer " + rahulToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(jitReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String jitRequestId = objectMapper.readTree(jitSubmitResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        ApproveJitRequest approveReq = new ApproveJitRequest("Approved for incident triage");
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/jit/requests/" + jitRequestId + "/approve")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk());

        // Now Rahul's Project List MUST include Admin Portal via active JIT grant!
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + rahulToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[*].id", hasItem(adminPortalId)));

        // Direct GET for Admin Portal by Rahul now succeeds!
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects/" + adminPortalId)
                        .header("Authorization", "Bearer " + rahulToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(adminPortalId));
    }

    @Test
    @DisplayName("Standing Unrestricted Member with no scoped assignments discovers all workspace projects")
    void testStandingUnrestrictedMemberDiscoversAllProjects() throws Exception {
        // Register Owner
        String ownerEmail = "owner_standing_" + UUID.randomUUID() + "@example.com";
        RegisterRequest ownerReg = new RegisterRequest(ownerEmail, "Password123!Secure", "Owner Standing", "Standing Org");
        MvcResult ownerRegResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ownerReg)))
                .andExpect(status().isCreated())
                .andReturn();
        String ownerToken = objectMapper.readTree(ownerRegResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        MvcResult wsListResult = mockMvc.perform(get("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String workspaceId = objectMapper.readTree(wsListResult.getResponse().getContentAsString())
                .path("data").get(0).path("id").asText();

        // Create 2 Projects
        CreateProjectRequest p1Req = new CreateProjectRequest("Core API", "core-api", "Core backend");
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p1Req)))
                .andExpect(status().isCreated());

        CreateProjectRequest p2Req = new CreateProjectRequest("Analytics", "analytics", "Data pipeline");
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p2Req)))
                .andExpect(status().isCreated());

        // Register Bob and add as DEVELOPER with NO scoped assignments
        String bobEmail = "bob_" + UUID.randomUUID() + "@example.com";
        RegisterRequest bobReg = new RegisterRequest(bobEmail, "Password123!Secure", "Bob Standing", "Bob Org");
        MvcResult bobRegResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bobReg)))
                .andExpect(status().isCreated())
                .andReturn();
        String bobToken = objectMapper.readTree(bobRegResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        AddMemberRequest addMemberReq = new AddMemberRequest(bobEmail, WorkspaceRole.DEVELOPER);
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addMemberReq)))
                .andExpect(status().isCreated());

        // Bob should see all 2 projects by default standing role
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].name", hasItems("Core API", "Analytics")));
    }

    @Test
    @DisplayName("Granular AccessGrant dynamically enables project discovery and eliminates IDOR denial")
    void testGranularAccessGrantEnablesProjectDiscovery() throws Exception {
        // Register Owner
        String ownerEmail = "owner_grant_" + UUID.randomUUID() + "@example.com";
        RegisterRequest ownerReg = new RegisterRequest(ownerEmail, "Password123!Secure", "Grant Owner", "Grant Org");
        MvcResult ownerRegResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ownerReg)))
                .andExpect(status().isCreated())
                .andReturn();
        String ownerToken = objectMapper.readTree(ownerRegResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        MvcResult wsListResult = mockMvc.perform(get("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String workspaceId = objectMapper.readTree(wsListResult.getResponse().getContentAsString())
                .path("data").get(0).path("id").asText();

        // Create Project Alpha and Project Beta
        CreateProjectRequest alphaReq = new CreateProjectRequest("Project Alpha", "project-alpha", "Alpha service");
        MvcResult alphaRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alphaReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String alphaId = objectMapper.readTree(alphaRes.getResponse().getContentAsString()).path("data").path("id").asText();

        CreateProjectRequest betaReq = new CreateProjectRequest("Project Beta", "project-beta", "Beta service");
        MvcResult betaRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(betaReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String betaId = objectMapper.readTree(betaRes.getResponse().getContentAsString()).path("data").path("id").asText();

        // Register Charlie
        String charlieEmail = "charlie_" + UUID.randomUUID() + "@example.com";
        RegisterRequest charlieReg = new RegisterRequest(charlieEmail, "Password123!Secure", "Charlie User", "Charlie Org");
        MvcResult charlieRegResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(charlieReg)))
                .andExpect(status().isCreated())
                .andReturn();
        String charlieToken = objectMapper.readTree(charlieRegResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
        String charlieUserId = objectMapper.readTree(charlieRegResult.getResponse().getContentAsString())
                .path("data").path("user").path("id").asText();

        AddMemberRequest addMemberReq = new AddMemberRequest(charlieEmail, WorkspaceRole.DEVELOPER);
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addMemberReq)))
                .andExpect(status().isCreated());

        // Scope Charlie to Project Alpha ONLY
        GrantProjectAccessRequest grantAlpha = new GrantProjectAccessRequest(UUID.fromString(charlieUserId), WorkspaceRole.DEVELOPER);
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/projects/" + alphaId + "/members")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grantAlpha)))
                .andExpect(status().isCreated());

        // Charlie sees only Alpha
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + charlieToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(alphaId));

        // Direct GET on Beta by Charlie is rejected with 403
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects/" + betaId)
                        .header("Authorization", "Bearer " + charlieToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // Owner creates a Granular AccessGrant for Charlie on Project Beta
        CreateAccessGrantRequest grantBetaReq = new CreateAccessGrantRequest(
                UUID.fromString(charlieUserId),
                com.secretvault.access.model.AccessScope.PROJECT,
                UUID.fromString(betaId),
                null,
                null,
                AccessPermission.SECRET_READ
        );
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/access/grants")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(grantBetaReq)))
                .andExpect(status().isCreated());

        // Charlie now discovers both Alpha and Beta!
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects")
                        .header("Authorization", "Bearer " + charlieToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].id", hasItems(alphaId, betaId)));

        // Direct GET on Beta now succeeds
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/projects/" + betaId)
                        .header("Authorization", "Bearer " + charlieToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(betaId));
    }
}
