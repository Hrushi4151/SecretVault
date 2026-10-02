package com.secretvault.security.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.AuthResponse;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.auth.service.AuthService;
import com.secretvault.security.engine.SecurityIntelligenceEngine;
import com.secretvault.security.event.dto.RecordSecurityEventRequest;
import com.secretvault.security.event.model.SecurityEventOutcome;
import com.secretvault.security.event.model.SecurityEventSeverity;
import com.secretvault.security.event.model.SecurityEventType;
import com.secretvault.security.event.service.SecurityEventService;
import com.secretvault.security.finding.dto.AssignFindingRequest;
import com.secretvault.security.finding.dto.SecurityFindingDraft;
import com.secretvault.security.finding.dto.UpdateFindingStatusRequest;
import com.secretvault.security.finding.entity.SecurityFinding;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.model.FindingConfidence;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.security.finding.model.FindingStatus;
import com.secretvault.security.finding.service.SecurityFindingService;
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

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityCenterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private SecurityFindingService findingService;

    @Autowired
    private SecurityEventService eventService;

    @Autowired
    private SecurityIntelligenceEngine intelligenceEngine;

    private String ownerToken;
    private UUID ownerId;
    private UUID workspaceId;

    private String developerToken;
    private UUID developerId;

    private String otherWorkspaceOwnerToken;
    private UUID otherWorkspaceId;

    @BeforeEach
    void setUp() {
        // Register Owner
        String ownerEmail = "sec_owner_" + UUID.randomUUID() + "@example.com";
        AuthResponse ownerAuth = authService.register(new RegisterRequest(ownerEmail, "Password123!", "Sec Owner", "Sec Org " + UUID.randomUUID()));
        ownerToken = ownerAuth.accessToken();
        ownerId = ownerAuth.user().id();
        workspaceId = ownerAuth.activeWorkspace().id();

        // Register Developer
        String devEmail = "sec_dev_" + UUID.randomUUID() + "@example.com";
        AuthResponse devAuth = authService.register(new RegisterRequest(devEmail, "Password123!", "Sec Dev", "Dev Org " + UUID.randomUUID()));
        developerToken = devAuth.accessToken();
        developerId = devAuth.user().id();

        // Add developer to owner's workspace
        workspaceService.addMember(workspaceId, new AddMemberRequest(devEmail, WorkspaceRole.DEVELOPER), ownerId);

        // Register Another Owner with a different Workspace for IDOR tests
        String otherEmail = "other_owner_" + UUID.randomUUID() + "@example.com";
        AuthResponse otherAuth = authService.register(new RegisterRequest(otherEmail, "Password123!", "Other Owner", "Other Org " + UUID.randomUUID()));
        otherWorkspaceOwnerToken = otherAuth.accessToken();
        otherWorkspaceId = otherAuth.activeWorkspace().id();
    }

    @Test
    @DisplayName("GET /overview: Returns executive security metrics and top findings")
    void testGetOverview() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/security/overview", workspaceId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.data.overallRiskScore").isNumber())
                .andExpect(jsonPath("$.data.overallRiskLevel").isNotEmpty());
    }

    @Test
    @DisplayName("GET /posture: Returns detailed risk score and breakdown")
    void testGetPosture() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/security/posture", workspaceId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.data.overallRiskScore").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.overallRiskLevel").isNotEmpty())
                .andExpect(jsonPath("$.data.privilegedUserCount").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("GET /risk: Returns risk calculation result")
    void testGetRiskBreakdown() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/security/risk", workspaceId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.score").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.level").isNotEmpty());
    }

    @Test
    @DisplayName("POST /events & GET /events: Records and lists sanitized security events")
    void testRecordAndListEvents() throws Exception {
        RecordSecurityEventRequest recordReq = new RecordSecurityEventRequest(
                SecurityEventType.SECRET_CREATED,
                SecurityEventSeverity.INFO,
                SecurityEventOutcome.SUCCESS,
                null,
                null,
                null,
                "API_TEST",
                "192.168.1.100",
                "Mozilla/5.0",
                "req-12345",
                Map.of("targetResource", "DATABASE_CLUSTER", "password", "SuperSecret123!") // password should be scrubbed
        );

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/security/events", workspaceId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recordReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventType").value("SECRET_CREATED"))
                .andExpect(jsonPath("$.data.metadata.password").value("[REDACTED_SENSITIVE_FIELD]"))
                .andExpect(jsonPath("$.data.metadata.targetResource").value("DATABASE_CLUSTER"));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/security/events", workspaceId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("size", "10")
                        .param("sort", "timestamp,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("Findings lifecycle: list, get details, update status, and assign")
    void testFindingsLifecycle() throws Exception {
        // Create finding draft
        SecurityFindingDraft draft = new SecurityFindingDraft(
                workspaceId,
                null,
                null,
                FindingCategory.EXCESSIVE_PRIVILEGE,
                FindingSeverity.HIGH,
                FindingConfidence.HIGH,
                "Elevated Workspace Role Assignment",
                "Detailed safe description",
                "Conduct access review",
                "user:" + developerId,
                Map.of("roles", 3)
        );
        SecurityFinding finding = findingService.upsertFinding(draft);

        // List findings
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/security/findings", workspaceId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));

        // Get single finding details
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/security/findings/{findingId}", workspaceId, finding.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(finding.getId().toString()))
                .andExpect(jsonPath("$.data.title").value("Elevated Workspace Role Assignment"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        // Update status to ACKNOWLEDGED
        UpdateFindingStatusRequest updateReq = new UpdateFindingStatusRequest(FindingStatus.ACKNOWLEDGED, null);
        mockMvc.perform(patch("/api/v1/workspaces/{workspaceId}/security/findings/{findingId}", workspaceId, finding.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"));

        // Assign finding to developer
        AssignFindingRequest assignReq = new AssignFindingRequest(developerId);
        mockMvc.perform(patch("/api/v1/workspaces/{workspaceId}/security/findings/{findingId}/assign", workspaceId, finding.getId())
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assigneeUserId").value(developerId.toString()));
    }

    @Test
    @DisplayName("POST /analyze: On-demand security analysis triggers all rules")
    void testTriggerAnalysis() throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/security/analyze", workspaceId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.workspaceId").value(workspaceId.toString()))
                .andExpect(jsonPath("$.data.rulesExecuted").value(greaterThanOrEqualTo(10)));
    }

    @Test
    @DisplayName("GET /timeline: Returns combined security timeline")
    void testGetTimeline() throws Exception {
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/security/timeline", workspaceId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("limit", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("IDOR / Isolation: Users cannot access security findings of another workspace")
    void testTenantIsolation() throws Exception {
        // Create finding in workspaceId
        SecurityFindingDraft draft = new SecurityFindingDraft(
                workspaceId, null, null,
                FindingCategory.EXCESSIVE_PRIVILEGE, FindingSeverity.HIGH, FindingConfidence.HIGH,
                "Isolated Finding", "Desc", "Remediation", "user:isolation", Map.of()
        );
        SecurityFinding finding = findingService.upsertFinding(draft);

        // otherWorkspaceOwner tries to access finding belonging to workspaceId
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/security/findings/{findingId}", workspaceId, finding.getId())
                        .header("Authorization", "Bearer " + otherWorkspaceOwnerToken))
                .andExpect(status().isForbidden());

        // otherWorkspaceOwner tries to access finding via their own workspaceId URL (cross-tenant lookup)
        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/security/findings/{findingId}", otherWorkspaceId, finding.getId())
                        .header("Authorization", "Bearer " + otherWorkspaceOwnerToken))
                .andExpect(status().isNotFound());
    }
}
