package com.secretvault.oidc.controller;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.oidc.dto.OidcDtos;
import com.secretvault.oidc.service.OidcProviderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/oidc-providers")
public class OidcProviderController {

    private final OidcProviderService providerService;
    private final EffectiveAccessService effectiveAccessService;

    public OidcProviderController(
            OidcProviderService providerService,
            EffectiveAccessService effectiveAccessService
    ) {
        this.providerService = providerService;
        this.effectiveAccessService = effectiveAccessService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OidcDtos.OidcProviderResponse>> createProvider(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody OidcDtos.CreateOidcProviderRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_MANAGE, principal.getId());
        OidcDtos.OidcProviderResponse response = providerService.createProvider(workspaceId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "OIDC provider registered successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<OidcDtos.OidcProviderResponse>>> listProviders(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<OidcDtos.OidcProviderResponse> list = providerService.listProviders(workspaceId);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OidcDtos.OidcProviderResponse>> getProvider(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        OidcDtos.OidcProviderResponse response = providerService.getProvider(id, workspaceId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<OidcDtos.OidcProviderResponse>> updateProvider(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @Valid @RequestBody OidcDtos.UpdateOidcProviderRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_MANAGE, principal.getId());
        OidcDtos.OidcProviderResponse response = providerService.updateProvider(id, workspaceId, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "OIDC provider updated successfully"));
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<ApiResponse<OidcDtos.OidcProviderResponse>> disableProvider(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_MANAGE, principal.getId());
        OidcDtos.OidcProviderResponse response = providerService.disableProvider(id, workspaceId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "OIDC provider disabled"));
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<OidcDtos.OidcProviderResponse>> enableProvider(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_MANAGE, principal.getId());
        OidcDtos.OidcProviderResponse response = providerService.enableProvider(id, workspaceId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "OIDC provider enabled"));
    }

    @PostMapping("/{id}/refresh-jwks")
    public ResponseEntity<ApiResponse<OidcDtos.OidcProviderResponse>> refreshJwks(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_MANAGE, principal.getId());
        OidcDtos.OidcProviderResponse response = providerService.refreshJwks(id, workspaceId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "JWKS keys refreshed successfully"));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testProvider(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_MANAGE, principal.getId());
        Map<String, Object> testResult = providerService.testProvider(id, workspaceId);
        return ResponseEntity.ok(ApiResponse.success(testResult));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProvider(
            @PathVariable UUID workspaceId,
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_MANAGE, principal.getId());
        providerService.deleteProvider(id, workspaceId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "OIDC provider deleted"));
    }
}
