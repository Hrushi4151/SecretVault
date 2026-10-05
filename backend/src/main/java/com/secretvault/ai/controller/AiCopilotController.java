package com.secretvault.ai.controller;

import com.secretvault.ai.dto.*;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    public AiCopilotController(
            AiCopilotService copilotService,
            AiDeploymentRcaService rcaService,
            AiSecurityAnalysisService analysisService,
            AiRecommendationEngine recommendationEngine,
            AiRemediationExecutionGateway executionGateway
    ) {
        this.copilotService = copilotService;
        this.rcaService = rcaService;
        this.analysisService = analysisService;
        this.recommendationEngine = recommendationEngine;
        this.executionGateway = executionGateway;
    }

    @PostMapping("/chat")
    @Operation(summary = "Submit a natural-language security inquiry to AI Copilot")
    public ResponseEntity<ApiResponse<AiChatResponse>> chat(
            @PathVariable UUID workspaceId,
            @RequestBody @Valid AiChatRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getId() : null;
        AiChatResponse response = copilotService.processInquiry(workspaceId, request, userId);
        return ResponseEntity.ok(ApiResponse.success(response, "AI Copilot analysis generated"));
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
