package com.secretvault.incident.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.incident.dto.IncidentDtos.IncidentRemediationRequest;
import com.secretvault.incident.dto.IncidentDtos.IncidentRemediationResult;
import com.secretvault.incident.entity.IncidentSeverity;
import com.secretvault.incident.entity.IncidentStatus;
import com.secretvault.incident.entity.SecurityIncident;
import com.secretvault.incident.entity.SecurityIncidentEvent;
import com.secretvault.incident.service.SecurityIncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/incidents")
@Tag(name = "Security Incidents", description = "Security incident management, triage, and automated compromise response operations")
@SecurityRequirement(name = "BearerAuth")
public class SecurityIncidentController {

    private final SecurityIncidentService incidentService;

    public SecurityIncidentController(SecurityIncidentService incidentService) {
        this.incidentService = incidentService;
    }

    public record CreateIncidentRequest(
            @NotBlank String title,
            String description,
            IncidentSeverity severity,
            String category,
            UUID sourceEventId
    ) {}

    public record UpdateIncidentStatusRequest(
            @NotNull IncidentStatus status,
            String resolutionSummary
    ) {}

    @PostMapping
    @Operation(summary = "Create security incident")
    public ResponseEntity<ApiResponse<SecurityIncident>> createIncident(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateIncidentRequest req,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        SecurityIncident incident = incidentService.createIncident(
                workspaceId,
                req.title(),
                req.description(),
                req.severity(),
                req.category(),
                req.sourceEventId(),
                actorId
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(incident, "Security incident created"));
    }

    @GetMapping
    @Operation(summary = "List security incidents in workspace")
    public ResponseEntity<ApiResponse<Page<SecurityIncident>>> listIncidents(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentSeverity severity,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        Page<SecurityIncident> incidents = incidentService.listIncidents(workspaceId, status, severity, pageable, actorId);
        return ResponseEntity.ok(ApiResponse.success(incidents));
    }

    @GetMapping("/{incidentId}")
    @Operation(summary = "Get security incident details")
    public ResponseEntity<ApiResponse<SecurityIncident>> getIncident(
            @PathVariable UUID workspaceId,
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        SecurityIncident incident = incidentService.getIncident(workspaceId, incidentId, actorId);
        return ResponseEntity.ok(ApiResponse.success(incident));
    }

    @PutMapping("/{incidentId}/status")
    @Operation(summary = "Update incident triage or resolution status")
    public ResponseEntity<ApiResponse<SecurityIncident>> updateStatus(
            @PathVariable UUID workspaceId,
            @PathVariable UUID incidentId,
            @Valid @RequestBody UpdateIncidentStatusRequest req,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        SecurityIncident updated = incidentService.updateIncidentStatus(
                workspaceId,
                incidentId,
                req.status(),
                req.resolutionSummary(),
                actorId
        );
        return ResponseEntity.ok(ApiResponse.success(updated, "Incident status updated"));
    }

    @PostMapping("/{incidentId}/remediate")
    @Operation(summary = "Execute remediation playbook or preview dry-run for an incident")
    public ResponseEntity<ApiResponse<IncidentRemediationResult>> remediateIncident(
            @PathVariable UUID workspaceId,
            @PathVariable UUID incidentId,
            @Valid @RequestBody IncidentRemediationRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        IncidentRemediationResult result = incidentService.remediateIncident(
                workspaceId,
                incidentId,
                request,
                actorId
        );
        if ("QUEUED".equals(result.status())) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.success(result, "Incident remediation queued for asynchronous processing"));
        }
        return ResponseEntity.ok(ApiResponse.success(result, "Incident remediation processed"));
    }

    @GetMapping("/{incidentId}/events")
    @Operation(summary = "Get correlated domain events for an incident")
    public ResponseEntity<ApiResponse<List<SecurityIncidentEvent>>> getIncidentEvents(
            @PathVariable UUID workspaceId,
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        List<SecurityIncidentEvent> events = incidentService.getIncidentEvents(workspaceId, incidentId, actorId);
        return ResponseEntity.ok(ApiResponse.success(events));
    }

    @GetMapping("/{incidentId}/approvals")
    @Operation(summary = "List remediation approval requests for an incident")
    public ResponseEntity<ApiResponse<List<com.secretvault.incident.dto.IncidentDtos.IncidentApprovalResponse>>> listApprovals(
            @PathVariable UUID workspaceId,
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        var approvals = incidentService.listIncidentApprovals(workspaceId, incidentId, actorId);
        return ResponseEntity.ok(ApiResponse.success(approvals));
    }

    @GetMapping("/{incidentId}/approvals/{approvalId}")
    @Operation(summary = "Get remediation approval request details")
    public ResponseEntity<ApiResponse<com.secretvault.incident.dto.IncidentDtos.IncidentApprovalResponse>> getApproval(
            @PathVariable UUID workspaceId,
            @PathVariable UUID incidentId,
            @PathVariable UUID approvalId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        var approval = incidentService.getIncidentApproval(workspaceId, incidentId, approvalId, actorId);
        return ResponseEntity.ok(ApiResponse.success(approval));
    }

    @PostMapping("/{incidentId}/approvals/{approvalId}/decide")
    @Operation(summary = "Decide remediation approval request (two-person rule)")
    public ResponseEntity<ApiResponse<com.secretvault.incident.dto.IncidentDtos.IncidentApprovalResponse>> decideApproval(
            @PathVariable UUID workspaceId,
            @PathVariable UUID incidentId,
            @PathVariable UUID approvalId,
            @Valid @RequestBody com.secretvault.incident.dto.IncidentDtos.IncidentApprovalDecisionRequest req,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        var approval = incidentService.decideRemediationApproval(
                workspaceId,
                incidentId,
                approvalId,
                req.approve(),
                req.reason(),
                actorId
        );
        return ResponseEntity.ok(ApiResponse.success(
                com.secretvault.incident.dto.IncidentDtos.IncidentApprovalResponse.fromEntity(approval),
                "Approval decision recorded"
        ));
    }

    @GetMapping("/{incidentId}/remediations")
    @Operation(summary = "List asynchronous remediation executions for an incident")
    public ResponseEntity<ApiResponse<List<com.secretvault.incident.dto.IncidentDtos.IncidentRemediationExecutionResponse>>> listRemediations(
            @PathVariable UUID workspaceId,
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        var remediations = incidentService.listIncidentRemediations(workspaceId, incidentId, actorId);
        return ResponseEntity.ok(ApiResponse.success(remediations));
    }

    @GetMapping("/{incidentId}/remediations/{executionId}")
    @Operation(summary = "Get asynchronous remediation execution details")
    public ResponseEntity<ApiResponse<com.secretvault.incident.dto.IncidentDtos.IncidentRemediationExecutionResponse>> getRemediation(
            @PathVariable UUID workspaceId,
            @PathVariable UUID incidentId,
            @PathVariable UUID executionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        var remediation = incidentService.getIncidentRemediation(workspaceId, incidentId, executionId, actorId);
        return ResponseEntity.ok(ApiResponse.success(remediation));
    }

    @PostMapping("/{incidentId}/remediations/{executionId}/execute")
    @Operation(summary = "Trigger execution of a queued remediation task (worker simulation)")
    public ResponseEntity<ApiResponse<com.secretvault.incident.dto.IncidentDtos.IncidentRemediationExecutionResponse>> executeRemediationTask(
            @PathVariable UUID workspaceId,
            @PathVariable UUID incidentId,
            @PathVariable UUID executionId,
            @RequestParam(required = false, defaultValue = "worker-default") String workerId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        var response = incidentService.executeRemediationTask(executionId, workerId);
        return ResponseEntity.ok(ApiResponse.success(response, "Remediation execution processed"));
    }

    @PostMapping("/{incidentId}/remediations/{executionId}/retry")
    @Operation(summary = "Reset and retry a failed remediation task")
    public ResponseEntity<ApiResponse<com.secretvault.incident.dto.IncidentDtos.IncidentRemediationExecutionResponse>> retryRemediation(
            @PathVariable UUID workspaceId,
            @PathVariable UUID incidentId,
            @PathVariable UUID executionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        var response = incidentService.retryRemediation(workspaceId, incidentId, executionId, actorId);
        return ResponseEntity.ok(ApiResponse.success(response, "Remediation execution reset for retry"));
    }
}
