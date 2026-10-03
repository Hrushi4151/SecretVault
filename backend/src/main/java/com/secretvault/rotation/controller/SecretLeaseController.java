package com.secretvault.rotation.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.rotation.dto.RotationDtos.*;
import com.secretvault.rotation.model.LeaseStatus;
import com.secretvault.rotation.service.SecretLeaseService;
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
@Tag(name = "Secret Leases", description = "Runtime secret lease issuance, renewal, and revocation APIs")
@SecurityRequirement(name = "BearerAuth")
public class SecretLeaseController {

    private final SecretLeaseService leaseService;

    public SecretLeaseController(SecretLeaseService leaseService) {
        this.leaseService = leaseService;
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/leases")
    @Operation(summary = "Issue a new runtime secret lease")
    public ResponseEntity<ApiResponse<SecretLeaseResponse>> createLease(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateSecretLeaseRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        SecretLeaseResponse response = leaseService.createLease(workspaceId, request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Secret lease issued"));
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/leases/{leaseId}")
    @Operation(summary = "Get secret lease by ID")
    public ResponseEntity<ApiResponse<SecretLeaseResponse>> getLease(
            @PathVariable UUID workspaceId,
            @PathVariable UUID leaseId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        SecretLeaseResponse response = leaseService.getLease(workspaceId, leaseId, actorId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/leases")
    @Operation(summary = "List secret leases in workspace")
    public ResponseEntity<ApiResponse<Page<SecretLeaseResponse>>> listLeases(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID secretId,
            @RequestParam(required = false) LeaseStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        Page<SecretLeaseResponse> response = leaseService.listLeases(workspaceId, secretId, status, pageable, actorId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/leases/{leaseId}/renew")
    @Operation(summary = "Renew an active secret lease")
    public ResponseEntity<ApiResponse<SecretLeaseResponse>> renewLease(
            @PathVariable UUID workspaceId,
            @PathVariable UUID leaseId,
            @RequestBody(required = false) RenewSecretLeaseRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        SecretLeaseResponse response = leaseService.renewLease(workspaceId, leaseId, request, actorId);
        return ResponseEntity.ok(ApiResponse.success(response, "Secret lease renewed"));
    }

    @DeleteMapping("/api/v1/workspaces/{workspaceId}/leases/{leaseId}")
    @Operation(summary = "Revoke a secret lease")
    public ResponseEntity<ApiResponse<Void>> revokeLease(
            @PathVariable UUID workspaceId,
            @PathVariable UUID leaseId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        leaseService.revokeLease(workspaceId, leaseId, actorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Secret lease revoked"));
    }
}
