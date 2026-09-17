package com.secretvault.environment.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.environment.dto.CreateEnvironmentRequest;
import com.secretvault.environment.dto.EnvironmentResponse;
import com.secretvault.environment.dto.UpdateEnvironmentRequest;
import com.secretvault.environment.service.EnvironmentService;
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
 * REST API for managing Environments within a Project.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/environments")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Environments", description = "Endpoints for managing project deployment environments and protection tiers")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    public EnvironmentController(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    @GetMapping
    @Operation(summary = "List Project Environments", description = "Retrieves all environments belonging to the specified project.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Environments retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not a member of the workspace"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Workspace or Project not found")
    })
    public ResponseEntity<ApiResponse<List<EnvironmentResponse>>> listEnvironments(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<EnvironmentResponse> envs = environmentService.getEnvironments(workspaceId, projectId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(envs));
    }

    @PostMapping
    @Operation(summary = "Create Environment", description = "Creates a new custom environment within a project. (Requires OWNER or ADMIN).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Environment created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate environment slug")
    })
    public ResponseEntity<ApiResponse<EnvironmentResponse>> createEnvironment(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateEnvironmentRequest request) {
        EnvironmentResponse env = environmentService.createEnvironment(workspaceId, projectId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(env));
    }

    @GetMapping("/{environmentId}")
    @Operation(summary = "Get Environment Details", description = "Retrieves environment details if caller has access to the workspace hierarchy.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Environment retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not a member of the workspace"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Hierarchy mismatch or environment not found")
    })
    public ResponseEntity<ApiResponse<EnvironmentResponse>> getEnvironment(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        EnvironmentResponse env = environmentService.getEnvironmentById(workspaceId, projectId, environmentId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(env));
    }

    @PatchMapping("/{environmentId}")
    @Operation(summary = "Update Environment", description = "Updates environment settings, tier, or protection status. (Requires OWNER or ADMIN).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Environment updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Environment not found")
    })
    public ResponseEntity<ApiResponse<EnvironmentResponse>> updateEnvironment(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateEnvironmentRequest request) {
        EnvironmentResponse env = environmentService.updateEnvironment(workspaceId, projectId, environmentId, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(env));
    }

    @DeleteMapping("/{environmentId}")
    @Operation(summary = "Delete Environment", description = "Deletes an environment. (Requires OWNER or ADMIN).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Environment deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Environment not found")
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteEnvironment(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        environmentService.deleteEnvironment(workspaceId, projectId, environmentId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Environment successfully deleted", "environmentId", environmentId)));
    }
}
