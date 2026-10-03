package com.secretvault.rotation.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.rotation.dto.RotationDtos.*;
import com.secretvault.rotation.service.RotationImpactService;
import com.secretvault.rotation.service.RotationService;
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

import java.util.UUID;

@RestController
@Tag(name = "Secret Rotation Execution", description = "Secret rotation execution, state machine, impact analysis, rollback and compromise remediation APIs")
@SecurityRequirement(name = "BearerAuth")
public class RotationJobController {

    private final RotationService rotationService;
    private final RotationImpactService impactService;

    public RotationJobController(RotationService rotationService, RotationImpactService impactService) {
        this.rotationService = rotationService;
        this.impactService = impactService;
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/secrets/{secretId}/rotate")
    @Operation(summary = "Trigger manual or emergency secret rotation")
    public ResponseEntity<ApiResponse<RotationJobResponse>> triggerRotation(
            @PathVariable UUID workspaceId,
            @PathVariable UUID secretId,
            @RequestBody(required = false) TriggerRotationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        RotationJobResponse response = rotationService.triggerRotation(workspaceId, secretId, request, actorId, idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response, "Rotation initiated successfully"));
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/secrets/{secretId}/rotations")
    @Operation(summary = "List rotation history for a secret")
    public ResponseEntity<ApiResponse<Page<RotationJobResponse>>> listRotations(
            @PathVariable UUID workspaceId,
            @PathVariable UUID secretId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        Page<RotationJobResponse> response = rotationService.listJobs(workspaceId, secretId, actorId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/rotations")
    @Operation(summary = "List all rotation jobs in workspace")
    public ResponseEntity<ApiResponse<Page<RotationJobResponse>>> listWorkspaceRotations(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID secretId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        Page<RotationJobResponse> response = rotationService.listJobs(workspaceId, secretId, actorId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/rotations/{jobId}/retry")
    @Operation(summary = "Retry a failed rotation job")
    public ResponseEntity<ApiResponse<RotationJobResponse>> retryJob(
            @PathVariable UUID workspaceId,
            @PathVariable UUID jobId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        RotationJobResponse response = rotationService.retryRotation(workspaceId, jobId, actorId);
        return ResponseEntity.ok(ApiResponse.success(response, "Rotation job queued for retry"));
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/rotations/{jobId}")
    @Operation(summary = "Get rotation job details by ID")
    public ResponseEntity<ApiResponse<RotationJobResponse>> getJob(
            @PathVariable UUID workspaceId,
            @PathVariable UUID jobId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        RotationJobResponse response = rotationService.getJob(workspaceId, jobId, actorId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/rotations/{jobId}/cancel")
    @Operation(summary = "Cancel an in-progress rotation job")
    public ResponseEntity<ApiResponse<Void>> cancelJob(
            @PathVariable UUID workspaceId,
            @PathVariable UUID jobId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        rotationService.cancelRotation(workspaceId, jobId, actorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Rotation job cancelled"));
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/secrets/{secretId}/rotate/rollback")
    @Operation(summary = "Rollback secret to a previous version and create new rollback version")
    public ResponseEntity<ApiResponse<RotationJobResponse>> rollback(
            @PathVariable UUID workspaceId,
            @PathVariable UUID secretId,
            @RequestBody(required = false) RotationRollbackRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        RotationJobResponse response = rotationService.rollbackRotation(workspaceId, secretId, request, actorId);
        return ResponseEntity.ok(ApiResponse.success(response, "Secret rolled back successfully"));
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/secrets/{secretId}/rotate/compromise")
    @Operation(summary = "Mark secret as compromised: immediate invalidation and emergency rotation")
    public ResponseEntity<ApiResponse<Void>> markCompromised(
            @PathVariable UUID workspaceId,
            @PathVariable UUID secretId,
            @RequestBody(required = false) MarkCompromisedRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        rotationService.markCompromised(workspaceId, secretId, request, actorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Secret marked as compromised; emergency rotation initiated"));
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/secrets/{secretId}/rotation-impact")
    @Operation(summary = "Analyze rotation impact and runtime consumer dependencies")
    public ResponseEntity<ApiResponse<RotationImpactResponse>> getRotationImpact(
            @PathVariable UUID workspaceId,
            @PathVariable UUID secretId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        RotationImpactResponse response = impactService.calculateImpact(workspaceId, secretId, actorId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
