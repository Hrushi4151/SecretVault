package com.secretvault.rotation.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.rotation.dto.RotationDtos.*;
import com.secretvault.rotation.service.RotationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Tag(name = "Secret Rotation Policies", description = "Secret rotation policy lifecycle management APIs")
@SecurityRequirement(name = "BearerAuth")
public class RotationPolicyController {

    private final RotationService rotationService;

    public RotationPolicyController(RotationService rotationService) {
        this.rotationService = rotationService;
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}/secrets/{secretId}/rotation-policy")
    @Operation(summary = "Create rotation policy for a secret")
    public ResponseEntity<ApiResponse<RotationPolicyResponse>> createPolicy(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @Valid @RequestBody CreateRotationPolicyRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        RotationPolicyResponse response = rotationService.createPolicy(workspaceId, projectId, environmentId, secretId, request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Rotation policy created successfully"));
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/secrets/{secretId}/rotation-policy")
    @Operation(summary = "Get rotation policy for a secret")
    public ResponseEntity<ApiResponse<RotationPolicyResponse>> getPolicy(
            @PathVariable UUID workspaceId,
            @PathVariable UUID secretId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        RotationPolicyResponse response = rotationService.getPolicy(workspaceId, secretId, actorId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/api/v1/workspaces/{workspaceId}/secrets/{secretId}/rotation-policy")
    @Operation(summary = "Update rotation policy for a secret")
    public ResponseEntity<ApiResponse<RotationPolicyResponse>> updatePolicy(
            @PathVariable UUID workspaceId,
            @PathVariable UUID secretId,
            @Valid @RequestBody UpdateRotationPolicyRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        RotationPolicyResponse response = rotationService.updatePolicy(workspaceId, secretId, request, actorId);
        return ResponseEntity.ok(ApiResponse.success(response, "Rotation policy updated successfully"));
    }

    @DeleteMapping("/api/v1/workspaces/{workspaceId}/secrets/{secretId}/rotation-policy")
    @Operation(summary = "Disable rotation policy for a secret")
    public ResponseEntity<ApiResponse<Void>> disablePolicy(
            @PathVariable UUID workspaceId,
            @PathVariable UUID secretId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        rotationService.disablePolicy(workspaceId, secretId, actorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Rotation policy disabled successfully"));
    }
}
