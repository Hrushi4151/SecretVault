package com.secretvault.security.controller;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.common.dto.PageResponse;
import com.secretvault.security.engine.SecurityIntelligenceEngine;
import com.secretvault.security.event.dto.RecordSecurityEventRequest;
import com.secretvault.security.event.dto.SecurityEventResponse;
import com.secretvault.security.event.model.SecurityEventOutcome;
import com.secretvault.security.event.model.SecurityEventSeverity;
import com.secretvault.security.event.model.SecurityEventType;
import com.secretvault.security.event.service.SecurityEventService;
import com.secretvault.security.finding.dto.AssignFindingRequest;
import com.secretvault.security.finding.dto.SecurityFindingResponse;
import com.secretvault.security.finding.dto.UpdateFindingStatusRequest;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.security.finding.model.FindingStatus;
import com.secretvault.security.finding.service.SecurityFindingService;
import com.secretvault.security.posture.dto.SecurityOverviewResponse;
import com.secretvault.security.posture.dto.SecurityPostureResponse;
import com.secretvault.security.posture.dto.SecurityTimelineEventResponse;
import com.secretvault.security.posture.service.SecurityPostureService;
import com.secretvault.security.risk.service.RiskAssessmentEngine;
import com.secretvault.security.util.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/security")
@Tag(name = "Security Center & Intelligence", description = "Endpoints for Security Posture, Events, Findings, and Intelligence Analysis")
public class SecurityCenterController {

    private final SecurityFindingService findingService;
    private final SecurityEventService eventService;
    private final SecurityPostureService postureService;
    private final SecurityIntelligenceEngine intelligenceEngine;
    private final RiskAssessmentEngine riskAssessmentEngine;
    private final EffectiveAccessService effectiveAccessService;

    private static final Set<String> ALLOWED_FINDING_SORT_FIELDS = Set.of(
            "lastObservedAt", "firstObservedAt", "severity", "status", "category", "occurrenceCount", "createdAt"
    );

    private static final Set<String> ALLOWED_EVENT_SORT_FIELDS = Set.of(
            "timestamp", "eventType", "severity", "outcome"
    );

    public SecurityCenterController(
            SecurityFindingService findingService,
            SecurityEventService eventService,
            SecurityPostureService postureService,
            SecurityIntelligenceEngine intelligenceEngine,
            RiskAssessmentEngine riskAssessmentEngine,
            EffectiveAccessService effectiveAccessService
    ) {
        this.findingService = findingService;
        this.eventService = eventService;
        this.postureService = postureService;
        this.intelligenceEngine = intelligenceEngine;
        this.riskAssessmentEngine = riskAssessmentEngine;
        this.effectiveAccessService = effectiveAccessService;
    }

    @GetMapping("/overview")
    @Operation(summary = "Get executive security overview", description = "Returns high-level posture metrics, top risk findings, and recent timeline")
    public ResponseEntity<ApiResponse<SecurityOverviewResponse>> getOverview(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SecurityOverviewResponse overview = postureService.getOverview(workspaceId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(overview));
    }

    @GetMapping("/posture")
    @Operation(summary = "Get workspace security posture", description = "Calculates deterministic risk score (0-100), risk factors, and access hygiene metrics")
    public ResponseEntity<ApiResponse<SecurityPostureResponse>> getPosture(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SecurityPostureResponse posture = postureService.getPosture(workspaceId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(posture));
    }

    @GetMapping("/risk")
    @Operation(summary = "Get detailed risk assessment", description = "Calculates explainable risk score and contributing weighted factors")
    public ResponseEntity<ApiResponse<RiskAssessmentEngine.RiskAssessmentResult>> getRiskBreakdown(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(
                workspaceId, null, null, null,
                AccessPermission.SECURITY_VIEW, principal.getId()
        );
        RiskAssessmentEngine.RiskAssessmentResult result = riskAssessmentEngine.computeRisk(workspaceId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/events")
    @Operation(summary = "List security events", description = "Paginated stream of sanitized security events with server-side sort whitelist and filters")
    public ResponseEntity<ApiResponse<PageResponse<SecurityEventResponse>>> listEvents(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID environmentId,
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) SecurityEventType eventType,
            @RequestParam(required = false) SecurityEventSeverity severity,
            @RequestParam(required = false) SecurityEventOutcome outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Pageable safePageable = PaginationUtils.sanitizePageable(
                pageable, ALLOWED_EVENT_SORT_FIELDS, "timestamp", Sort.Direction.DESC
        );

        Page<SecurityEventResponse> page = eventService.getEvents(
                workspaceId, projectId, environmentId, actorUserId,
                eventType, severity, outcome, from, to, principal.getId(), safePageable
        );

        return ResponseEntity.ok(ApiResponse.success(toPageResponse(page)));
    }

    @PostMapping("/events")
    @Operation(summary = "Record security event", description = "Explicitly records a sanitized security event")
    public ResponseEntity<ApiResponse<SecurityEventResponse>> recordEvent(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody RecordSecurityEventRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(
                workspaceId, null, null, null,
                AccessPermission.SECURITY_MANAGE, principal.getId()
        );
        SecurityEventResponse response = eventService.recordEvent(workspaceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Security event recorded"));
    }

    @GetMapping("/findings")
    @Operation(summary = "List security findings", description = "Paginated list of deduplicated security findings with filters and sort whitelisting")
    public ResponseEntity<ApiResponse<PageResponse<SecurityFindingResponse>>> listFindings(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) FindingStatus status,
            @RequestParam(required = false) FindingSeverity severity,
            @RequestParam(required = false) FindingCategory category,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID environmentId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "lastObservedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Pageable safePageable = PaginationUtils.sanitizePageable(
                pageable, ALLOWED_FINDING_SORT_FIELDS, "lastObservedAt", Sort.Direction.DESC
        );

        Page<SecurityFindingResponse> page = findingService.getFindings(
                workspaceId, status, severity, category, projectId, environmentId, search,
                principal.getId(), safePageable
        );

        return ResponseEntity.ok(ApiResponse.success(toPageResponse(page)));
    }

    @GetMapping("/findings/{findingId}")
    @Operation(summary = "Get security finding details", description = "Detailed finding view with structured evidence and remediation guidance")
    public ResponseEntity<ApiResponse<SecurityFindingResponse>> getFindingById(
            @PathVariable UUID workspaceId,
            @PathVariable UUID findingId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SecurityFindingResponse response = findingService.getFindingDetails(workspaceId, findingId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/findings/{findingId}")
    @Operation(summary = "Update finding status", description = "Transitions finding status (ACKNOWLEDGED, IN_PROGRESS, RESOLVED, FALSE_POSITIVE) with state transition validation")
    public ResponseEntity<ApiResponse<SecurityFindingResponse>> updateFindingStatus(
            @PathVariable UUID workspaceId,
            @PathVariable UUID findingId,
            @Valid @RequestBody UpdateFindingStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SecurityFindingResponse response = findingService.updateStatus(workspaceId, findingId, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Finding status updated successfully"));
    }

    @PatchMapping("/findings/{findingId}/assign")
    @Operation(summary = "Assign finding", description = "Assigns an investigator to a security finding")
    public ResponseEntity<ApiResponse<SecurityFindingResponse>> assignFinding(
            @PathVariable UUID workspaceId,
            @PathVariable UUID findingId,
            @RequestBody AssignFindingRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SecurityFindingResponse response = findingService.assignFinding(workspaceId, findingId, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Finding assigned successfully"));
    }

    @GetMapping("/timeline")
    @Operation(summary = "Get unified security timeline", description = "Chronological merged stream of security events and audit actions")
    public ResponseEntity<ApiResponse<List<SecurityTimelineEventResponse>>> getTimeline(
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<SecurityTimelineEventResponse> timeline = postureService.getTimeline(workspaceId, principal.getId(), limit);
        return ResponseEntity.ok(ApiResponse.success(timeline));
    }

    @PostMapping("/analyze")
    @Operation(summary = "Trigger on-demand security intelligence analysis", description = "Executes full deterministic rule evaluation and updates workspace posture")
    public ResponseEntity<ApiResponse<SecurityIntelligenceEngine.SecurityAnalysisResult>> triggerAnalysis(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(
                workspaceId, null, null, null,
                AccessPermission.SECURITY_MANAGE, principal.getId()
        );
        SecurityIntelligenceEngine.SecurityAnalysisResult result = intelligenceEngine.analyzeWorkspace(
                workspaceId, principal.getId()
        );
        return ResponseEntity.ok(ApiResponse.success(result, "Security analysis completed successfully"));
    }

    private <T> PageResponse<T> toPageResponse(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
