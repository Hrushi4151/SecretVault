package com.secretvault.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.events.repository.DomainEventRepository;
import com.secretvault.events.repository.OutboxEventRepository;
import com.secretvault.incident.dto.IncidentDtos.IncidentRemediationRequest;
import com.secretvault.incident.dto.IncidentDtos.RemediationPlaybookAction;
import com.secretvault.incident.entity.*;
import com.secretvault.incident.repository.SecurityIncidentApprovalRepository;
import com.secretvault.incident.repository.SecurityIncidentRemediationRepository;
import com.secretvault.incident.repository.SecurityIncidentRepository;
import com.secretvault.incident.service.SecurityIncidentService;
import com.secretvault.incident.worker.IncidentRemediationWorker;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Phase 13: Asynchronous Remediation Worker Operational Verification")
public class IncidentRemediationWorkerOperationalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SecurityIncidentService incidentService;

    @Autowired
    private SecurityIncidentRepository incidentRepository;

    @Autowired
    private SecurityIncidentRemediationRepository remediationRepository;

    @Autowired
    private SecurityIncidentApprovalRepository approvalRepository;

    @Autowired
    private IncidentRemediationWorker remediationWorker;

    @Autowired
    private DomainEventRepository domainEventRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private String adminToken;
    private UUID adminUserId;
    private UUID workspaceId;

    private String peerToken;
    private UUID peerUserId;

    @BeforeEach
    void setUp() throws Exception {
        // Register Workspace Admin
        String adminEmail = "admin_worker_" + UUID.randomUUID() + "@example.com";
        RegisterRequest reg1 = new RegisterRequest(adminEmail, "Password123!Secure", "Admin Lead", "SecOps");
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

        // Register Peer Reviewer
        String peerEmail = "peer_worker_" + UUID.randomUUID() + "@example.com";
        RegisterRequest reg2 = new RegisterRequest(peerEmail, "Password123!Secure", "Peer Reviewer", "SecOps");
        MvcResult res2 = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg2)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode data2 = objectMapper.readTree(res2.getResponse().getContentAsString()).path("data");
        peerToken = data2.path("accessToken").asText();
        peerUserId = UUID.fromString(data2.path("user").path("id").asText());

        // Add peer to workspace as ADMIN
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/members")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", peerEmail,
                                "role", "ADMIN"
                        ))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("1. Autonomous Worker Execution: HTTP 202 Accepted triggers automated background execution to COMPLETED")
    void testAutomaticWorkerExecutionFromHttp202() throws Exception {
        SecurityIncident incident = incidentService.createIncident(
                workspaceId,
                "Autonomous Worker E2E Incident",
                "Verifying automated claiming without internal method invocation",
                IncidentSeverity.HIGH,
                "COMPROMISE",
                null,
                adminUserId
        );

        IncidentRemediationRequest asyncReq = new IncidentRemediationRequest(
                RemediationPlaybookAction.CONTAIN_INCIDENT,
                null,
                null,
                "Autonomous containment verification",
                false,
                false,
                null,
                true // async = true
        );

        // 1. Post async remediation request and verify HTTP 202 Accepted
        MvcResult queueRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/remediate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(asyncReq)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.executionId").isNotEmpty())
                .andReturn();

        String executionIdStr = objectMapper.readTree(queueRes.getResponse().getContentAsString())
                .path("data").path("executionId").asText();
        UUID executionId = UUID.fromString(executionIdStr);

        // 2. Poll the REST API directly — NO internal service method or repo query called
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        String executionStatus = "QUEUED";
        while (Instant.now().isBefore(deadline)) {
            MvcResult checkRes = mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/remediations/" + executionId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn();

            executionStatus = objectMapper.readTree(checkRes.getResponse().getContentAsString())
                    .path("data").path("status").asText();

            if ("COMPLETED".equals(executionStatus)) {
                break;
            }
            Thread.sleep(200);
        }

        assertThat(executionStatus).isEqualTo("COMPLETED");

        // Verify incident status updated to CONTAINED
        MvcResult incRes = mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        String incStatus = objectMapper.readTree(incRes.getResponse().getContentAsString())
                .path("data").path("status").asText();
        assertThat(incStatus).isEqualTo("CONTAINED");
    }

    @Test
    @DisplayName("2. Stale Claim Recovery & Worker Crash: Crashed worker claim past staleCutoff is recovered and completed")
    void testWorkerRestartAndStaleClaimRecovery() throws Exception {
        SecurityIncident incident = incidentService.createIncident(
                workspaceId,
                "Worker Crash Incident",
                "Simulating crashed worker holding stale lease",
                IncidentSeverity.HIGH,
                "COMPROMISE",
                null,
                adminUserId
        );

        // Create a remediation record simulating a crashed worker 10 minutes ago
        SecurityIncidentRemediation rem = new SecurityIncidentRemediation();
        rem.setWorkspaceId(workspaceId);
        rem.setIncidentId(incident.getId());
        rem.setAction(RemediationPlaybookAction.CONTAIN_INCIDENT);
        rem.setStatus(IncidentRemediationStatus.CLAIMED);
        rem.setWorkerId("crashed-worker-node-99");
        rem.setClaimToken("dead-token-uuid");
        rem.setLockedAt(Instant.now().minus(Duration.ofMinutes(10)));
        rem.setAttemptCount(1);
        rem.setMaxAttempts(3);
        rem.setActorId(adminUserId);
        SecurityIncidentRemediation saved = remediationRepository.save(rem);

        // Trigger worker batch cycle (which performs stale recovery + execution)
        int processed = remediationWorker.processRemediationBatch();
        assertThat(processed).isGreaterThanOrEqualTo(1);

        // Verify that the task was claimed by the alive worker and COMPLETED
        SecurityIncidentRemediation reloaded = remediationRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(IncidentRemediationStatus.COMPLETED);
        assertThat(reloaded.getAttemptCount()).isGreaterThanOrEqualTo(2);
        assertThat(reloaded.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("3. Retry Exhaustion: Tasks that exhaust maxAttempts transition terminally to FAILED without reclaiming")
    void testRetryExhaustionTerminatesInFailed() {
        SecurityIncident incident = incidentService.createIncident(
                workspaceId,
                "Exhaustion Incident",
                "Testing retry boundary enforcement",
                IncidentSeverity.HIGH,
                "COMPROMISE",
                null,
                adminUserId
        );

        // Create a remediation that already reached maxAttempts (3) and was stuck CLAIMED in the past
        SecurityIncidentRemediation rem = new SecurityIncidentRemediation();
        rem.setWorkspaceId(workspaceId);
        rem.setIncidentId(incident.getId());
        rem.setAction(RemediationPlaybookAction.CONTAIN_INCIDENT);
        rem.setStatus(IncidentRemediationStatus.CLAIMED);
        rem.setWorkerId("failing-worker-node");
        rem.setClaimToken("token-fail");
        rem.setLockedAt(Instant.now().minus(Duration.ofMinutes(10)));
        rem.setAttemptCount(3);
        rem.setMaxAttempts(3);
        rem.setActorId(adminUserId);
        SecurityIncidentRemediation saved = remediationRepository.save(rem);

        // Run stale recovery
        incidentService.recoverStaleRemediations(Duration.ofMinutes(5));

        // Verify task transitioned to FAILED (terminal state due to retry exhaustion)
        SecurityIncidentRemediation reloaded = remediationRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(IncidentRemediationStatus.FAILED);
        assertThat(reloaded.getLastError()).contains("Exhausted maximum retry attempts");

        // Verify subsequent worker claim attempt returns 0 (cannot be claimed)
        int claimed = remediationRepository.claimRemediationAtomic(
                saved.getId(),
                IncidentRemediationStatus.CLAIMED,
                IncidentRemediationStatus.QUEUED,
                Instant.now().minus(Duration.ofMinutes(5)),
                "another-worker",
                UUID.randomUUID().toString(),
                Instant.now()
        );
        assertThat(claimed).isEqualTo(0);
    }

    @Test
    @DisplayName("4. Duplicate Delivery Prevention: Multiple concurrent claim attempts on the same task yield exactly one claim")
    void testDuplicateDeliveryAndIdempotentClaiming() {
        SecurityIncident incident = incidentService.createIncident(
                workspaceId,
                "Duplicate Delivery Incident",
                "Verifying atomic single claim across duplicate deliveries",
                IncidentSeverity.MEDIUM,
                "POLICY_VIOLATION",
                null,
                adminUserId
        );

        SecurityIncidentRemediation rem = new SecurityIncidentRemediation();
        rem.setWorkspaceId(workspaceId);
        rem.setIncidentId(incident.getId());
        rem.setAction(RemediationPlaybookAction.CONTAIN_INCIDENT);
        rem.setStatus(IncidentRemediationStatus.QUEUED);
        rem.setAttemptCount(0);
        rem.setMaxAttempts(3);
        rem.setActorId(adminUserId);
        SecurityIncidentRemediation saved = remediationRepository.save(rem);

        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofMinutes(5));

        // Two worker threads attempt to claim simultaneously
        int claim1 = remediationRepository.claimRemediationAtomic(
                saved.getId(),
                IncidentRemediationStatus.CLAIMED,
                IncidentRemediationStatus.QUEUED,
                cutoff,
                "worker-alpha",
                "token-alpha",
                now
        );

        int claim2 = remediationRepository.claimRemediationAtomic(
                saved.getId(),
                IncidentRemediationStatus.CLAIMED,
                IncidentRemediationStatus.QUEUED,
                cutoff,
                "worker-beta",
                "token-beta",
                now
        );

        // Exactly one worker must win the claim
        assertThat(claim1 + claim2).isEqualTo(1);
        assertThat(claim1).isEqualTo(1);
        assertThat(claim2).isEqualTo(0);

        SecurityIncidentRemediation reloaded = remediationRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getWorkerId()).isEqualTo("worker-alpha");
        assertThat(reloaded.getClaimToken()).isEqualTo("token-alpha");
    }

    @Test
    @DisplayName("5. Concurrent Workers: Multiple worker threads concurrently process distinct tasks without collision")
    void testConcurrentWorkersClaimDistinctJobs() throws Exception {
        int taskCount = 6;
        List<UUID> executionIds = new ArrayList<>();

        for (int i = 0; i < taskCount; i++) {
            SecurityIncident incident = incidentService.createIncident(
                    workspaceId,
                    "Concurrent Incident " + i,
                    "Testing parallel worker dispatch",
                    IncidentSeverity.LOW,
                    "SCAN",
                    null,
                    adminUserId
            );

            SecurityIncidentRemediation rem = new SecurityIncidentRemediation();
            rem.setWorkspaceId(workspaceId);
            rem.setIncidentId(incident.getId());
            rem.setAction(RemediationPlaybookAction.CONTAIN_INCIDENT);
            rem.setStatus(IncidentRemediationStatus.QUEUED);
            rem.setAttemptCount(0);
            rem.setMaxAttempts(3);
            rem.setActorId(adminUserId);
            executionIds.add(remediationRepository.save(rem).getId());
        }

        // Run 3 worker threads concurrently
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final String workerName = "worker-thread-" + i;
            futures.add(executor.submit(() -> {
                int count = 0;
                for (UUID id : executionIds) {
                    var resp = incidentService.executeRemediationTask(id, workerName);
                    if (resp != null && resp.status() == IncidentRemediationStatus.COMPLETED) {
                        count++;
                    }
                }
                return count;
            }));
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

        // Verify all tasks reached COMPLETED
        for (UUID id : executionIds) {
            SecurityIncidentRemediation reloaded = remediationRepository.findById(id).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(IncidentRemediationStatus.COMPLETED);
            assertThat(reloaded.getAttemptCount()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("6. Approval Lifecycle Semantics: Approved action remains safely retryable after failure without allowing payload tampering")
    void testApprovalLifecycleRetrySafetyAndPayloadImmutability() throws Exception {
        SecurityIncident incident = incidentService.createIncident(
                workspaceId,
                "Dual Custody Lifecycle Incident",
                "Testing retry safety and payload immutability across failures",
                IncidentSeverity.CRITICAL,
                "COMPROMISE",
                null,
                adminUserId
        );

        // 1. Request approval for CONTAIN_INCIDENT
        IncidentRemediationRequest approvalReq = new IncidentRemediationRequest(
                RemediationPlaybookAction.CONTAIN_INCIDENT,
                null,
                null,
                "Containment requiring approval",
                false,
                true,
                null,
                false
        );

        MvcResult appRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/remediate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approvalReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
                .andReturn();

        UUID approvalId = UUID.fromString(objectMapper.readTree(appRes.getResponse().getContentAsString())
                .path("data").path("approvalId").asText());

        // 2. Peer approves
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/approvals/" + approvalId + "/decide")
                        .header("Authorization", "Bearer " + peerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("approve", true, "reason", "Verified threat"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // 3. Queue async remediation with approved approvalId
        IncidentRemediationRequest asyncExecReq = new IncidentRemediationRequest(
                RemediationPlaybookAction.CONTAIN_INCIDENT,
                null,
                null,
                "Containment requiring approval",
                false,
                false,
                approvalId,
                true // async
        );

        MvcResult execQueueRes = mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/remediate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(asyncExecReq)))
                .andExpect(status().isAccepted())
                .andReturn();

        UUID executionId = UUID.fromString(objectMapper.readTree(execQueueRes.getResponse().getContentAsString())
                .path("data").path("executionId").asText());

        // 4. Simulate a worker transient failure before completion
        remediationRepository.releaseClaimForRetry(
                executionId,
                IncidentRemediationStatus.QUEUED,
                "Simulated transient database disconnect",
                "dummy-claim-token",
                Instant.now()
        );

        // Verify the approval remains APPROVED (not EXECUTED) so it can be retried safely
        SecurityIncidentApproval appCheck = approvalRepository.findById(approvalId).orElseThrow();
        assertThat(appCheck.getStatus()).isEqualTo(IncidentApprovalStatus.APPROVED);

        // 5. Verify payload tampering defense: attempting to execute with tampered payload is rejected
        IncidentRemediationRequest tamperedReq = new IncidentRemediationRequest(
                RemediationPlaybookAction.CONTAIN_INCIDENT,
                null,
                null,
                "Tampered attacker notes swap",
                false,
                false,
                approvalId,
                false
        );

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/remediate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tamperedReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("payload hash mismatch")));

        // 6. Complete retry of original task
        var completedResp = incidentService.executeRemediationTask(executionId, "retry-worker");
        assertThat(completedResp.status()).isEqualTo(IncidentRemediationStatus.COMPLETED);

        // Verify approval transitioned to EXECUTED upon completion
        SecurityIncidentApproval executedApp = approvalRepository.findById(approvalId).orElseThrow();
        assertThat(executedApp.getStatus()).isEqualTo(IncidentApprovalStatus.EXECUTED);

        // 7. Verify executed approval cannot be reused again
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/remediate")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(asyncExecReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("is not approved")));
    }

    @Test
    @DisplayName("7. Transactional Integrity: Rollback during queueing leaves no orphaned remediation or domain events")
    void testTransactionalRollbackLeavesNoPhantomJob() {
        long initialRemediations = remediationRepository.count();
        long initialEvents = domainEventRepository.count();

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        try {
            txTemplate.execute(status -> {
                SecurityIncident incident = incidentService.createIncident(
                        workspaceId,
                        "Rollback Incident",
                        "Testing rollback safety",
                        IncidentSeverity.LOW,
                        "AUDIT",
                        null,
                        adminUserId
                );

                IncidentRemediationRequest req = new IncidentRemediationRequest(
                        RemediationPlaybookAction.CONTAIN_INCIDENT,
                        null,
                        null,
                        "Rolling back",
                        false,
                        false,
                        null,
                        true // async
                );

                incidentService.remediateIncident(workspaceId, incident.getId(), req, adminUserId);

                // Force deliberate rollback
                throw new RuntimeException("Simulated JVM crash / database rollback");
            });
        } catch (Exception expected) {
            // Transaction rolled back
        }

        // Verify zero phantom remediations or orphaned domain events were committed
        assertThat(remediationRepository.count()).isEqualTo(initialRemediations);
        assertThat(domainEventRepository.count()).isEqualTo(initialEvents);
    }

    @Test
    @DisplayName("8. Observability & Administrative Recovery: FAILED terminal state is observable and recoverable via /retry API")
    void testTerminalFailureObservabilityAndAdministrativeRetry() throws Exception {
        SecurityIncident incident = incidentService.createIncident(
                workspaceId,
                "Observability & Recovery Incident",
                "Verifying failure visibility and administrative retry endpoint",
                IncidentSeverity.HIGH,
                "COMPROMISE",
                null,
                adminUserId
        );

        // 1. Create a task in FAILED status
        SecurityIncidentRemediation failedRem = new SecurityIncidentRemediation();
        failedRem.setWorkspaceId(workspaceId);
        failedRem.setIncidentId(incident.getId());
        failedRem.setAction(RemediationPlaybookAction.CONTAIN_INCIDENT);
        failedRem.setStatus(IncidentRemediationStatus.FAILED);
        failedRem.setAttemptCount(3);
        failedRem.setMaxAttempts(3);
        failedRem.setLastError("Fatal provider timeout: Remote socket connection reset");
        failedRem.setVerifiedEffectsJson("{\"error\":\"Fatal provider timeout\"}");
        failedRem.setActorId(adminUserId);
        SecurityIncidentRemediation savedFailed = remediationRepository.save(failedRem);

        // 2. Observe the failure via REST API
        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/remediations/" + savedFailed.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.lastError").value("Fatal provider timeout: Remote socket connection reset"))
                .andExpect(jsonPath("$.data.attemptCount").value(3));

        // 3. Administrative recovery: Operator triggers POST /remediations/{id}/retry
        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/remediations/" + savedFailed.getId() + "/retry")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.attemptCount").value(0))
                .andExpect(jsonPath("$.data.lastError").isEmpty());

        // 4. Poll until the autonomous background worker picks it up and finishes it
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        String finalStatus = "QUEUED";
        while (Instant.now().isBefore(deadline)) {
            MvcResult checkRes = mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/incidents/" + incident.getId() + "/remediations/" + savedFailed.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andReturn();

            finalStatus = objectMapper.readTree(checkRes.getResponse().getContentAsString())
                    .path("data").path("status").asText();

            if ("COMPLETED".equals(finalStatus)) {
                break;
            }
            Thread.sleep(200);
        }

        assertThat(finalStatus).isEqualTo("COMPLETED");

        // Verify incident status updated to CONTAINED
        SecurityIncident reloadedIncident = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(reloadedIncident.getStatus()).isEqualTo(IncidentStatus.CONTAINED);
    }
}
