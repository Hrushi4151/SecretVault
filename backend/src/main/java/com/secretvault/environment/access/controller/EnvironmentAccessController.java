package com.secretvault.environment.access.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.environment.access.dto.EnvironmentAccessResponse;
import com.secretvault.environment.access.dto.GrantEnvironmentAccessRequest;
import com.secretvault.environment.access.dto.UpdateEnvironmentAccessRequest;
import com.secretvault.environment.access.service.EnvironmentAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/**
 * REST API for Scoped Environment Access and Permission Levels.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}/access")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Environment Access", description = "Endpoints for managing fine-grained environment access scoping")
public class EnvironmentAccessController {

    private final EnvironmentAccessService environmentAccessService;

    public EnvironmentAccessController(EnvironmentAccessService environmentAccessService) {
        this.environmentAccessService = environmentAccessService;
    }

    @GetMapping
    @Operation(summary = "List Environment Access Grants", description = "Retrieves all scoped access grants for this environment.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Environment access list"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not a member of the workspace"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Environment not found")
    })
    public ResponseEntity<ApiResponse<List<EnvironmentAccessResponse>>> listEnvironmentAccess(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<EnvironmentAccessResponse> accesses = environmentAccessService.getEnvironmentAccesses(
                workspaceId, projectId, environmentId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(accesses));
    }

    @PostMapping
    @Operation(summary = "Grant Environment Access", description = "Grants scoped environment permission to a workspace member. (Requires OWNER or ADMIN).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Environment access granted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Target user is not a workspace member"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Environment or user not found")
    })
    public ResponseEntity<ApiResponse<EnvironmentAccessResponse>> grantEnvironmentAccess(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody GrantEnvironmentAccessRequest request) {
        EnvironmentAccessResponse response = environmentAccessService.grantEnvironmentAccess(
                workspaceId, projectId, environmentId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PatchMapping("/{userId}")
    @Operation(summary = "Update Environment Access", description = "Updates scoped environment permission for a user. (Requires OWNER or ADMIN).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Environment access updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Grant or user not found")
    })
    public ResponseEntity<ApiResponse<EnvironmentAccessResponse>> updateEnvironmentAccess(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateEnvironmentAccessRequest request) {
        EnvironmentAccessResponse response = environmentAccessService.updateEnvironmentAccess(
                workspaceId, projectId, environmentId, userId, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Revoke Environment Access", description = "Revokes scoped environment access for a user. (Requires OWNER or ADMIN).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Environment access revoked"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Grant not found")
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> removeEnvironmentAccess(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        environmentAccessService.removeEnvironmentAccess(workspaceId, projectId, environmentId, userId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Environment access successfully revoked", "userId", userId)));
    }
}
