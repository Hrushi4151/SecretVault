package com.secretvault.access.jit.controller;

import com.secretvault.access.jit.dto.ApproveJitRequest;
import com.secretvault.access.jit.dto.JitAccessRequestResponse;
import com.secretvault.access.jit.dto.RejectJitRequest;
import com.secretvault.access.jit.dto.SubmitJitRequest;
import com.secretvault.access.jit.entity.JitStatus;
import com.secretvault.access.jit.service.JitAccessService;
import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/jit")
public class JitAccessController {

    private final JitAccessService jitAccessService;

    public JitAccessController(JitAccessService jitAccessService) {
        this.jitAccessService = jitAccessService;
    }

    @GetMapping("/requests")
    public ResponseEntity<ApiResponse<List<JitAccessRequestResponse>>> listRequests(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) JitStatus status,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<JitAccessRequestResponse> requests = jitAccessService.listRequests(workspaceId, status, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(requests));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<JitAccessRequestResponse>>> getActiveGrants(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<JitAccessRequestResponse> active = jitAccessService.getActiveGrants(workspaceId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(active));
    }

    @PostMapping("/requests")
    public ResponseEntity<ApiResponse<JitAccessRequestResponse>> submitRequest(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody SubmitJitRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        JitAccessRequestResponse response = jitAccessService.submitRequest(workspaceId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "JIT access request submitted successfully"));
    }

    @PostMapping("/requests/{requestId}/approve")
    public ResponseEntity<ApiResponse<JitAccessRequestResponse>> approveRequest(
            @PathVariable UUID workspaceId,
            @PathVariable UUID requestId,
            @Valid @RequestBody(required = false) ApproveJitRequest body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        JitAccessRequestResponse response = jitAccessService.approveRequest(workspaceId, requestId, body, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "JIT access request approved successfully"));
    }

    @PostMapping("/requests/{requestId}/reject")
    public ResponseEntity<ApiResponse<JitAccessRequestResponse>> rejectRequest(
            @PathVariable UUID workspaceId,
            @PathVariable UUID requestId,
            @Valid @RequestBody RejectJitRequest body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        JitAccessRequestResponse response = jitAccessService.rejectRequest(workspaceId, requestId, body, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "JIT access request rejected"));
    }

    @PostMapping("/requests/{requestId}/revoke")
    public ResponseEntity<ApiResponse<Void>> revokeGrant(
            @PathVariable UUID workspaceId,
            @PathVariable UUID requestId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        jitAccessService.revokeGrant(workspaceId, requestId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Active JIT grant revoked successfully"));
    }

    @PostMapping("/requests/{requestId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelRequest(
            @PathVariable UUID workspaceId,
            @PathVariable UUID requestId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        jitAccessService.cancelRequest(workspaceId, requestId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "JIT request cancelled successfully"));
    }
}
