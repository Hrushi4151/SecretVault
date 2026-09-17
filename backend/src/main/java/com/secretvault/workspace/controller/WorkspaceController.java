package com.secretvault.workspace.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.workspace.dto.AddMemberRequest;
import com.secretvault.workspace.dto.CreateWorkspaceRequest;
import com.secretvault.workspace.dto.MemberResponse;
import com.secretvault.workspace.dto.WorkspaceResponse;
import com.secretvault.workspace.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Multi-tenant Workspace and Member Administration API.
 */
@RestController
@RequestMapping("/api/v1/workspaces")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Workspaces", description = "Endpoints for managing workspace isolation containers and membership access")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    @Operation(summary = "List User Workspaces", description = "Retrieves all workspaces where the caller has active membership.")
    public ResponseEntity<ApiResponse<List<WorkspaceResponse>>> listWorkspaces(@AuthenticationPrincipal UserPrincipal principal) {
        List<WorkspaceResponse> workspaces = workspaceService.getWorkspacesForUser(principal.getId());
        return ResponseEntity.ok(ApiResponse.success(workspaces));
    }

    @PostMapping
    @Operation(summary = "Create Workspace", description = "Creates a new workspace container within the caller's organization.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Workspace created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Slug conflict")
    })
    public ResponseEntity<ApiResponse<WorkspaceResponse>> createWorkspace(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateWorkspaceRequest request) {
        WorkspaceResponse workspace = workspaceService.createWorkspace(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(workspace));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Workspace by ID", description = "Retrieves workspace details if caller is a verified member.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Workspace details"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not a member"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Workspace not found")
    })
    public ResponseEntity<ApiResponse<WorkspaceResponse>> getWorkspace(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        WorkspaceResponse workspace = workspaceService.getWorkspaceById(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(workspace));
    }

    @PostMapping("/{id}/members")
    @Operation(summary = "Add Workspace Member", description = "Grants workspace access to a registered user with an assigned RBAC role. (Requires OWNER or ADMIN).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Member added"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "User is already a member")
    })
    public ResponseEntity<ApiResponse<MemberResponse>> addMember(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AddMemberRequest request) {
        MemberResponse member = workspaceService.addMember(id, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(member));
    }

    @GetMapping("/{id}/members")
    @Operation(summary = "List Workspace Members", description = "Lists all members and their roles for the specified workspace.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Members list"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not a member")
    })
    public ResponseEntity<ApiResponse<List<MemberResponse>>> listMembers(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<MemberResponse> members = workspaceService.getWorkspaceMembers(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(members));
    }
}
