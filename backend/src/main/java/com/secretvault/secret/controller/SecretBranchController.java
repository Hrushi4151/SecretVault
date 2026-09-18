package com.secretvault.secret.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.common.exception.ApiException;
import com.secretvault.secret.dto.BranchCommitRequest;
import com.secretvault.secret.dto.BranchComparisonResponse;
import com.secretvault.secret.dto.BranchMergeRequest;
import com.secretvault.secret.dto.BranchMergeResponse;
import com.secretvault.secret.dto.CreateBranchRequest;
import com.secretvault.secret.dto.SecretBranchResponse;
import com.secretvault.secret.dto.SecretVersionResponse;
import com.secretvault.secret.service.SecretBranchService;
import com.secretvault.secret.service.SecretMergeEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}/secrets/{secretId}/branches")
@Tag(name = "Secret Branches", description = "Secret branching, branch version commits, and 3-way branch merging APIs")
@SecurityRequirement(name = "BearerAuth")
public class SecretBranchController {

    private final SecretBranchService branchService;
    private final SecretMergeEngine mergeEngine;

    public SecretBranchController(SecretBranchService branchService, SecretMergeEngine mergeEngine) {
        this.branchService = branchService;
        this.mergeEngine = mergeEngine;
    }

    @PostMapping
    @Operation(summary = "Create a new secret feature branch from a base version")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<SecretBranchResponse>> createBranch(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @Valid @RequestBody CreateBranchRequest request,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        String requestId = resolveRequestId();
        String ipAddress = httpRequest.getRemoteAddr();

        SecretBranchResponse response = branchService.createBranch(
                workspaceId, projectId, environmentId, secretId, request, principal.getId(), requestId, ipAddress
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Branch created successfully"));
    }

    @GetMapping
    @Operation(summary = "List all branches for a secret including canonical main trunk")
    public ResponseEntity<ApiResponse<List<SecretBranchResponse>>> getBranches(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        List<SecretBranchResponse> branches = branchService.getBranches(
                workspaceId, projectId, environmentId, secretId, principal.getId()
        );
        return ResponseEntity.ok(ApiResponse.success(branches));
    }

    @GetMapping("/{branchId}")
    @Operation(summary = "Get branch details by ID")
    public ResponseEntity<ApiResponse<SecretBranchResponse>> getBranchById(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @PathVariable UUID branchId,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        SecretBranchResponse branch = branchService.getBranchById(
                workspaceId, projectId, environmentId, secretId, branchId, principal.getId()
        );
        return ResponseEntity.ok(ApiResponse.success(branch));
    }

    @PostMapping("/{branchId}/versions")
    @Operation(summary = "Commit a new version to a feature branch (does not modify main trunk)")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<SecretVersionResponse>> createBranchVersion(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @PathVariable UUID branchId,
            @Valid @RequestBody BranchCommitRequest request,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        String requestId = resolveRequestId();
        String ipAddress = httpRequest.getRemoteAddr();

        SecretVersionResponse response = branchService.createBranchVersion(
                workspaceId, projectId, environmentId, secretId, branchId, request, principal.getId(), requestId, ipAddress
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Version committed to branch"));
    }

    @GetMapping("/{branchId}/compare")
    @Operation(summary = "Compare branch with canonical main trunk")
    public ResponseEntity<ApiResponse<BranchComparisonResponse>> compareBranch(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @PathVariable UUID branchId,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        BranchComparisonResponse comparison = branchService.compareBranchWithMain(
                workspaceId, projectId, environmentId, secretId, branchId, principal.getId()
        );
        return ResponseEntity.ok(ApiResponse.success(comparison));
    }

    @PostMapping("/{branchId}/merge")
    @Operation(summary = "Execute 3-way merge of branch into main trunk")
    public ResponseEntity<ApiResponse<BranchMergeResponse>> mergeBranch(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @PathVariable UUID branchId,
            @RequestBody(required = false) BranchMergeRequest request,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        String requestId = resolveRequestId();
        String ipAddress = httpRequest.getRemoteAddr();

        BranchMergeResponse response = mergeEngine.mergeBranch(
                workspaceId, projectId, environmentId, secretId, branchId, request, principal.getId(), requestId, ipAddress
        );
        return ResponseEntity.ok(ApiResponse.success(response, "Branch merged successfully"));
    }

    @PostMapping("/{branchId}/archive")
    @Operation(summary = "Archive a feature branch")
    public ResponseEntity<ApiResponse<SecretBranchResponse>> archiveBranch(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @PathVariable UUID branchId,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        String requestId = resolveRequestId();
        String ipAddress = httpRequest.getRemoteAddr();

        SecretBranchResponse response = branchService.archiveBranch(
                workspaceId, projectId, environmentId, secretId, branchId, principal.getId(), requestId, ipAddress
        );
        return ResponseEntity.ok(ApiResponse.success(response, "Branch archived successfully"));
    }

    private void validateWorkspaceHeader(UUID pathWorkspaceId, UUID headerWorkspaceId) {
        if (headerWorkspaceId != null && !headerWorkspaceId.equals(pathWorkspaceId)) {
            throw ApiException.badRequest("X-Workspace-ID header does not match path workspace ID");
        }
    }

    private String resolveRequestId() {
        String reqId = MDC.get("correlationId");
        return StringUtils.hasText(reqId) ? reqId : UUID.randomUUID().toString();
    }
}
