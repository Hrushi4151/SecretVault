package com.secretvault.oidc.controller;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.oidc.dto.OidcDtos;
import com.secretvault.oidc.service.OidcTrustPolicyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
public class OidcTrustPolicyController {

    private final OidcTrustPolicyService policyService;
    private final EffectiveAccessService effectiveAccessService;

    public OidcTrustPolicyController(
            OidcTrustPolicyService policyService,
            EffectiveAccessService effectiveAccessService
    ) {
        this.policyService = policyService;
        this.effectiveAccessService = effectiveAccessService;
    }

    @PostMapping("/machine-identities/{machineId}/trust-policies")
    public ResponseEntity<ApiResponse<OidcDtos.OidcTrustPolicyResponse>> createTrustPolicy(
            @PathVariable UUID workspaceId,
            @PathVariable UUID machineId,
            @Valid @RequestBody OidcDtos.CreateTrustPolicyRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_MANAGE, principal.getId());
        OidcDtos.OidcTrustPolicyResponse response = policyService.createTrustPolicy(workspaceId, machineId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "OIDC trust policy created successfully"));
    }

    @GetMapping("/machine-identities/{machineId}/trust-policies")
    public ResponseEntity<ApiResponse<List<OidcDtos.OidcTrustPolicyResponse>>> listTrustPolicies(
            @PathVariable UUID workspaceId,
            @PathVariable UUID machineId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<OidcDtos.OidcTrustPolicyResponse> list = policyService.listTrustPolicies(workspaceId, machineId);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/trust-policies/{id}")
    public ResponseEntity<ApiResponse<OidcDtos.OidcTrustPolicyResponse>> getTrustPolicy(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        OidcDtos.OidcTrustPolicyResponse response = policyService.getTrustPolicy(id, workspaceId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/trust-policies/{id}")
    public ResponseEntity<ApiResponse<OidcDtos.OidcTrustPolicyResponse>> updateTrustPolicy(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @Valid @RequestBody OidcDtos.UpdateTrustPolicyRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_MANAGE, principal.getId());
        OidcDtos.OidcTrustPolicyResponse response = policyService.updateTrustPolicy(id, workspaceId, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Trust policy updated successfully"));
    }

    @DeleteMapping("/trust-policies/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTrustPolicy(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_MANAGE, principal.getId());
        policyService.deleteTrustPolicy(id, workspaceId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Trust policy deleted"));
    }
}
