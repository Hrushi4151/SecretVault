package com.secretvault.access.controller;

import com.secretvault.access.dto.MemberAccessOverviewResponse;
import com.secretvault.access.dto.UpdateMemberAccessRequest;
import com.secretvault.access.service.MemberAccessService;
import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST API for configuring a workspace member's multi-project, multi-environment, and granular access.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/members/{userId}/access")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Member Access Management", description = "Endpoints for configuring per-member project, environment, and permission matrices")
public class MemberAccessController {

    private final MemberAccessService memberAccessService;

    public MemberAccessController(MemberAccessService memberAccessService) {
        this.memberAccessService = memberAccessService;
    }

    @GetMapping
    @Operation(summary = "Get Member Access Overview", description = "Retrieves a member's complete project and environment access hierarchy, granular grants, and JIT sessions.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Member access overview"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Workspace or member not found")
    })
    public ResponseEntity<ApiResponse<MemberAccessOverviewResponse>> getMemberAccess(
            @PathVariable UUID workspaceId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        MemberAccessOverviewResponse response = memberAccessService.getMemberAccess(workspaceId, userId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping
    @Operation(summary = "Update Member Access Configuration", description = "Atomically configures scoped project and environment access levels for a member. (Requires OWNER or ADMIN).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Member access updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid hierarchy or bad request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Workspace, member, project, or environment not found")
    })
    public ResponseEntity<ApiResponse<MemberAccessOverviewResponse>> updateMemberAccess(
            @PathVariable UUID workspaceId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateMemberAccessRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        MemberAccessOverviewResponse response = memberAccessService.updateMemberAccess(workspaceId, userId, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Member access configuration saved successfully"));
    }
}
