package com.secretvault.sync.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.sync.dto.DriftRecordResponse;
import com.secretvault.sync.dto.UpdateDriftStatusRequest;
import com.secretvault.sync.model.DriftSeverity;
import com.secretvault.sync.model.DriftStatus;
import com.secretvault.sync.model.DriftType;
import com.secretvault.sync.service.DriftRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller providing drift visibility, filtering, and triage management.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/drift")
@Tag(name = "Drift Detection", description = "Endpoints for inspecting and managing secret state drift across external providers")
public class DriftController {

    private final DriftRecordService driftRecordService;

    public DriftController(DriftRecordService driftRecordService) {
        this.driftRecordService = driftRecordService;
    }

    @GetMapping
    @Operation(summary = "List drift records with filtering, pagination, and sorting")
    public ResponseEntity<ApiResponse<Page<DriftRecordResponse>>> getDriftRecords(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) DriftStatus status,
            @RequestParam(required = false) DriftType driftType,
            @RequestParam(required = false) DriftSeverity severity,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID environmentId,
            @RequestParam(required = false) UUID integrationId,
            @RequestParam(required = false) UUID mappingId,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Page<DriftRecordResponse> response = driftRecordService.getDriftRecords(
                workspaceId, status, driftType, severity,
                projectId, environmentId, integrationId, mappingId,
                pageable, principal.getId()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{driftId}")
    @Operation(summary = "Get a single drift record by ID")
    public ResponseEntity<ApiResponse<DriftRecordResponse>> getDriftRecordById(
            @PathVariable UUID workspaceId,
            @PathVariable UUID driftId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        DriftRecordResponse response = driftRecordService.getDriftRecordById(workspaceId, driftId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{driftId}/status")
    @Operation(summary = "Update triage status of a drift record")
    public ResponseEntity<ApiResponse<DriftRecordResponse>> updateDriftStatus(
            @PathVariable UUID workspaceId,
            @PathVariable UUID driftId,
            @Valid @RequestBody UpdateDriftStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        DriftRecordResponse response = driftRecordService.updateDriftStatus(
                workspaceId, driftId, request, principal.getId()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/detect")
    @Operation(summary = "Trigger on-demand drift detection scan across workspace or scoped targets")
    public ResponseEntity<ApiResponse<java.util.List<DriftRecordResponse>>> triggerDriftDetection(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) com.secretvault.sync.model.SyncScope scope,
            @RequestParam(required = false) UUID scopeResourceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        java.util.List<DriftRecordResponse> responses = driftRecordService.triggerDriftDetection(
                workspaceId, scope, scopeResourceId, principal.getId()
        );
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
