package com.secretvault.access.privileged.controller;

import com.secretvault.access.privileged.dto.*;
import com.secretvault.access.privileged.model.PrivilegedRequestStatus;
import com.secretvault.access.privileged.service.PrivilegedAccessService;
import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.common.ratelimit.RateLimitIdentifierType;
import com.secretvault.common.ratelimit.RateLimited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
@Tag(name = "Privileged Access Security", description = "Endpoints for Break-Glass, Dual Approval Quorum, Temporary Elevation, and Policy Governance")
public class PrivilegedAccessController {

    private final PrivilegedAccessService privilegedService;

    public PrivilegedAccessController(PrivilegedAccessService privilegedService) {
        this.privilegedService = privilegedService;
    }

    @PostMapping("/privileged-access/requests")
    @SecurityRequirement(name = "BearerAuth")
    @RateLimited(
            category = "priv_request",
            limit = 20,
            windowSeconds = 60,
            type = RateLimitIdentifierType.USER_ID,
            message = "Too many privileged access requests submitted. Please try again later."
    )
    @Operation(summary = "Create Privileged Access Request", description = "Submits a sensitive operation elevation request governed by policy quorum and step-up rules.")
    public ResponseEntity<ApiResponse<PrivilegedAccessRequestResponse>> createRequest(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreatePrivilegedAccessRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PrivilegedAccessRequestResponse response = privilegedService.createRequest(
                workspaceId,
                principal.getId(),
                principal.getSessionIdentifier(),
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response, "Privileged access request submitted"));
    }

    @GetMapping("/privileged-access/requests")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "List Privileged Access Requests", description = "Retrieves privileged access requests in the workspace, with optional status and approval filtering.")
    public ResponseEntity<ApiResponse<List<PrivilegedAccessRequestResponse>>> listRequests(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) PrivilegedRequestStatus status,
            @RequestParam(required = false) Boolean myRequestsOnly,
            @RequestParam(required = false) Boolean awaitingMyApprovalOnly,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<PrivilegedAccessRequestResponse> response = privilegedService.listRequests(
                workspaceId,
                status,
                myRequestsOnly,
                awaitingMyApprovalOnly,
                principal.getId()
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response));
    }

    @GetMapping("/privileged-access/requests/{requestId}")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Get Privileged Access Request Details", description = "Retrieves detailed state and approval ledger for a specific request.")
    public ResponseEntity<ApiResponse<PrivilegedAccessRequestResponse>> getRequest(
            @PathVariable UUID workspaceId,
            @PathVariable UUID requestId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PrivilegedAccessRequestResponse response = privilegedService.getRequest(workspaceId, requestId, principal.getId());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response));
    }

    @PostMapping("/privileged-access/requests/{requestId}/approve")
    @SecurityRequirement(name = "BearerAuth")
    @RateLimited(
            category = "priv_approve",
            limit = 30,
            windowSeconds = 60,
            type = RateLimitIdentifierType.USER_ID,
            message = "Too many approval attempts. Please try again later."
    )
    @Operation(summary = "Approve Privileged Request", description = "Submits an authorized approval decision toward satisfying quorum.")
    public ResponseEntity<ApiResponse<PrivilegedAccessRequestResponse>> approveRequest(
            @PathVariable UUID workspaceId,
            @PathVariable UUID requestId,
            @RequestBody(required = false) ApprovePrivilegedRequest body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ApprovePrivilegedRequest approvalReq = body != null ? body : new ApprovePrivilegedRequest(null, null, null);
        PrivilegedAccessRequestResponse response = privilegedService.approveRequest(
                workspaceId,
                requestId,
                principal.getId(),
                principal.getSessionIdentifier(),
                approvalReq
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response, "Privileged access request approval recorded"));
    }

    @PostMapping("/privileged-access/requests/{requestId}/reject")
    @SecurityRequirement(name = "BearerAuth")
    @RateLimited(
            category = "priv_reject",
            limit = 30,
            windowSeconds = 60,
            type = RateLimitIdentifierType.USER_ID,
            message = "Too many rejection attempts. Please try again later."
    )
    @Operation(summary = "Reject Privileged Request", description = "Rejects a pending privileged access request.")
    public ResponseEntity<ApiResponse<PrivilegedAccessRequestResponse>> rejectRequest(
            @PathVariable UUID workspaceId,
            @PathVariable UUID requestId,
            @Valid @RequestBody RejectPrivilegedRequest body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PrivilegedAccessRequestResponse response = privilegedService.rejectRequest(
                workspaceId,
                requestId,
                principal.getId(),
                body
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response, "Privileged access request rejected"));
    }

    @PostMapping("/privileged-access/requests/{requestId}/cancel")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Cancel Privileged Request", description = "Cancels a pending privileged access request (requester or admin only).")
    public ResponseEntity<ApiResponse<PrivilegedAccessRequestResponse>> cancelRequest(
            @PathVariable UUID workspaceId,
            @PathVariable UUID requestId,
            @RequestBody(required = false) CancelPrivilegedRequest body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PrivilegedAccessRequestResponse response = privilegedService.cancelRequest(
                workspaceId,
                requestId,
                principal.getId(),
                body
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response, "Privileged access request cancelled"));
    }

    @PostMapping("/privileged-access/requests/{requestId}/execute")
    @SecurityRequirement(name = "BearerAuth")
    @RateLimited(
            category = "priv_execute",
            limit = 20,
            windowSeconds = 60,
            type = RateLimitIdentifierType.USER_ID,
            message = "Too many execution attempts. Please try again later."
    )
    @Operation(summary = "Execute Approved Privileged Operation", description = "Executes an approved privileged request and activates temporary elevation.")
    public ResponseEntity<ApiResponse<PrivilegedAccessRequestResponse>> executeRequest(
            @PathVariable UUID workspaceId,
            @PathVariable UUID requestId,
            @RequestParam(required = false) String stepUpProof,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PrivilegedAccessRequestResponse response = privilegedService.executeRequest(
                workspaceId,
                requestId,
                principal.getId(),
                principal.getSessionIdentifier(),
                stepUpProof
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response, "Privileged access operation executed successfully"));
    }

    @PostMapping("/privileged-access/requests/{requestId}/revoke")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Revoke Privileged Access Request / Elevation", description = "Immediately revokes an approved request and its associated active elevation.")
    public ResponseEntity<ApiResponse<PrivilegedAccessRequestResponse>> revokeRequest(
            @PathVariable UUID workspaceId,
            @PathVariable UUID requestId,
            @Valid @RequestBody RevokePrivilegedRequest body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PrivilegedAccessRequestResponse response = privilegedService.revokeRequest(
                workspaceId,
                requestId,
                principal.getId(),
                body
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response, "Privileged access elevation revoked successfully"));
    }

    @PostMapping({"/privileged-access/break-glass", "/break-glass"})
    @SecurityRequirement(name = "BearerAuth")
    @RateLimited(
            category = "break_glass",
            limit = 5,
            windowSeconds = 60,
            type = RateLimitIdentifierType.USER_ID,
            message = "Too many Break-Glass emergency attempts. Rate limit exceeded."
    )
    @Operation(summary = "Emergency Break-Glass Access", description = "Executes strongly-authenticated, time-bounded emergency access with heavy auditing.")
    public ResponseEntity<ApiResponse<PrivilegedAccessRequestResponse>> breakGlass(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody BreakGlassRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PrivilegedAccessRequestResponse response = privilegedService.breakGlass(
                workspaceId,
                principal.getId(),
                principal.getSessionIdentifier(),
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response, "Break-Glass emergency access granted and logged"));
    }

    @GetMapping("/privileged-access/elevations")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "List Temporary Privileged Elevations", description = "Retrieves active or historical temporary elevations in the workspace.")
    public ResponseEntity<ApiResponse<List<PrivilegedAccessElevationResponse>>> listElevations(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) Boolean activeOnly,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<PrivilegedAccessElevationResponse> response = privilegedService.listElevations(
                workspaceId,
                activeOnly,
                principal.getId()
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response));
    }

    @PostMapping("/privileged-access/elevations/{elevationId}/revoke")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Revoke Elevation Grant", description = "Immediately terminates an active temporary elevation grant.")
    public ResponseEntity<ApiResponse<Void>> revokeElevation(
            @PathVariable UUID workspaceId,
            @PathVariable UUID elevationId,
            @Valid @RequestBody RevokePrivilegedRequest body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        privilegedService.revokeElevation(workspaceId, elevationId, principal.getId(), body.reason());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(null, "Temporary elevation grant revoked"));
    }

    @GetMapping("/privileged-access/policies")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "List Privileged Access Policies", description = "Retrieves configured privileged access policies for the workspace.")
    public ResponseEntity<ApiResponse<List<PrivilegedAccessPolicyResponse>>> listPolicies(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<PrivilegedAccessPolicyResponse> response = privilegedService.listPolicies(workspaceId, principal.getId());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response));
    }

    @PutMapping("/privileged-access/policies/{policyId}")
    @SecurityRequirement(name = "BearerAuth")
    @RateLimited(
            category = "priv_policy",
            limit = 10,
            windowSeconds = 60,
            type = RateLimitIdentifierType.USER_ID,
            message = "Too many policy modification attempts. Rate limit exceeded."
    )
    @Operation(summary = "Update Privileged Access Policy", description = "Modifies privileged governance policy settings (requires OWNER/ADMIN role and Step-Up).")
    public ResponseEntity<ApiResponse<PrivilegedAccessPolicyResponse>> updatePolicy(
            @PathVariable UUID workspaceId,
            @PathVariable UUID policyId,
            @Valid @RequestBody UpdatePrivilegedPolicyRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PrivilegedAccessPolicyResponse response = privilegedService.updatePolicy(
                workspaceId,
                policyId,
                principal.getId(),
                principal.getSessionIdentifier(),
                request
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response, "Privileged Access Policy updated"));
    }
}
