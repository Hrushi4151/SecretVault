package com.secretvault.sync.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.sync.dto.*;
import com.secretvault.sync.model.ReconciliationPolicy;
import com.secretvault.sync.model.SyncJobStatus;
import com.secretvault.sync.model.SyncScope;
import com.secretvault.sync.service.SyncJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for executing synchronization plans, dry-run simulations,
 * and inspecting historical sync job executions.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
@Tag(name = "Sync Engine", description = "Endpoints for dry-run simulation, live reconciliation, and sync job execution tracking")
public class SyncController {

    private final SyncJobService syncJobService;

    public SyncController(SyncJobService syncJobService) {
        this.syncJobService = syncJobService;
    }

    @PostMapping("/sync/dry-run")
    @Operation(summary = "Execute workspace-scoped dry-run drift simulation and sync planning")
    public ResponseEntity<ApiResponse<DryRunResponse>> executeWorkspaceDryRun(
            @PathVariable UUID workspaceId,
            @RequestBody(required = false) SyncExecutionRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SyncScope scope = request != null && request.scope() != null ? request.scope() : SyncScope.WORKSPACE;
        UUID scopeId = request != null ? request.scopeResourceId() : null;
        ReconciliationPolicy policy = request != null && request.reconciliationPolicy() != null ?
                request.reconciliationPolicy() : ReconciliationPolicy.SAFE_RECONCILIATION;

        DryRunResponse response = syncJobService.triggerDryRun(workspaceId, scope, scopeId, policy, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/sync")
    @Operation(summary = "Execute workspace-scoped live synchronization to external providers")
    public ResponseEntity<ApiResponse<SyncExecutionResponse>> executeWorkspaceSync(
            @PathVariable UUID workspaceId,
            @RequestBody(required = false) SyncExecutionRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SyncScope scope = request != null && request.scope() != null ? request.scope() : SyncScope.WORKSPACE;
        UUID scopeId = request != null ? request.scopeResourceId() : null;
        ReconciliationPolicy policy = request != null && request.reconciliationPolicy() != null ?
                request.reconciliationPolicy() : ReconciliationPolicy.SAFE_RECONCILIATION;

        SyncExecutionResponse response = syncJobService.triggerSync(workspaceId, scope, scopeId, policy, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/projects/{projectId}/sync/dry-run")
    @Operation(summary = "Execute project-scoped dry-run drift simulation")
    public ResponseEntity<ApiResponse<DryRunResponse>> executeProjectDryRun(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @RequestBody(required = false) SyncExecutionRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ReconciliationPolicy policy = request != null && request.reconciliationPolicy() != null ?
                request.reconciliationPolicy() : ReconciliationPolicy.SAFE_RECONCILIATION;

        DryRunResponse response = syncJobService.triggerDryRun(workspaceId, SyncScope.PROJECT, projectId, policy, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/projects/{projectId}/sync")
    @Operation(summary = "Execute project-scoped live synchronization")
    public ResponseEntity<ApiResponse<SyncExecutionResponse>> executeProjectSync(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @RequestBody(required = false) SyncExecutionRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ReconciliationPolicy policy = request != null && request.reconciliationPolicy() != null ?
                request.reconciliationPolicy() : ReconciliationPolicy.SAFE_RECONCILIATION;

        SyncExecutionResponse response = syncJobService.triggerSync(workspaceId, SyncScope.PROJECT, projectId, policy, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/environments/{environmentId}/sync/dry-run")
    @Operation(summary = "Execute environment-scoped dry-run drift simulation")
    public ResponseEntity<ApiResponse<DryRunResponse>> executeEnvironmentDryRun(
            @PathVariable UUID workspaceId,
            @PathVariable UUID environmentId,
            @RequestBody(required = false) SyncExecutionRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ReconciliationPolicy policy = request != null && request.reconciliationPolicy() != null ?
                request.reconciliationPolicy() : ReconciliationPolicy.SAFE_RECONCILIATION;

        DryRunResponse response = syncJobService.triggerDryRun(workspaceId, SyncScope.ENVIRONMENT, environmentId, policy, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/environments/{environmentId}/sync")
    @Operation(summary = "Execute environment-scoped live synchronization")
    public ResponseEntity<ApiResponse<SyncExecutionResponse>> executeEnvironmentSync(
            @PathVariable UUID workspaceId,
            @PathVariable UUID environmentId,
            @RequestBody(required = false) SyncExecutionRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ReconciliationPolicy policy = request != null && request.reconciliationPolicy() != null ?
                request.reconciliationPolicy() : ReconciliationPolicy.SAFE_RECONCILIATION;

        SyncExecutionResponse response = syncJobService.triggerSync(workspaceId, SyncScope.ENVIRONMENT, environmentId, policy, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/sync/jobs")
    @Operation(summary = "List sync jobs for a workspace with pagination and sorting")
    public ResponseEntity<ApiResponse<Page<SyncJobResponse>>> getSyncJobs(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) SyncJobStatus status,
            @RequestParam(required = false) Boolean dryRun,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Page<SyncJobResponse> response = syncJobService.getSyncJobs(workspaceId, status, dryRun, pageable, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/sync/jobs/{jobId}")
    @Operation(summary = "Get sync job details by ID")
    public ResponseEntity<ApiResponse<SyncJobResponse>> getSyncJobById(
            @PathVariable UUID workspaceId,
            @PathVariable UUID jobId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SyncJobResponse response = syncJobService.getSyncJobById(workspaceId, jobId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/sync/jobs/{jobId}/operations")
    @Operation(summary = "List operations for a specific sync job")
    public ResponseEntity<ApiResponse<Page<SyncOperationResponse>>> getSyncJobOperations(
            @PathVariable UUID workspaceId,
            @PathVariable UUID jobId,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Page<SyncOperationResponse> response = syncJobService.getSyncJobOperations(workspaceId, jobId, pageable, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
