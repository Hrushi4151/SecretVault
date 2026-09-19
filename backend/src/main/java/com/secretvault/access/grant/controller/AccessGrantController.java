package com.secretvault.access.grant.controller;

import com.secretvault.access.dto.EffectiveAccessExplanation;
import com.secretvault.access.grant.dto.AccessGrantResponse;
import com.secretvault.access.grant.dto.CreateAccessGrantRequest;
import com.secretvault.access.grant.service.AccessGrantService;
import com.secretvault.access.service.EffectiveAccessService;
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
@RequestMapping("/api/v1/workspaces/{workspaceId}/access")
public class AccessGrantController {

    private final AccessGrantService accessGrantService;
    private final EffectiveAccessService effectiveAccessService;

    public AccessGrantController(AccessGrantService accessGrantService, EffectiveAccessService effectiveAccessService) {
        this.accessGrantService = accessGrantService;
        this.effectiveAccessService = effectiveAccessService;
    }

    @GetMapping("/grants")
    public ResponseEntity<ApiResponse<List<AccessGrantResponse>>> listGrants(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<AccessGrantResponse> grants = accessGrantService.listGrants(workspaceId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(grants));
    }

    @PostMapping("/grants")
    public ResponseEntity<ApiResponse<AccessGrantResponse>> createGrant(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateAccessGrantRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        AccessGrantResponse response = accessGrantService.createGrant(workspaceId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Access grant created successfully"));
    }

    @DeleteMapping("/grants/{grantId}")
    public ResponseEntity<ApiResponse<Void>> revokeGrant(
            @PathVariable UUID workspaceId,
            @PathVariable UUID grantId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        accessGrantService.revokeGrant(workspaceId, grantId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Access grant revoked successfully"));
    }

    @GetMapping("/effective")
    public ResponseEntity<ApiResponse<List<EffectiveAccessExplanation>>> getEffectivePermissions(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID environmentId,
            @RequestParam(required = false) UUID secretId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<EffectiveAccessExplanation> explanations = effectiveAccessService.explainAccess(
                workspaceId, projectId, environmentId, secretId, principal.getId()
        );
        return ResponseEntity.ok(ApiResponse.success(explanations));
    }
}
