package com.secretvault.workspace.invitation.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.workspace.dto.WorkspaceResponse;
import com.secretvault.workspace.invitation.dto.AcceptInvitationRequest;
import com.secretvault.workspace.invitation.dto.CreateInvitationRequest;
import com.secretvault.workspace.invitation.dto.InvitationResponse;
import com.secretvault.workspace.invitation.service.WorkspaceInvitationService;
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
import java.util.Map;
import java.util.UUID;

/**
 * REST API for Workspace Invitations and Onboarding.
 */
@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Workspace Invitations", description = "Endpoints for inviting members and token acceptance")
public class WorkspaceInvitationController {

    private final WorkspaceInvitationService invitationService;

    public WorkspaceInvitationController(WorkspaceInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/workspaces/{workspaceId}/invitations")
    @Operation(summary = "Invite Member to Workspace", description = "Generates a single-use cryptographically random invitation token. (Requires OWNER or ADMIN).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Invitation created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "User already a member or pending invitation exists")
    })
    public ResponseEntity<ApiResponse<InvitationResponse>> createInvitation(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateInvitationRequest request) {
        InvitationResponse invitation = invitationService.createInvitation(workspaceId, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(invitation));
    }

    @GetMapping("/workspaces/{workspaceId}/invitations")
    @Operation(summary = "List Pending Invitations", description = "Retrieves all active pending invitations for the workspace.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pending invitations list"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not a member of the workspace")
    })
    public ResponseEntity<ApiResponse<List<InvitationResponse>>> listPendingInvitations(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<InvitationResponse> invitations = invitationService.getPendingInvitations(workspaceId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(invitations));
    }

    @PostMapping("/workspaces/{workspaceId}/invitations/{invitationId}/revoke")
    @Operation(summary = "Revoke Workspace Invitation", description = "Cancels a pending invitation. (Requires OWNER or ADMIN).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invitation revoked"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invitation not found")
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> revokeInvitation(
            @PathVariable UUID workspaceId,
            @PathVariable UUID invitationId,
            @AuthenticationPrincipal UserPrincipal principal) {
        invitationService.revokeInvitation(workspaceId, invitationId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Invitation successfully revoked", "invitationId", invitationId)));
    }

    @PostMapping("/invitations/accept")
    @Operation(summary = "Accept Workspace Invitation", description = "Consumes a one-time invitation token to join the target workspace.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invitation accepted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Workspace not found")
    })
    public ResponseEntity<ApiResponse<WorkspaceResponse>> acceptInvitation(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AcceptInvitationRequest request) {
        WorkspaceResponse workspace = invitationService.acceptInvitation(request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(workspace));
    }
}
