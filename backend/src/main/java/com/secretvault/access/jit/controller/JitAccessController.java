package com.secretvault.access.jit.controller;

import com.secretvault.access.jit.dto.ApproveJitRequest;
import com.secretvault.access.jit.dto.JitAccessRequestResponse;
import com.secretvault.access.jit.dto.RejectJitRequest;
import com.secretvault.access.jit.dto.SubmitJitRequest;
import com.secretvault.access.jit.entity.JitStatus;
import com.secretvault.access.jit.service.JitAccessService;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.common.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/workspaces/{workspaceId}/access/jit", "/api/v1/workspaces/{workspaceId}/jit"})
public class JitAccessController {

    private final JitAccessService jitService;

    public JitAccessController(JitAccessService jitService) {
        this.jitService = jitService;
    }

    @PostMapping("/requests")
    public ResponseEntity<ApiResponse<JitAccessRequestResponse>> submitRequest(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody SubmitJitRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        JitAccessRequestResponse response = jitService.submitRequest(workspaceId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "JIT temporary access request submitted"));
    }

    @GetMapping("/requests")
    public ResponseEntity<ApiResponse<PageResponse<JitAccessRequestResponse>>> listRequests(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) JitStatus status,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) AccessPermission permission,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID environmentId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PageResponse<JitAccessRequestResponse> response = jitService.listRequestsPaginated(
                workspaceId, status, userId, permission, projectId, environmentId, pageable, principal.getId()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/requests/{requestId}")
    public ResponseEntity<ApiResponse<JitAccessRequestResponse>> getRequestById(
            @PathVariable UUID workspaceId,
            @PathVariable UUID requestId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        JitAccessRequestResponse response = jitService.getRequestById(workspaceId, requestId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/requests/{requestId}/approve")
    public ResponseEntity<ApiResponse<JitAccessRequestResponse>> approveRequest(
            @PathVariable UUID workspaceId,
            @PathVariable UUID requestId,
            @RequestBody(required = false) ApproveJitRequest body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        JitAccessRequestResponse response = jitService.approveRequest(workspaceId, requestId, body, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "JIT request approved and temporary elevation granted"));
    }

    @PostMapping("/requests/{requestId}/reject")
    public ResponseEntity<ApiResponse<JitAccessRequestResponse>> rejectRequest(
            @PathVariable UUID workspaceId,
            @PathVariable UUID requestId,
            @RequestBody(required = false) RejectJitRequest body,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        JitAccessRequestResponse response = jitService.rejectRequest(workspaceId, requestId, body, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "JIT request rejected"));
    }

    @PostMapping("/requests/{requestId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelRequest(
            @PathVariable UUID workspaceId,
            @PathVariable UUID requestId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        jitService.cancelRequest(workspaceId, requestId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "JIT request cancelled"));
    }

    @PostMapping("/requests/{requestId}/revoke")
    public ResponseEntity<ApiResponse<Void>> revokeGrant(
            @PathVariable UUID workspaceId,
            @PathVariable UUID requestId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        jitService.revokeGrant(workspaceId, requestId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Active JIT elevation revoked successfully"));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<JitAccessRequestResponse>>> getActiveGrants(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<JitAccessRequestResponse> active = jitService.getActiveGrants(workspaceId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(active));
    }
}
