package com.secretvault.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.automation.controller.AutomationApprovalController.DecideApprovalRequest;
import com.secretvault.automation.controller.AutomationPolicyController.CreateAutomationPolicyRequest;
import com.secretvault.automation.controller.AutomationPolicyController.UpdateAutomationPolicyRequest;
import com.secretvault.automation.engine.AutomationEngine;
import com.secretvault.automation.entity.AutomationApprovalStatus;
import com.secretvault.automation.entity.AutomationExecutionStatus;
import com.secretvault.automation.entity.AutomationPolicyScope;
import com.secretvault.events.model.BaseDomainEvent;
import com.secretvault.events.model.EventSeverity;
import com.secretvault.events.model.EventType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Security Automation & Policy Engine Full Lifecycle & Governance Integration Tests")
public class AutomationPolicyEngineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AutomationEngine automationEngine;

    private String creatorToken;
    private UUID creatorId;
    private UUID workspaceId;

    private String secondOperatorToken;
    private UUID secondOperatorId;

    @BeforeEach
    void setUp() throws Exception {
        // 1. Register Policy Creator (Workspace Owner)
        String creatorEmail = "creator_" + UUID.randomUUID() + "@example.com";
        RegisterRequest reg1 = new RegisterRequest(creatorEmail, "Password123!Secure", "Policy Creator", "SecOps Corp");
        MvcResult res1 = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg1)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode data1 = objectMapper.readTree(res1.getResponse().getContentAsString()).path("data");
        creatorToken = data1.path("accessToken").asText();
        creatorId = UUID.fromString(data1.path("user").path("id").asText());

        // Get workspace ID for creator
        MvcResult wsRes = mockMvc.perform(get("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andReturn();
        workspaceId = UUID.fromString(objectMapper.readTree(wsRes.getResponse().getContentAsString())
                .path("data").get(0).path("id").asText());

        // 2. Register Second Operator in the same workspace (for four-eyes approvals)
        String opEmail = "operator_" + UUID.randomUUID() + "@example.com";
        RegisterRequest reg2 = new RegisterRequest(opEmail, "Password123!Secure", "Second Approver", "SecOps Corp");
        MvcResult res2 = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg2)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode data2 = objectMapper.readTree(res2.getResponse().getContentAsString()).path("data");
        secondOperatorToken = data2.path("accessToken").asText();
        secondOperatorId = UUID.fromString(data2.path("user").path("id").asText());

        // Add second operator to workspace as ADMIN / MANAGER
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/members")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", opEmail,
                                "role", "ADMIN"
                        ))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("AST Policy Lifecycle: Creation, versioning, toggle, and malformed rejection")
    void testPolicyLifecycleAndValidation() throws Exception {
        // 1. Rejection of malformed condition JSON
        CreateAutomationPolicyRequest badCondReq = new CreateAutomationPolicyRequest(
                "Malformed Condition Policy",
                "Testing validation",
                true,
                100,
                AutomationPolicyScope.WORKSPACE,
                null, null, null,
                "SECRET_COMPROMISED",
                "[{ \"operator\": \"AND\", \"nested\": [] }]", // empty nested for AND is invalid
                "[]",
                false,
                false
        );

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/automation-policies")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badCondReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Logical operator AND must contain non-empty nested conditions")));

        // 2. Rejection of malformed action JSON
        CreateAutomationPolicyRequest badActionReq = new CreateAutomationPolicyRequest(
                "Malformed Action Policy",
                "Testing action validation",
                true,
                100,
                AutomationPolicyScope.WORKSPACE,
                null, null, null,
                "SECRET_COMPROMISED",
                "[]",
                "[{ \"type\": null }]",
                false,
                false
        );

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/automation-policies")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badActionReq)))
                .andExpect(status().isBadRequest());

        // 3. Valid policy creation
        String validCond = "[{\"field\":\"severity\",\"operator\":\"EQUALS\",\"value\":\"CRITICAL\"}]";
        String validAction = "[{\"type\":\"NOTIFY\",\"parameters\":{\"title\":\"Alert\",\"severity\":\"HIGH\"}}]";
        CreateAutomationPolicyRequest validReq = new CreateAutomationPolicyRequest(
                "Valid Notification Policy",
                "Sends notification on critical severity",
                true,
                50,
                AutomationPolicyScope.WORKSPACE,
                null, null, null,
                "SECRET_COMPROMISED",
                validCond,
                validAction,
                false,
                false
        );

        MvcResult createRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/automation-policies")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Valid Notification Policy"))
                .andExpect(jsonPath("$.data.policyVersion").value(1))
                .andReturn();

        UUID policyId = UUID.fromString(objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("id").asText());

        // 4. Update policy increments version
        UpdateAutomationPolicyRequest updateReq = new UpdateAutomationPolicyRequest(
                "Valid Notification Policy Updated",
                "Updated description",
                true,
                40,
                AutomationPolicyScope.WORKSPACE,
                null, null, null,
                "SECRET_COMPROMISED",
                validCond,
                validAction,
                false,
                false
        );

        mockMvc.perform(put("/api/v1/workspaces/" + workspaceId + "/automation-policies/" + policyId)
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.policyVersion").value(2));

        // 5. Disable policy
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/automation-policies/" + policyId + "/disable")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        // 6. Enable policy
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/automation-policies/" + policyId + "/enable")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    @DisplayName("Anti-Loop Tripwires: Events at depth >= 5 are safely aborted with LOOP_ABORTED")
    void testAntiLoopTripwireOnDepth() throws Exception {
        // Create an automated policy
        CreateAutomationPolicyRequest req = new CreateAutomationPolicyRequest(
                "Loop Protection Policy",
                "Tests execution depth limits",
                true,
                100,
                AutomationPolicyScope.WORKSPACE,
                null, null, null,
                "LEASE_EXPIRED",
                "[]",
                "[{\"type\":\"NOTIFY\",\"parameters\":{\"title\":\"Loop Test\"}}]",
                false,
                false
        );

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/automation-policies")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Dispatch domain event with executionDepth = 5 (threshold is 5)
        BaseDomainEvent deepEvent = BaseDomainEvent.builder()
                .eventType(EventType.LEASE_EXPIRED)
                .workspaceId(workspaceId)
                .severity(EventSeverity.MEDIUM)
                .source("automated-tester")
                .metadata(Map.of("executionDepth", 5))
                .build();

        automationEngine.processEvent(deepEvent);

        // Verify execution history reflects LOOP_ABORTED
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/automation-executions?status=LOOP_ABORTED")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.content[0].status").value("LOOP_ABORTED"))
                .andExpect(jsonPath("$.data.content[0].executionDepth").value(5));
    }

    @Test
    @DisplayName("Four-Eyes Approval: Requester cannot approve own action, distinct operator succeeds")
    void testFourEyesApprovalSeparationOfDuties() throws Exception {
        // 1. Create a policy requiring approval with sensitive action (TRIGGER_ROTATION)
        UUID secretId = UUID.randomUUID();
        CreateAutomationPolicyRequest req = new CreateAutomationPolicyRequest(
                "Sensitive Emergency Rotation",
                "Rotates secret when compromised",
                true,
                10,
                AutomationPolicyScope.WORKSPACE,
                null, null, null,
                "SECRET_COMPROMISED",
                "[]",
                "[{\"type\":\"TRIGGER_ROTATION\",\"parameters\":{\"secretId\":\"" + secretId + "\"}}]",
                false,
                true // approvalRequired
        );

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/automation-policies")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // 2. Dispatch triggering event from creator
        BaseDomainEvent event = BaseDomainEvent.builder()
                .eventType(EventType.SECRET_COMPROMISED)
                .workspaceId(workspaceId)
                .secretId(secretId)
                .severity(EventSeverity.CRITICAL)
                .source("threat-intel")
                .actorId(creatorId.toString())
                .build();

        automationEngine.processEvent(event);

        // 3. Verify approval queue has 1 PENDING item
        MvcResult listRes = mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/automation-approvals?status=PENDING")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.content[0].actionType").value("TRIGGER_ROTATION"))
                .andReturn();

        JsonNode approvalItem = objectMapper.readTree(listRes.getResponse().getContentAsString())
                .path("data").path("content").get(0);
        UUID approvalId = UUID.fromString(approvalItem.path("id").asText());

        // 4. Policy Creator attempts to approve -> Anti-self-approval violation (403 Forbidden)
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/automation-approvals/" + approvalId + "/decide")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DecideApprovalRequest(true, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Anti-self-approval violation")));

        // 5. Second Operator (independent human) approves -> Succeeds (200 OK)
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/automation-approvals/" + approvalId + "/decide")
                        .header("Authorization", "Bearer " + secondOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DecideApprovalRequest(true, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.decidedBy").value(secondOperatorId.toString()));

        // 6. Replay protection: Attempting to decide again fails
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/automation-approvals/" + approvalId + "/decide")
                        .header("Authorization", "Bearer " + secondOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DecideApprovalRequest(true, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Approval is already decided")));
    }

    @Test
    @DisplayName("Approval Cancellation: Operator can cancel pending approval request")
    void testCancelPendingApproval() throws Exception {
        UUID secretId = UUID.randomUUID();
        CreateAutomationPolicyRequest req = new CreateAutomationPolicyRequest(
                "Lease Revoke Policy",
                "Revokes leases on breach",
                true,
                10,
                AutomationPolicyScope.WORKSPACE,
                null, null, null,
                "SECRET_COMPROMISED",
                "[]",
                "[{\"type\":\"REVOKE_LEASE\",\"parameters\":{}}]",
                false,
                true // approvalRequired
        );

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/automation-policies")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        BaseDomainEvent event = BaseDomainEvent.builder()
                .eventType(EventType.SECRET_COMPROMISED)
                .workspaceId(workspaceId)
                .secretId(secretId)
                .severity(EventSeverity.CRITICAL)
                .source("threat-intel")
                .actorId(creatorId.toString())
                .build();

        automationEngine.processEvent(event);

        MvcResult listRes = mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/automation-approvals?status=PENDING")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andReturn();

        UUID approvalId = UUID.fromString(objectMapper.readTree(listRes.getResponse().getContentAsString())
                .path("data").path("content").get(0).path("id").asText());

        // Cancel approval
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/automation-approvals/" + approvalId + "/cancel")
                        .header("Authorization", "Bearer " + secondOperatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Cancelled by operator"));
    }

    @Test
    @DisplayName("Dry-Run Simulation: Accurately evaluates decisions without mutating state or exposing secrets")
    void testDryRunSimulationEndpoint() throws Exception {
        // Create an active policy with specific condition
        String cond = "[{\"field\":\"severity\",\"operator\":\"EQUALS\",\"value\":\"CRITICAL\"}]";
        String action = "[{\"type\":\"SUSPEND_MACHINE\",\"parameters\":{\"machineIdentityId\":\"" + UUID.randomUUID() + "\"}}]";

        CreateAutomationPolicyRequest req = new CreateAutomationPolicyRequest(
                "Simulated Suspension Policy",
                "Suspends machine on critical alerts",
                true,
                20,
                AutomationPolicyScope.WORKSPACE,
                null, null, null,
                "SECRET_COMPROMISED",
                cond,
                action,
                false,
                true
        );

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/automation-policies")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Run simulation with matching event
        BaseDomainEvent mockEvent = BaseDomainEvent.builder()
                .eventType(EventType.SECRET_COMPROMISED)
                .workspaceId(workspaceId)
                .severity(EventSeverity.CRITICAL)
                .source("simulator")
                .build();

        MvcResult simResult = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/automation-policies/simulate")
                        .header("Authorization", "Bearer " + creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mockEvent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andReturn();

        String body = simResult.getResponse().getContentAsString();
        JsonNode simData = objectMapper.readTree(body).path("data").get(0);

        assertThat(simData.path("matched").asBoolean()).isTrue();
        assertThat(simData.path("plannedActions")).isNotEmpty();
        assertThat(simData.path("requiresApproval").asBoolean()).isTrue();
        assertThat(simData.path("requiredPermissions").toString()).contains("MACHINE_IDENTITY_MANAGE");

        // Verify that NO actual executions were persisted during simulation
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/automation-executions")
                        .header("Authorization", "Bearer " + creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }
}
