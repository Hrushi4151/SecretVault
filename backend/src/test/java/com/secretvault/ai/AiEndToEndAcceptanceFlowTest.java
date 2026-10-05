package com.secretvault.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.secretvault.ai.context.AiContextBuilder;
import com.secretvault.ai.domain.entity.AiInquiry;
import com.secretvault.ai.domain.entity.AiRcaReport;
import com.secretvault.ai.domain.entity.AiRemediationPlan;
import com.secretvault.ai.domain.entity.AiTokenBudget;
import com.secretvault.ai.domain.model.AiIntentType;
import com.secretvault.ai.domain.model.AiPlanStatus;
import com.secretvault.ai.domain.model.AiRiskLevel;
import com.secretvault.ai.domain.repository.AiInquiryRepository;
import com.secretvault.ai.domain.repository.AiRcaReportRepository;
import com.secretvault.ai.domain.repository.AiRemediationPlanRepository;
import com.secretvault.ai.domain.repository.AiTokenBudgetRepository;
import com.secretvault.ai.dto.*;
import com.secretvault.ai.provider.DeterministicOfflineLlmProvider;
import com.secretvault.ai.provider.LlmProviderRegistry;
import com.secretvault.ai.security.AiContextSanitizer;
import com.secretvault.ai.security.AiRateLimiterAndBudgetEnforcer;
import com.secretvault.ai.security.AiSafetyGuardrailValidator;
import com.secretvault.ai.service.AiCopilotService;
import com.secretvault.ai.service.AiDeploymentRcaService;
import com.secretvault.ai.service.AiRecommendationEngine;
import com.secretvault.ai.service.AiRemediationExecutionGateway;
import com.secretvault.audit.service.AuditService;
import com.secretvault.security.event.service.SecurityEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("AiEndToEndAcceptanceFlowTest - End-to-End Copilot, RCA, Four-Eyes, and Execution Lifecycle")
class AiEndToEndAcceptanceFlowTest {

    private ObjectMapper objectMapper;
    private AiInquiryRepository inquiryRepository;
    private AiTokenBudgetRepository budgetRepository;
    private AiRcaReportRepository rcaRepository;
    private AiRemediationPlanRepository planRepository;
    private AuditService auditService;
    private SecurityEventService securityEventService;

    private AiContextSanitizer sanitizer;
    private AiContextBuilder contextBuilder;
    private AiSafetyGuardrailValidator guardrailValidator;
    private AiRateLimiterAndBudgetEnforcer budgetEnforcer;
    private LlmProviderRegistry providerRegistry;

    private AiCopilotService copilotService;
    private AiDeploymentRcaService rcaService;
    private AiRecommendationEngine recommendationEngine;
    private AiRemediationExecutionGateway executionGateway;

    // In-memory mock storage
    private final Map<UUID, AiInquiry> inquiryDb = new HashMap<>();
    private final Map<UUID, AiRcaReport> rcaDb = new HashMap<>();
    private final Map<UUID, AiRemediationPlan> planDb = new HashMap<>();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        inquiryRepository = mock(AiInquiryRepository.class);
        budgetRepository = mock(AiTokenBudgetRepository.class);
        rcaRepository = mock(AiRcaReportRepository.class);
        planRepository = mock(AiRemediationPlanRepository.class);
        auditService = mock(AuditService.class);
        securityEventService = mock(SecurityEventService.class);

        // Wire in-memory persistence mocks
        when(inquiryRepository.save(any(AiInquiry.class))).thenAnswer(inv -> {
            AiInquiry entity = inv.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(Instant.now());
            }
            inquiryDb.put(entity.getId(), entity);
            return entity;
        });

        when(rcaRepository.save(any(AiRcaReport.class))).thenAnswer(inv -> {
            AiRcaReport entity = inv.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(Instant.now());
            }
            rcaDb.put(entity.getId(), entity);
            return entity;
        });

        when(rcaRepository.findByIdAndWorkspaceId(any(), any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(rcaDb.get(id));
        });

        when(planRepository.save(any(AiRemediationPlan.class))).thenAnswer(inv -> {
            AiRemediationPlan entity = inv.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(Instant.now());
            }
            planDb.put(entity.getId(), entity);
            return entity;
        });

        when(planRepository.findByIdAndWorkspaceId(any(), any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return Optional.ofNullable(planDb.get(id));
        });

        when(budgetRepository.findByWorkspaceId(any())).thenAnswer(inv -> {
            AiTokenBudget b = new AiTokenBudget();
            b.setWorkspaceId(inv.getArgument(0));
            b.setMinuteWindowStart(Instant.now());
            b.setMonthWindowStart(Instant.now());
            return Optional.of(b);
        });
        when(budgetRepository.save(any(AiTokenBudget.class))).thenAnswer(inv -> inv.getArgument(0));

        sanitizer = new AiContextSanitizer();
        contextBuilder = new AiContextBuilder(null, sanitizer, objectMapper);
        guardrailValidator = new AiSafetyGuardrailValidator(sanitizer);
        budgetEnforcer = new AiRateLimiterAndBudgetEnforcer(budgetRepository);

        DeterministicOfflineLlmProvider offlineProvider = new DeterministicOfflineLlmProvider();
        providerRegistry = new LlmProviderRegistry(offlineProvider, "DETERMINISTIC_OFFLINE");

        copilotService = new AiCopilotService(
                inquiryRepository,
                budgetRepository,
                contextBuilder,
                sanitizer,
                budgetEnforcer,
                guardrailValidator,
                providerRegistry,
                auditService,
                securityEventService,
                objectMapper
        );

        rcaService = new AiDeploymentRcaService(
                rcaRepository,
                planRepository,
                sanitizer,
                auditService,
                securityEventService,
                objectMapper
        );

        recommendationEngine = new AiRecommendationEngine(
                planRepository,
                auditService,
                securityEventService,
                objectMapper
        );

        executionGateway = new AiRemediationExecutionGateway(
                planRepository,
                recommendationEngine,
                auditService,
                securityEventService,
                objectMapper
        );
    }

    @Test
    @DisplayName("End-to-End Acceptance: Chat -> RCA -> Plan Gen -> 4-Eyes Approval -> Execution -> Feedback")
    void testFullEndToEndLifecycle() {
        UUID workspaceId = UUID.randomUUID();
        UUID operatorId = UUID.randomUUID();
        UUID approverOne = UUID.randomUUID();
        UUID approverTwo = UUID.randomUUID();

        // -------------------------------------------------------------
        // Step 1: Natural Language Inquiry to Copilot
        // -------------------------------------------------------------
        AiChatRequest chatRequest = new AiChatRequest(
                "Why did payments-worker fail during deployment?",
                "DEPLOYMENT_RCA",
                null,
                null,
                null,
                "DEPLOYMENT",
                "dep-pay-worker-01",
                "Pod crashed with exit code 1"
        );

        AiChatResponse chatResponse = copilotService.processInquiry(workspaceId, chatRequest, operatorId);

        assertNotNull(chatResponse);
        assertEquals("DEPLOYMENT_RCA", chatResponse.intentType());
        assertTrue(chatResponse.confidenceScore() >= 0.90);
        assertTrue(chatResponse.responseText().contains("Root Cause Identified"));
        assertFalse(chatResponse.sanitizedTelemetryEvidence().isEmpty());
        assertNotNull(chatResponse.conversationId());

        // -------------------------------------------------------------
        // Step 2: Generate Formal RCA Report and Linked Remediation Plan
        // -------------------------------------------------------------
        AiRcaReportDto rcaReport = rcaService.analyzeFailure(
                workspaceId,
                "DEPLOYMENT",
                "dep-pay-worker-01",
                "Pod crashed with exit code 1: container credential mismatch",
                operatorId
        );

        assertNotNull(rcaReport);
        assertNotNull(rcaReport.id());
        assertEquals("dep-pay-worker-01", rcaReport.targetId());
        assertTrue(rcaReport.confidenceScore() >= 0.90);

        // -------------------------------------------------------------
        // Step 3: Generate High-Risk Remediation Plan
        // -------------------------------------------------------------
        AiPlanGenerateRequest planGenReq = new AiPlanGenerateRequest(
                "Reconcile Payments Worker Secret Drift",
                null,
                rcaReport.id(),
                "DEPLOYMENT",
                "dep-pay-worker-01",
                AiRiskLevel.CRITICAL,
                true,
                true
        );

        AiRemediationPlanDto initialPlan = recommendationEngine.generatePlan(workspaceId, planGenReq, operatorId);
        assertNotNull(initialPlan);
        assertEquals(AiPlanStatus.PENDING_APPROVAL, initialPlan.status());
        assertTrue(initialPlan.requiresFourEyes(), "Critical risk plans must enforce Four-Eyes requirement");
        assertTrue(initialPlan.requiresStepUp(), "Critical risk plans must require Step-Up MFA");

        // -------------------------------------------------------------
        // Step 4: Step-Up Rejection Check before Approval
        // -------------------------------------------------------------
        UUID planId = initialPlan.id();
        assertThrows(IllegalStateException.class, () ->
                executionGateway.executePlan(workspaceId, planId, new ExecutePlanRequest(false, "Execute unapproved", "stepup-mfa-valid-token"), operatorId),
                "Executing unapproved plan must fail"
        );

        // -------------------------------------------------------------
        // Step 5: Four-Eyes Approval Flow (Approver 1 then Approver 2)
        // -------------------------------------------------------------
        // First approval by Approver 1
        AiRemediationPlanDto afterFirstApproval = recommendationEngine.approvePlan(workspaceId, planId, approverOne);
        assertEquals(AiPlanStatus.PENDING_APPROVAL, afterFirstApproval.status(), "Still pending until second distinct approver acts");
        assertEquals(approverOne, afterFirstApproval.reviewedByUserId());
        assertNull(afterFirstApproval.secondReviewedByUserId());

        // Same approver attempting second approval MUST be rejected (Separation of Duties)
        assertThrows(IllegalStateException.class, () ->
                recommendationEngine.approvePlan(workspaceId, planId, approverOne)
        );

        // Distinct approver 2 approves
        AiRemediationPlanDto afterSecondApproval = recommendationEngine.approvePlan(workspaceId, planId, approverTwo);
        assertEquals(AiPlanStatus.APPROVED, afterSecondApproval.status());
        assertEquals(approverOne, afterSecondApproval.reviewedByUserId());
        assertEquals(approverTwo, afterSecondApproval.secondReviewedByUserId());
        assertNotNull(afterSecondApproval.planFingerprint(), "Plan cryptographic fingerprint seal must be established upon final approval");

        // -------------------------------------------------------------
        // Step 6: Dry Run Preview Execution (Non-Destructive)
        // -------------------------------------------------------------
        AiRemediationPlanDto dryRunResult = executionGateway.executePlan(
                workspaceId,
                planId,
                new ExecutePlanRequest(true, "Dry run verification"),
                operatorId
        );
        assertEquals(AiPlanStatus.APPROVED, dryRunResult.status(), "Dry run must NOT transition plan to EXECUTED");

        // -------------------------------------------------------------
        // Step 7: Step-Up MFA Guard on Authoritative Execution
        // -------------------------------------------------------------
        // Missing Step-Up Token throws SecurityException
        assertThrows(SecurityException.class, () ->
                executionGateway.executePlan(workspaceId, planId, new ExecutePlanRequest(false, "No MFA", null), operatorId)
        );

        // -------------------------------------------------------------
        // Step 8: Authoritative Execution with Step-Up MFA & Fingerprint Seal
        // -------------------------------------------------------------
        AiRemediationPlanDto executedPlan = executionGateway.executePlan(
                workspaceId,
                planId,
                new ExecutePlanRequest(false, "Confirmed emergency reconciliation", "mfa-stepup-ok-pass"),
                operatorId
        );

        assertEquals(AiPlanStatus.EXECUTED, executedPlan.status());
        assertNotNull(executedPlan.executedAt());

        // -------------------------------------------------------------
        // Step 9: Replay Execution Guard (Idempotency)
        // -------------------------------------------------------------
        AiRemediationPlanDto replayResult = executionGateway.executePlan(
                workspaceId,
                planId,
                new ExecutePlanRequest(false, "Replay execution", "mfa-stepup-ok-pass"),
                operatorId
        );
        assertEquals(AiPlanStatus.EXECUTED, replayResult.status());
        assertEquals(executedPlan.executedAt(), replayResult.executedAt(), "Idempotent replay must return existing execution state without re-running");

        // -------------------------------------------------------------
        // Step 10: Feedback Loop
        // -------------------------------------------------------------
        AiRemediationPlanDto feedbackRecorded = recommendationEngine.recordFeedback(
                workspaceId,
                planId,
                5,
                "Flawless zero-downtime rollover. Pod recovered in 12 seconds."
        );

        assertEquals(5, feedbackRecorded.feedbackRating());
        assertEquals("Flawless zero-downtime rollover. Pod recovered in 12 seconds.", feedbackRecorded.feedbackComment());
    }
}
