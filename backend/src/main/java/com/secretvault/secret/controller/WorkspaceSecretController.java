package com.secretvault.secret.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.secret.dto.SecretMetadataResponse;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.service.SecretService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for workspace-level secret queries and rotation dashboard listings.
 * Never returns plaintext secret values.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/secrets")
@Tag(name = "Workspace Secrets", description = "Workspace-wide secret retrieval and rotation dashboard queries")
@SecurityRequirement(name = "BearerAuth")
public class WorkspaceSecretController {

    private final SecretService secretService;

    public WorkspaceSecretController(SecretService secretService) {
        this.secretService = secretService;
    }

    @GetMapping
    @Operation(summary = "List all secret metadata in workspace (never returns plaintext)")
    public ResponseEntity<ApiResponse<List<SecretMetadataResponse>>> getWorkspaceSecrets(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SecretStatus status,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getId() : null;
        List<SecretMetadataResponse> secrets = secretService.getSecretsByWorkspace(workspaceId, search, status, userId);
        return ResponseEntity.ok(ApiResponse.success(secrets));
    }
}
