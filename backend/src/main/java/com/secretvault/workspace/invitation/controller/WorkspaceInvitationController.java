package com.secretvault.workspace.invitation.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.workspace.dto.WorkspaceResponse;
import com.secretvault.workspace.invitation.dto.AcceptInvitationRequest;
import com.secretvault.workspace.invitation.dto.CreateInvitationRequest;
import com.secretvault.workspace.invitation.dto.InvitationResponse;
import com.secretvault.workspace.invitation.dto.UserInvitationsListResponse;
import com.secretvault.workspace.invitation.dto.UserLookupResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for Workspace Invitations, In-App Delivery, User Lookup, and Member Onboarding.
 */
@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Workspace Invitations", description = "Endpoints for inviting members, debounced user lookup, and in-app invitation delivery/acceptance")
public class WorkspaceInvitationController {

    private final WorkspaceInvitationService invitationService;

    public WorkspaceInvitationController(WorkspaceInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping("/workspaces/{workspaceId}/invitations/lookup")
    @Operation(summary = "Lookup User for Workspace Invitation", description = "Checks whether an email belongs to an existing user and inspects membership/pending state. (Requires OWNER or ADMIN).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Lookup result"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions to lookup users")
    })
    public ResponseEntity<ApiResponse<UserLookupResponse>> lookupUser(
            @PathVariable UUID workspaceId,
            @RequestParam String email,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserLookupResponse result = invitationService.lookupUser(workspaceId, email, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/users/lookup")
    @Operation(summary = "Lookup User by Email", description = "General user lookup by email for authenticated members.")
    public ResponseEntity<ApiResponse<UserLookupResponse>> lookupUserGlobal(
            @RequestParam String email,
            @RequestParam(required = false) UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (workspaceId != null) {
            UserLookupResponse result = invitationService.lookupUser(workspaceId, email, principal.getId());
            return ResponseEntity.ok(ApiResponse.success(result));
        }
        return ResponseEntity.ok(ApiResponse.success(UserLookupResponse.notFound(false, false)));
    }

    @PostMapping("/workspaces/{workspaceId}/invitations")
    @com.secretvault.common.ratelimit.RateLimited(
            category = "invitation_create",
            limit = 20,
            windowSeconds = 60,
            type = com.secretvault.common.ratelimit.RateLimitIdentifierType.WORKSPACE_ID,
            message = "Too many invitation requests. Please try again later."
    )
    @Operation(summary = "Invite Member to Workspace", description = "Generates a single-use cryptographically random invitation token and records pending invite. (Requires OWNER or ADMIN).")
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
    @Operation(summary = "List Pending Invitations for Workspace", description = "Retrieves all active pending invitations for the workspace.")
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

    @GetMapping("/invitations/me")
    @Operation(summary = "List Pending In-App Invitations for Authenticated User", description = "Returns all active pending invitations sent to the authenticated user's email across all workspaces.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User pending invitations list")
    })
    public ResponseEntity<ApiResponse<UserInvitationsListResponse>> getMyInvitations(
            @AuthenticationPrincipal UserPrincipal principal) {
        UserInvitationsListResponse response = invitationService.getMyPendingInvitations(principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/invitations/{id}/accept")
    @com.secretvault.common.ratelimit.RateLimited(
            category = "invitation_accept",
            limit = 15,
            windowSeconds = 60,
            type = com.secretvault.common.ratelimit.RateLimitIdentifierType.USER_ID,
            message = "Too many invitation acceptance attempts. Please try again later."
    )
    @Operation(summary = "Accept In-App Invitation", description = "Accepts a pending workspace invitation in-app directly by invitation ID.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invitation accepted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired invitation"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Invitation was issued to a different user"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invitation or workspace not found")
    })
    public ResponseEntity<ApiResponse<WorkspaceResponse>> acceptInvitationById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        WorkspaceResponse workspace = invitationService.acceptInvitationById(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(workspace));
    }

    @PostMapping("/invitations/{id}/decline")
    @Operation(summary = "Decline In-App Invitation", description = "Declines a pending workspace invitation in-app directly by invitation ID.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invitation declined"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired invitation"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Invitation was issued to a different user"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Invitation not found")
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> declineInvitationById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        invitationService.declineInvitationById(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Invitation declined successfully", "invitationId", id)));
    }

    @PostMapping("/invitations/accept")
    @com.secretvault.common.ratelimit.RateLimited(
            category = "invitation_accept",
            limit = 15,
            windowSeconds = 60,
            type = com.secretvault.common.ratelimit.RateLimitIdentifierType.IP,
            message = "Too many invitation acceptance attempts. Please try again later."
    )
    @Operation(summary = "Accept Workspace Invitation with Token", description = "Consumes a one-time invitation token to join the target workspace.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invitation accepted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or expired token"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Invitation was issued to a different email"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Workspace not found")
    })
    public ResponseEntity<ApiResponse<WorkspaceResponse>> acceptInvitation(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AcceptInvitationRequest request) {
        WorkspaceResponse workspace = invitationService.acceptInvitation(request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(workspace));
    }
}
