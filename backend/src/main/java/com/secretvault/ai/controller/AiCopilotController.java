package com.secretvault.ai.controller;

import com.secretvault.ai.domain.entity.AiConversation;
import com.secretvault.ai.domain.entity.AiMessage;
import com.secretvault.ai.dto.*;
import com.secretvault.ai.service.AiConversationService;
import com.secretvault.ai.service.AiCopilotService;
import com.secretvault.ai.service.AiDeploymentRcaService;
import com.secretvault.ai.service.AiRecommendationEngine;
import com.secretvault.ai.service.AiRemediationExecutionGateway;
import com.secretvault.ai.service.AiSecurityAnalysisService;
import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/ai")
@Tag(name = "AI Intelligence Copilot", description = "Endpoints for DevSecOps AI copilot, failure RCA, posture forecasting, and remediation workbench")
@SecurityRequirement(name = "BearerAuth")
public class AiCopilotController {

    private final AiCopilotService copilotService;
    private final AiDeploymentRcaService rcaService;
    private final AiSecurityAnalysisService analysisService;
    private final AiRecommendationEngine recommendationEngine;
    private final AiRemediationExecutionGateway executionGateway;
    private final AiConversationService conversationService;

    public AiCopilotController(
            AiCopilotService copilotService,
            AiDeploymentRcaService rcaService,
            AiSecurityAnalysisService analysisService,
            AiRecommendationEngine recommendationEngine,
            AiRemediationExecutionGateway executionGateway,
            @Autowired(required = false) AiConversationService conversationService
    ) {
        this.copilotService = copilotService;
        this.rcaService = rcaService;
        this.analysisService = analysisService;
        this.recommendationEngine = recommendationEngine;
        this.executionGateway = executionGateway;
        this.conversationService = conversationService;
    }

    @PostMapping("/chat")
    @Operation(summary = "Submit a natural-language security inquiry to AI Copilot")
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(
            @PathVariable UUID workspaceId,
            @RequestBody @Valid AiChatRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getId() : null;
        String userRole = "DEVELOPER";
        if (principal != null && principal.getAuthorities() != null && !principal.getAuthorities().isEmpty()) {
            userRole = principal.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        }
        Set<String> permissions = principal != null && principal.getAuthorities() != null
                ? principal.getAuthorities().stream().map(Object::toString).collect(java.util.stream.Collectors.toSet())
                : Set.of();

        AiChatResponse response = copilotService.processInquiry(workspaceId, request, userId, userRole, permissions);
        return ResponseEntity.ok(ApiResponse.success(response, "AI Copilot analysis generated"));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream live AI Copilot reasoning and tool execution progress over SSE")
    public SseEmitter chatStream(
            @PathVariable UUID workspaceId,
            @RequestBody @Valid AiChatRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getId() : null;
        String userRole = "DEVELOPER";
        if (principal != null && principal.getAuthorities() != null && !principal.getAuthorities().isEmpty()) {
            userRole = principal.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        }
        Set<String> permissions = principal != null && principal.getAuthorities() != null
                ? principal.getAuthorities().stream().map(Object::toString).collect(java.util.stream.Collectors.toSet())
                : Set.of();

        SseEmitter emitter = new SseEmitter(60_000L);
        copilotService.processInquiryStream(workspaceId, request, userId, userRole, permissions, emitter);
        return emitter;
    }

    @GetMapping("/conversations")
    @Operation(summary = "List persistent conversations in the authorized workspace")
    public ResponseEntity<ApiResponse<Page<AiConversation>>> listConversations(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID userId = principal != null ? principal.getId() : null;
        Page<AiConversation> page = conversationService != null
                ? conversationService.listConversations(workspaceId, userId, pageable)
                : Page.empty(pageable);
        return ResponseEntity.ok(ApiResponse.success(page, "Conversations retrieved"));
    }

    @PostMapping("/conversations")
    @Operation(summary = "Create a new conversation session")
    public ResponseEntity<ApiResponse<AiConversation>> createConversation(
            @PathVariable UUID workspaceId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getId() : null;
        String title = body.getOrDefault("title", "New Inquiry");
        String scopeType = body.getOrDefault("scopeType", "WORKSPACE");
        AiConversation created = conversationService != null
                ? conversationService.createConversation(workspaceId, userId, title, scopeType, null)
                : new AiConversation();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created, "Conversation created"));
    }

    @GetMapping("/conversations/{conversationId}")
    @Operation(summary = "Get conversation by ID")
    public ResponseEntity<ApiResponse<AiConversation>> getConversation(
            @PathVariable UUID workspaceId,
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getId() : null;
        AiConversation conv = conversationService != null
                ? conversationService.getConversation(conversationId, workspaceId, userId)
                : null;
        return ResponseEntity.ok(ApiResponse.success(conv, "Conversation retrieved"));
    }

    @PatchMapping("/conversations/{conversationId}")
    @Operation(summary = "Rename conversation")
    public ResponseEntity<ApiResponse<AiConversation>> renameConversation(
            @PathVariable UUID workspaceId,
            @PathVariable UUID conversationId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getId() : null;
        String newTitle = body.getOrDefault("title", "Conversation");
        AiConversation updated = conversationService != null
                ? conversationService.renameConversation(conversationId, workspaceId, userId, newTitle)
                : null;
        return ResponseEntity.ok(ApiResponse.success(updated, "Conversation renamed"));
    }

    @DeleteMapping("/conversations/{conversationId}")
    @Operation(summary = "Delete conversation")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(
            @PathVariable UUID workspaceId,
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getId() : null;
        if (conversationService != null) {
            conversationService.deleteConversation(conversationId, workspaceId, userId);
        }
        return ResponseEntity.ok(ApiResponse.success(null, "Conversation deleted"));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "Get messages for a conversation")
    public ResponseEntity<ApiResponse<List<AiMessage>>> getMessages(
            @PathVariable UUID workspaceId,
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getId() : null;
        List<AiMessage> messages = conversationService != null
                ? conversationService.getMessages(conversationId, workspaceId, userId)
                : List.of();
        return ResponseEntity.ok(ApiResponse.success(messages, "Messages retrieved"));
    }

    @GetMapping("/providers")
    @Operation(summary = "List registered AI model providers and availability")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getProviders(
            @PathVariable UUID workspaceId
    ) {
        List<Map<String, Object>> providers = copilotService.getProviders();
        return ResponseEntity.ok(ApiResponse.success(providers, "Providers retrieved"));
    }

    @GetMapping({"/chat/history", "/inquiries"})
    @Operation(summary = "Get historical AI inquiries for workspace")
    public ResponseEntity<ApiResponse<Page<AiChatResponse>>> getHistory(
            @PathVariable UUID workspaceId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<AiChatResponse> history = copilotService.getInquiryHistory(workspaceId, pageable);
        return ResponseEntity.ok(ApiResponse.success(history, "Inquiry history retrieved"));
    }

    @PostMapping("/rca")
    @Operation(summary = "Trigger Root Cause Analysis (RCA) on a failed deployment, sync job, or rotation")
    public ResponseEntity<ApiResponse<AiRcaReportDto>> triggerRca(
            @PathVariable UUID workspaceId,
            @RequestBody @Valid AiRcaRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getId() : null;
        AiRcaReportDto report = rcaService.analyzeFailure(
                workspaceId,
                request.targetType(),
                request.targetId(),
                request.contextHint(),
                userId
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(report, "Root cause analysis report generated"));
    }

    @GetMapping("/rca")
    @Operation(summary = "Get RCA reports for a specific target")
    public ResponseEntity<ApiResponse<List<AiRcaReportDto>>> getRcaReports(
            @PathVariable UUID workspaceId,
            @RequestParam String targetType,
            @RequestParam String targetId
    ) {
        List<AiRcaReportDto> reports = rcaService.getReportsForTarget(workspaceId, targetType, targetId);
        return ResponseEntity.ok(ApiResponse.success(reports, "RCA reports retrieved"));
    }

    @GetMapping("/rca/{reportId}")
    @Operation(summary = "Get specific Root Cause Analysis (RCA) report by ID")
    public ResponseEntity<ApiResponse<AiRcaReportDto>> getRcaReport(
            @PathVariable UUID workspaceId,
            @PathVariable UUID reportId
    ) {
        AiRcaReportDto report = rcaService.getReport(workspaceId, reportId);
        return ResponseEntity.ok(ApiResponse.success(report, "RCA report retrieved"));
    }

    @GetMapping({"/posture-forecast", "/posture/forecast"})
    @Operation(summary = "Get predictive security posture forecast and drift decay trajectory")
    public ResponseEntity<ApiResponse<AiPostureForecastResponse>> getPostureForecast(
            @PathVariable UUID workspaceId
    ) {
        AiPostureForecastResponse forecast = analysisService.getPostureForecast(workspaceId);
        return ResponseEntity.ok(ApiResponse.success(forecast, "Posture forecast calculated"));
    }

    @GetMapping("/plans")
    @Operation(summary = "List reviewable AI remediation plans")
    public ResponseEntity<ApiResponse<Page<AiRemediationPlanDto>>> getPlans(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) Boolean pendingOnly,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<AiRemediationPlanDto> plans = (pendingOnly != null && pendingOnly)
                ? recommendationEngine.getPendingPlans(workspaceId, pageable)
                : recommendationEngine.getPlans(workspaceId, pageable);
        return ResponseEntity.ok(ApiResponse.success(plans, "Remediation plans retrieved"));
    }

    @PostMapping("/plans/generate")
    @Operation(summary = "Generate a new reviewable AI remediation plan")
    public ResponseEntity<ApiResponse<AiRemediationPlanDto>> generatePlan(
            @PathVariable UUID workspaceId,
            @RequestBody(required = false) AiPlanGenerateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getId() : null;
        AiRemediationPlanDto plan = recommendationEngine.generatePlan(workspaceId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(plan, "Remediation plan generated"));
    }

    @GetMapping("/plans/{planId}")
    @Operation(summary = "Get specific remediation plan with telemetry diff and blast radius")
    public ResponseEntity<ApiResponse<AiRemediationPlanDto>> getPlan(
            @PathVariable UUID workspaceId,
            @PathVariable UUID planId
    ) {
        AiRemediationPlanDto plan = recommendationEngine.getPlan(workspaceId, planId);
        return ResponseEntity.ok(ApiResponse.success(plan, "Remediation plan retrieved"));
    }

    @PostMapping("/plans/{planId}/approve")
    @Operation(summary = "Approve a reviewable remediation plan")
    public ResponseEntity<ApiResponse<AiRemediationPlanDto>> approvePlan(
            @PathVariable UUID workspaceId,
            @PathVariable UUID planId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getId() : null;
        AiRemediationPlanDto approved = recommendationEngine.approvePlan(workspaceId, planId, userId);
        return ResponseEntity.ok(ApiResponse.success(approved, "Remediation plan approved"));
    }

    @PostMapping("/plans/{planId}/reject")
    @Operation(summary = "Reject a remediation plan")
    public ResponseEntity<ApiResponse<AiRemediationPlanDto>> rejectPlan(
            @PathVariable UUID workspaceId,
            @PathVariable UUID planId,
            @RequestParam(defaultValue = "Rejected by reviewer") String reason,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getId() : null;
        AiRemediationPlanDto rejected = recommendationEngine.rejectPlan(workspaceId, planId, userId, reason);
        return ResponseEntity.ok(ApiResponse.success(rejected, "Remediation plan rejected"));
    }

    @PostMapping("/plans/{planId}/execute")
    @Operation(summary = "Execute an approved remediation plan (or simulate via dryRun)")
    public ResponseEntity<ApiResponse<AiRemediationPlanDto>> executePlan(
            @PathVariable UUID workspaceId,
            @PathVariable UUID planId,
            @RequestBody(required = false) ExecutePlanRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getId() : null;
        AiRemediationPlanDto executed = executionGateway.executePlan(workspaceId, planId, request, userId);
        return ResponseEntity.ok(ApiResponse.success(executed, "Remediation plan executed"));
    }

    @PostMapping("/plans/{planId}/feedback")
    @Operation(summary = "Submit human feedback on an AI remediation proposal")
    public ResponseEntity<ApiResponse<AiRemediationPlanDto>> submitFeedback(
            @PathVariable UUID workspaceId,
            @PathVariable UUID planId,
            @RequestBody @Valid PlanFeedbackRequest request
    ) {
        AiRemediationPlanDto updated = recommendationEngine.recordFeedback(workspaceId, planId, request.rating(), request.comment());
        return ResponseEntity.ok(ApiResponse.success(updated, "Feedback recorded"));
    }

    @GetMapping({"/health", "/token-budget"})
    @Operation(summary = "Check AI model health, active provider, and token quotas")
    public ResponseEntity<ApiResponse<AiModelHealthResponse>> getHealth(
            @PathVariable UUID workspaceId
    ) {
        AiModelHealthResponse health = copilotService.getModelHealth(workspaceId);
        return ResponseEntity.ok(ApiResponse.success(health, "AI Copilot health checked"));
    }
}
