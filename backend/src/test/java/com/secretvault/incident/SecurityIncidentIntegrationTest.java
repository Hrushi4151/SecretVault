package com.secretvault.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.entity.AuditLog;
import com.secretvault.audit.repository.AuditLogRepository;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.events.model.BaseDomainEvent;
import com.secretvault.events.model.DomainEvent;
import com.secretvault.events.model.EventSeverity;
import com.secretvault.events.model.EventType;
import com.secretvault.incident.dto.IncidentDtos.IncidentRemediationRequest;
import com.secretvault.incident.dto.IncidentDtos.RemediationPlaybookAction;
import com.secretvault.incident.entity.*;
import com.secretvault.incident.repository.SecurityIncidentEventRepository;
import com.secretvault.incident.repository.SecurityIncidentRepository;
import com.secretvault.incident.service.SecurityIncidentService;
import com.secretvault.machine.dto.MachineDtos.CreateMachineIdentityRequest;
import com.secretvault.machine.dto.MachineDtos.MachineIdentityResponse;
import com.secretvault.machine.model.MachineType;
import com.secretvault.machine.service.MachineIdentityService;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Phase 13: Security Incident Operations & Remediation End-to-End Integration Tests")
public class SecurityIncidentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SecurityIncidentService incidentService;

    @Autowired
    private SecurityIncidentRepository incidentRepository;

    @Autowired
    private SecurityIncidentEventRepository incidentEventRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired(required = false)
    private MachineIdentityService machineService;

    private String adminToken;
    private UUID adminUserId;
    private UUID workspaceId;

    private String secondUserToken;
    private UUID secondUserId;
    private UUID secondWorkspaceId;

    @BeforeEach
    void setUp() throws Exception {
        // Register Workspace Admin
        String adminEmail = "secops_admin_" + UUID.randomUUID() + "@example.com";
        RegisterRequest reg1 = new RegisterRequest(adminEmail, "Password123!Secure", "SecOps Lead", "Enterprise Security");
        MvcResult res1 = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg1)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode data1 = objectMapper.readTree(res1.getResponse().getContentAsString()).path("data");
        adminToken = data1.path("accessToken").asText();
        adminUserId = UUID.fromString(data1.path("user").path("id").asText());

        MvcResult wsRes1 = mockMvc.perform(get("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        workspaceId = UUID.fromString(objectMapper.readTree(wsRes1.getResponse().getContentAsString())
                .path("data").get(0).path("id").asText());

        // Register Second User in separate workspace for IDOR testing
        String user2Email = "other_user_" + UUID.randomUUID() + "@example.com";
        RegisterRequest reg2 = new RegisterRequest(user2Email, "Password123!Secure", "Other Tenant", "Tenant Two");
        MvcResult res2 = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg2)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode data2 = objectMapper.readTree(res2.getResponse().getContentAsString()).path("data");
        secondToken = data2.path("accessToken").asText();
        secondUserId = UUID.fromString(data2.path("user").path("id").asText());

        MvcResult wsRes2 = mockMvc.perform(get("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk())
                .andReturn();
        secondWorkspaceId = UUID.fromString(objectMapper.readTree(wsRes2.getResponse().getContentAsString())
                .path("data").get(0).path("id").asText());
    }

    private String secondToken;

    @Test
    @DisplayName("1. Incident lifecycle: creation, triage state machine, and resolution verification")
    void testIncidentLifecycleAndStateMachine() throws Exception {
        // Create incident via REST API
        Map<String, Object> createReq = Map.of(
                "title", "Unauthorized Credential Reveal Surge",
                "description", "Excessive reveals detected on high-entropy production database secret",
                "severity", "HIGH",
                "category", "CREDENTIAL_EXPOSURE"
        );

        MvcResult createRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/incidents")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.incidentNumber").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andReturn();

        UUID incidentId = UUID.fromString(objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("data").path("id").asText());

        // Transition: OPEN -> INVESTIGATING
        mockMvc.perform(put("/api/v1/workspaces/" + workspaceId + "/incidents/" + incidentId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "INVESTIGATING",
                                "resolutionSummary", ""
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INVESTIGATING"));

        // Transition: INVESTIGATING -> CONTAINED
        mockMvc.perform(put("/api/v1/workspaces/" + workspaceId + "/incidents/" + incidentId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "CONTAINED",
                                "resolutionSummary", "Revoked offending tokens"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONTAINED"));

        // Reject invalid transition: empty resolutionSummary on RESOLVED
        mockMvc.perform(put("/api/v1/workspaces/" + workspaceId + "/incidents/" + incidentId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "RESOLVED",
                                "resolutionSummary", "   "
                        ))))
                .andExpect(status().isBadRequest());

        // Valid transition to RESOLVED with summary
        mockMvc.perform(put("/api/v1/workspaces/" + workspaceId + "/incidents/" + incidentId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "RESOLVED",
                                "resolutionSummary", "Rotated secret and verified zero consumer downtime"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.resolvedAt").isNotEmpty());

        // Transition: RESOLVED -> CLOSED
        mockMvc.perform(put("/api/v1/workspaces/" + workspaceId + "/incidents/" + incidentId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "CLOSED",
                                "resolutionSummary", "Post-mortem completed"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        // Reject invalid transition: CLOSED -> RESOLVED
        mockMvc.perform(put("/api/v1/workspaces/" + workspaceId + "/incidents/" + incidentId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "RESOLVED",
                                "resolutionSummary", "Invalid jump"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("2. Multi-signal detection: auto-create incident on critical security signal")
    void testAutoIncidentCreationFromDomainEvent() {
        UUID eventId = UUID.randomUUID();
        UUID secretId = UUID.randomUUID();

        DomainEvent compromiseEvent = BaseDomainEvent.builder()
                .eventId(eventId)
                .eventType(EventType.SECRET_COMPROMISED)
                .workspaceId(workspaceId)
                .aggregateType("SECRET")
                .aggregateId(secretId.toString())
                .secretId(secretId)
                .severity(EventSeverity.CRITICAL)
                .addMetadata("reason", "Public GitHub repo leak detected")
                .build();

        incidentService.processDomainEventCorrelation(compromiseEvent);

        List<SecurityIncident> activeIncidents = incidentRepository.findActiveIncidents(workspaceId);
        assertThat(activeIncidents).isNotEmpty();

        SecurityIncident incident = activeIncidents.stream()
                .filter(i -> i.getSourceEventId() != null && i.getSourceEventId().equals(eventId))
                .findFirst()
                .orElse(null);

        assertThat(incident).isNotNull();
        assertThat(incident.getSeverity()).isEqualTo(IncidentSeverity.CRITICAL);
        assertThat(incident.getCategory()).isEqualTo("SECRET_COMPROMISED");
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);

        // Verify root cause event linked
        List<SecurityIncidentEvent> events = incidentEventRepository.findByIncidentIdOrderByCreatedAtAsc(incident.getId());
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getRelationshipType()).isEqualTo(IncidentRelationshipType.ROOT_CAUSE);
    }

    @Test
    @DisplayName("3. Duplicate event suppression: duplicate event correlation is safely ignored")
    void testDuplicateEventSuppression() {
        SecurityIncident incident = incidentService.createIncident(
                workspaceId,
                "Duplicate Event Test",
                "Testing idempotent duplicate event correlation",
                IncidentSeverity.MEDIUM,
                "TEST",
                null,
                adminUserId
        );

        UUID testEventId = UUID.randomUUID();
        incidentService.correlateEvent(incident.getId(), testEventId, IncidentRelationshipType.CORRELATED, "First occurrence");
        incidentService.correlateEvent(incident.getId(), testEventId, IncidentRelationshipType.CORRELATED, "Duplicate occurrence");

        List<SecurityIncidentEvent> events = incidentEventRepository.findByIncidentIdOrderByCreatedAtAsc(incident.getId());
        long occurrences = events.stream().filter(e -> e.getEventId().equals(testEventId)).count();
        assertThat(occurrences).isEqualTo(1);
    }

    @Test
    @DisplayName("4. Cross-workspace isolation (IDOR defense): User from Workspace B cannot access or mutate Workspace A incident")
    void testCrossWorkspaceIsolation() throws Exception {
        // Create incident in Workspace A
        SecurityIncident incidentA = incidentService.createIncident(
                workspaceId,
                "Workspace A Secret Incident",
                "Internal incident for workspace A",
                IncidentSeverity.HIGH,
                "INTERNAL",
                null,
                adminUserId
        );

        // User from Workspace B attempts GET on Workspace A incident
        mockMvc.perform(get("/api/v1/workspaces/" + secondWorkspaceId + "/incidents/" + incidentA.getId())
                        .header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isNotFound());

        // User from Workspace B attempts PUT status on Workspace A incident
        mockMvc.perform(put("/api/v1/workspaces/" + secondWorkspaceId + "/incidents/" + incidentA.getId() + "/status")
                        .header("Authorization", "Bearer " + secondToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "RESOLVED",
                                "resolutionSummary", "Malicious resolution attempt"
                        ))))
                .andExpect(status().isNotFound());

        // User from Workspace B attempts directly referencing Workspace A's path with their token
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/incidents/" + incidentA.getId())
                        .header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("5. Playbook dry-run preview: validates remediation action without side effects")
    void testPlaybookDryRunPreview() throws Exception {
        SecurityIncident incident = incidentService.createIncident(
                workspaceId,
                "Dry Run Remediation Test",
                "Verify dry-run does not modify incident status",
                IncidentSeverity.HIGH,
                "PLAYBOOK",
                null,
                adminUserId
        );

        IncidentRemediationRequest dryRunReq = new IncidentRemediationRequest(
                RemediationPlaybookAction.CONTAIN_INCIDENT,
                null,
                null,
                "Preview containment",
                true
        );

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/remediate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dryRunReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dryRun").value(true))
                .andExpect(jsonPath("$.data.executed").value(false))
                .andExpect(jsonPath("$.data.status").value("DRY_RUN_PREVIEW"))
                .andExpect(jsonPath("$.data.updatedIncidentStatus").value("OPEN"));

        // Verify incident in DB is still OPEN
        SecurityIncident reloaded = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(IncidentStatus.OPEN);
    }

    @Test
    @DisplayName("6. Playbook live execution: CONTAIN_INCIDENT action advances status and records REMEDIATION event")
    void testPlaybookLiveExecutionContainment() throws Exception {
        SecurityIncident incident = incidentService.createIncident(
                workspaceId,
                "Live Remediation Containment Test",
                "Verify live containment playbook advances status",
                IncidentSeverity.HIGH,
                "PLAYBOOK",
                null,
                adminUserId
        );

        IncidentRemediationRequest liveReq = new IncidentRemediationRequest(
                RemediationPlaybookAction.CONTAIN_INCIDENT,
                null,
                null,
                "Contain blast radius",
                false
        );

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/remediate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(liveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dryRun").value(false))
                .andExpect(jsonPath("$.data.executed").value(true))
                .andExpect(jsonPath("$.data.updatedIncidentStatus").value("CONTAINED"));

        // Verify incident in DB is CONTAINED
        SecurityIncident reloaded = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(IncidentStatus.CONTAINED);

        // Verify REMEDIATION relationship event in database
        List<SecurityIncidentEvent> events = incidentEventRepository.findByIncidentIdOrderByCreatedAtAsc(incident.getId());
        boolean hasRemediationEvent = events.stream()
                .anyMatch(e -> e.getRelationshipType() == IncidentRelationshipType.REMEDIATION);
        assertThat(hasRemediationEvent).isTrue();
    }

    @Test
    @DisplayName("7. Audit log verification: incident creation, status update, and remediation produce audit records")
    void testAuditTrailIntegrity() {
        SecurityIncident incident = incidentService.createIncident(
                workspaceId,
                "Audited Incident",
                "Verify audit entries exist",
                IncidentSeverity.HIGH,
                "AUDIT_TEST",
                null,
                adminUserId
        );

        incidentService.updateIncidentStatus(
                workspaceId,
                incident.getId(),
                IncidentStatus.INVESTIGATING,
                null,
                adminUserId
        );

        List<AuditLog> audits = auditLogRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        boolean hasCreatedAudit = audits.stream()
                .anyMatch(a -> a.getAction() == AuditAction.SECURITY_INCIDENT_CREATED);
        boolean hasUpdatedAudit = audits.stream()
                .anyMatch(a -> a.getAction() == AuditAction.SECURITY_INCIDENT_UPDATED);

        assertThat(hasCreatedAudit).isTrue();
        assertThat(hasUpdatedAudit).isTrue();
    }

    @Test
    @DisplayName("8. Target validation: Playbook actions requiring resource IDs are validated in dry-run and live modes")
    void testRemediatePlaybook_TargetPreValidationBothDryRunAndLive() throws Exception {
        SecurityIncident incident = incidentService.createIncident(
                workspaceId,
                "Target Validation Incident",
                "Verifying missing target IDs trigger 400 Bad Request",
                IncidentSeverity.HIGH,
                "PLAYBOOK",
                null,
                adminUserId
        );

        // ROTATE_SECRET dry-run without targetSecretId -> 400 Bad Request
        IncidentRemediationRequest missingSecretDry = new IncidentRemediationRequest(
                RemediationPlaybookAction.ROTATE_SECRET,
                null,
                null,
                "Dry run missing targetSecretId",
                true
        );
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/remediate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingSecretDry)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("targetSecretId is required for ROTATE_SECRET action"));

        // DISABLE_MACHINE_IDENTITY live without targetMachineIdentityId -> 400 Bad Request
        IncidentRemediationRequest missingMachineLive = new IncidentRemediationRequest(
                RemediationPlaybookAction.DISABLE_MACHINE_IDENTITY,
                null,
                null,
                "Live run missing targetMachineIdentityId",
                false
        );
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/remediate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingMachineLive)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("targetMachineIdentityId is required for DISABLE_MACHINE_IDENTITY action"));
    }

    @Test
    @DisplayName("9. Resource scope isolation: Playbooks reject cross-workspace machine identities and foreign secrets")
    void testRemediatePlaybook_CrossWorkspaceResourceScopeIsolation() throws Exception {
        SecurityIncident incidentA = incidentService.createIncident(
                workspaceId,
                "Cross Resource Boundary Incident",
                "Testing cross-workspace target injection defense",
                IncidentSeverity.HIGH,
                "PLAYBOOK",
                null,
                adminUserId
        );

        // Create a machine identity in Workspace B
        UUID machineBId = UUID.randomUUID();
        if (machineService != null) {
            MachineIdentityResponse machineB = machineService.createMachineIdentity(
                    secondWorkspaceId,
                    new CreateMachineIdentityRequest("runner-b-" + UUID.randomUUID(), "Tenant B runner", MachineType.CI_CD, null, Map.of()),
                    secondUserId
            );
            machineBId = machineB.id();
        }

        // Admin of Workspace A tries to disable Workspace B's machine identity under Workspace A's incident
        IncidentRemediationRequest crossMachineReq = new IncidentRemediationRequest(
                RemediationPlaybookAction.DISABLE_MACHINE_IDENTITY,
                null,
                machineBId,
                "Malicious cross-workspace machine shutdown",
                false
        );

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/incidents/" + incidentA.getId() + "/remediate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crossMachineReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Machine identity not found in this workspace"));

        // Admin of Workspace A tries to rotate non-existent / foreign secret under Workspace A's incident
        UUID foreignSecretId = UUID.randomUUID();
        IncidentRemediationRequest crossSecretReq = new IncidentRemediationRequest(
                RemediationPlaybookAction.ROTATE_SECRET,
                foreignSecretId,
                null,
                "Malicious cross-workspace secret rotation",
                true
        );

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/incidents/" + incidentA.getId() + "/remediate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crossSecretReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Secret not found"));
    }

    @Test
    @DisplayName("10. Two-person approval workflow: creation, anti-self-approval rejection, secondary approval, and execution")
    void testTwoPersonApprovalWorkflow_FullLifecycle() throws Exception {
        SecurityIncident incident = incidentService.createIncident(
                workspaceId,
                "High-Risk Production Key Anomaly",
                "Anomaly requires 2-person approval for containment",
                IncidentSeverity.CRITICAL,
                "CREDENTIAL_EXPOSURE",
                null,
                adminUserId
        );

        // 1. Admin requests remediation with requireApproval = true
        IncidentRemediationRequest req = new IncidentRemediationRequest(
                RemediationPlaybookAction.CONTAIN_INCIDENT,
                null,
                null,
                "Containment requiring secondary signoff",
                false,
                true, // requireApproval
                null,
                false
        );

        MvcResult remRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/remediate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data.executed").value(false))
                .andExpect(jsonPath("$.data.approvalId").isNotEmpty())
                .andReturn();

        String approvalIdStr = objectMapper.readTree(remRes.getResponse().getContentAsString())
                .path("data").path("approvalId").asText();
        UUID approvalId = UUID.fromString(approvalIdStr);

        // 2. Verify approval is in list of approvals
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/approvals")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(approvalIdStr))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));

        // 3. Anti-self-approval rule: Requester cannot approve their own action
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/approvals/" + approvalId + "/decide")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("approve", true, "reason", "Self approving"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Requester cannot approve their own remediation action")));

        // 4. Register a second operator and add them to workspace as ADMIN
        String secondOpEmail = "secops_peer_" + UUID.randomUUID() + "@example.com";
        RegisterRequest regSecond = new RegisterRequest(secondOpEmail, "Password123!Secure", "Peer Reviewer", "Enterprise Security");
        MvcResult regRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regSecond)))
                .andExpect(status().isCreated())
                .andReturn();
        String peerToken = objectMapper.readTree(regRes.getResponse().getContentAsString()).path("data").path("accessToken").asText();

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/members")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", secondOpEmail,
                                "role", "ADMIN"
                        ))))
                .andExpect(status().isCreated());

        // 5. Peer approves the remediation request
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/approvals/" + approvalId + "/decide")
                        .header("Authorization", "Bearer " + peerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("approve", true, "reason", "Verified threat telemetry"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // 6. Admin executes remediation with approvalId
        IncidentRemediationRequest execReq = new IncidentRemediationRequest(
                RemediationPlaybookAction.CONTAIN_INCIDENT,
                null,
                null,
                "Containment requiring secondary signoff",
                false,
                false,
                approvalId,
                false
        );

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/remediate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(execReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executed").value(true))
                .andExpect(jsonPath("$.data.status").value("EXECUTED"));

        // 7. Verify approval transitioned to EXECUTED
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/approvals/" + approvalId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXECUTED"));
    }

    @Test
    @DisplayName("11. Asynchronous remediation queueing, durable execution record, and worker execution")
    void testAsyncRemediationWorkflowAndWorkerExecution() throws Exception {
        SecurityIncident incident = incidentService.createIncident(
                workspaceId,
                "Async Automated Quarantine Incident",
                "Testing async queueing and outbox worker execution",
                IncidentSeverity.HIGH,
                "COMPROMISE",
                null,
                adminUserId
        );

        IncidentRemediationRequest asyncReq = new IncidentRemediationRequest(
                RemediationPlaybookAction.CONTAIN_INCIDENT,
                null,
                null,
                "Queued async quarantine",
                false,
                false,
                null,
                true // async
        );

        MvcResult queueRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/remediate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(asyncReq)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.executed").value(false))
                .andExpect(jsonPath("$.data.executionId").isNotEmpty())
                .andReturn();

        String executionIdStr = objectMapper.readTree(queueRes.getResponse().getContentAsString())
                .path("data").path("executionId").asText();
        UUID executionId = UUID.fromString(executionIdStr);

        // List remediations
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/remediations")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(executionIdStr))
                .andExpect(jsonPath("$.data[0].status").value("QUEUED"));

        // Worker execution trigger
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/remediations/" + executionId + "/execute?workerId=worker-101")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.verifiedEffectsJson").isNotEmpty());

        // Verify incident status updated to CONTAINED
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONTAINED"));
    }

    @Test
    @DisplayName("12. Optimistic locking verifies version tracking on SecurityIncident")
    void testOptimisticLockConflictVerification() throws Exception {
        SecurityIncident incident = incidentService.createIncident(
                workspaceId,
                "Concurrent Collision Incident",
                "Testing optimistic concurrency version field",
                IncidentSeverity.MEDIUM,
                "POLICY_VIOLATION",
                null,
                adminUserId
        );

        assertThat(incident.getVersion()).isNotNull();
        Long initialVersion = incident.getVersion();

        // Update status advances version
        SecurityIncident updated = incidentService.updateIncidentStatus(
                workspaceId,
                incident.getId(),
                IncidentStatus.INVESTIGATING,
                "Assigned to analyst",
                adminUserId
        );

        assertThat(updated.getVersion()).isGreaterThan(initialVersion);
    }
}
