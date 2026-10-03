package com.secretvault.repository.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.repository.entity.RepositorySecurityPolicy;
import com.secretvault.repository.service.RepositoryPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/repository-policies")
@Tag(name = "Repository Security Policies", description = "Endpoints for configuring scanning policies, thresholds, and exclusions")
@SecurityRequirement(name = "BearerAuth")
public class RepositoryPolicyController {

    private final RepositoryPolicyService policyService;

    public RepositoryPolicyController(RepositoryPolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    @Operation(summary = "Get effective scanning policy for repository or workspace default")
    public ResponseEntity<ApiResponse<RepositorySecurityPolicy>> getPolicy(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID repositoryId
    ) {
        RepositorySecurityPolicy policy = policyService.getEffectivePolicy(workspaceId, repositoryId);
        return ResponseEntity.ok(ApiResponse.success(policy));
    }

    @PutMapping
    @Operation(summary = "Save or update security scanning policy")
    public ResponseEntity<ApiResponse<RepositorySecurityPolicy>> savePolicy(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID repositoryId,
            @RequestBody RepositorySecurityPolicy policy,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        RepositorySecurityPolicy saved = policyService.savePolicy(workspaceId, repositoryId, policy, actorId);
        return ResponseEntity.ok(ApiResponse.success(saved, "Repository policy updated successfully"));
    }
}
