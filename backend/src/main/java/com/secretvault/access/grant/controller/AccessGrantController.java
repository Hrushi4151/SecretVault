package com.secretvault.access.grant.controller;

import com.secretvault.access.dto.EffectiveAccessExplanation;
import com.secretvault.access.grant.dto.AccessGrantResponse;
import com.secretvault.access.grant.dto.CreateAccessGrantRequest;
import com.secretvault.access.grant.service.AccessGrantService;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.service.EffectiveAccessService;
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
@RequestMapping("/api/v1/workspaces/{workspaceId}/access")
public class AccessGrantController {

    private final AccessGrantService accessGrantService;
    private final EffectiveAccessService effectiveAccessService;

    public AccessGrantController(AccessGrantService accessGrantService, EffectiveAccessService effectiveAccessService) {
        this.accessGrantService = accessGrantService;
        this.effectiveAccessService = effectiveAccessService;
    }

    @GetMapping("/grants")
    public ResponseEntity<ApiResponse<PageResponse<AccessGrantResponse>>> listGrants(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) AccessScope scopeType,
            @RequestParam(required = false) AccessPermission permission,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID environmentId,
            @RequestParam(required = false) UUID secretId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PageResponse<AccessGrantResponse> grants = accessGrantService.listGrants(
                workspaceId, userId, scopeType, permission, projectId, environmentId, secretId, pageable, principal.getId()
        );
        return ResponseEntity.ok(ApiResponse.success(grants));
    }

    @GetMapping("/grants/{grantId}")
    public ResponseEntity<ApiResponse<AccessGrantResponse>> getGrantById(
            @PathVariable UUID workspaceId,
            @PathVariable UUID grantId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        AccessGrantResponse response = accessGrantService.getGrantById(workspaceId, grantId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
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
