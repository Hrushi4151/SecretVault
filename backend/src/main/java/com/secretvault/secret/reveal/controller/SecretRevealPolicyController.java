package com.secretvault.secret.reveal.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.common.ratelimit.RateLimitIdentifierType;
import com.secretvault.common.ratelimit.RateLimited;
import com.secretvault.secret.reveal.dto.SecretRevealAuditResponse;
import com.secretvault.secret.reveal.dto.SecretRevealPolicyResponse;
import com.secretvault.secret.reveal.dto.UpdateSecretRevealPolicyRequest;
import com.secretvault.secret.reveal.service.SecretRevealPolicyService;
import com.secretvault.secret.reveal.service.SecretRevealService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
@Tag(name = "Secret Reveal Governance", description = "Fine-grained policies and audit history for secret reveal protection")
@SecurityRequirement(name = "BearerAuth")
public class SecretRevealPolicyController {

    private final SecretRevealPolicyService policyService;
    private final SecretRevealService revealService;

    public SecretRevealPolicyController(
            SecretRevealPolicyService policyService,
            SecretRevealService revealService
    ) {
        this.policyService = policyService;
        this.revealService = revealService;
    }

    @GetMapping("/secret-reveal-policies")
    @Operation(summary = "List custom secret reveal policies configured for a workspace")
    public ResponseEntity<ApiResponse<List<SecretRevealPolicyResponse>>> getPolicies(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<SecretRevealPolicyResponse> policies = policyService.getWorkspacePolicies(workspaceId, principal.getId());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(policies));
    }

    @PostMapping("/secret-reveal-policies")
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimited(category = "secret_reveal_policy", limit = 10, windowSeconds = 60, type = RateLimitIdentifierType.IP_AND_USER)
    @Operation(summary = "Create or update a custom secret reveal policy")
    public ResponseEntity<ApiResponse<SecretRevealPolicyResponse>> setPolicy(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody UpdateSecretRevealPolicyRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        SecretRevealPolicyResponse response = policyService.setPolicy(workspaceId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response));
    }

    @DeleteMapping("/secret-reveal-policies/{policyId}")
    @RateLimited(category = "secret_reveal_policy", limit = 10, windowSeconds = 60, type = RateLimitIdentifierType.IP_AND_USER)
    @Operation(summary = "Delete a custom secret reveal policy override")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deletePolicy(
            @PathVariable UUID workspaceId,
            @PathVariable UUID policyId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        policyService.deletePolicy(workspaceId, policyId, principal.getId());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(Map.of("message", "Policy successfully deleted", "policyId", policyId)));
    }

    @GetMapping("/secret-reveal-audit")
    @Operation(summary = "Query secret reveal audit trail for security administrators")
    public ResponseEntity<ApiResponse<Page<SecretRevealAuditResponse>>> getRevealAudit(
            @PathVariable UUID workspaceId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Page<SecretRevealAuditResponse> auditHistory = revealService.getRevealAuditHistory(workspaceId, principal.getId(), pageable);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(auditHistory));
    }
}
