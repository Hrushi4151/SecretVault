package com.secretvault.project.access.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.project.access.dto.GrantProjectAccessRequest;
import com.secretvault.project.access.dto.ProjectMemberResponse;
import com.secretvault.project.access.dto.UpdateProjectAccessRequest;
import com.secretvault.project.access.service.ProjectAccessService;
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
 * REST API for Scoped Project Access and Member Role Grants.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/members")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Project Access", description = "Endpoints for managing fine-grained project membership and RBAC scoping")
public class ProjectAccessController {

    private final ProjectAccessService projectAccessService;

    public ProjectAccessController(ProjectAccessService projectAccessService) {
        this.projectAccessService = projectAccessService;
    }

    @GetMapping
    @Operation(summary = "List Project Members", description = "Retrieves all members with explicit scoped access grants for this project.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Project members list"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not a member of the workspace"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Project not found")
    })
    public ResponseEntity<ApiResponse<List<ProjectMemberResponse>>> listProjectMembers(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<ProjectMemberResponse> members = projectAccessService.getProjectMembers(workspaceId, projectId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    @PostMapping
    @Operation(summary = "Grant Project Access", description = "Grants scoped project role to a workspace member. (Requires OWNER or ADMIN).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Project access granted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Target user is not a workspace member"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Project or user not found")
    })
    public ResponseEntity<ApiResponse<ProjectMemberResponse>> grantProjectAccess(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody GrantProjectAccessRequest request) {
        ProjectMemberResponse response = projectAccessService.grantProjectAccess(workspaceId, projectId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PatchMapping("/{userId}")
    @Operation(summary = "Update Project Access", description = "Updates scoped project role for a user. (Requires OWNER or ADMIN).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Project access updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Grant or user not found")
    })
    public ResponseEntity<ApiResponse<ProjectMemberResponse>> updateProjectAccess(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProjectAccessRequest request) {
        ProjectMemberResponse response = projectAccessService.updateProjectAccess(workspaceId, projectId, userId, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Revoke Project Access", description = "Revokes scoped project access for a user. (Requires OWNER or ADMIN).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Project access revoked"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Grant not found")
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> removeProjectAccess(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        projectAccessService.removeProjectAccess(workspaceId, projectId, userId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Project access successfully revoked", "userId", userId)));
    }
}
