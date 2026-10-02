package com.secretvault.auth.session.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.auth.session.dto.SessionResponse;
import com.secretvault.auth.session.service.SessionService;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.common.ratelimit.RateLimitIdentifierType;
import com.secretvault.common.ratelimit.RateLimited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for Authenticated Human User Session Management & Security Governance.
 * Exposes endpoints for session discovery, current-session identification,
 * single session revocation, and bulk session termination.
 */
@RestController
@RequestMapping("/api/v1/auth/sessions")
@Tag(name = "Session Security", description = "Endpoints for server-side user session visibility, device management, and cryptographic revocation")
@SecurityRequirement(name = "BearerAuth")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    @RateLimited(
            category = "auth_sessions_list",
            limit = 60,
            windowSeconds = 60,
            type = RateLimitIdentifierType.USER_ID,
            message = "Too many session list requests. Please try again later."
    )
    @Operation(summary = "List User Sessions", description = "Retrieves all active and historical sessions for the authenticated user, indicating which session is the caller's current session.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Sessions listed successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<List<SessionResponse>>> listSessions(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<SessionResponse> sessions = sessionService.listUserSessions(
                principal.getId(),
                principal.getSessionIdentifier()
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(sessions));
    }

    @DeleteMapping("/{sessionId}")
    @RateLimited(
            category = "auth_session_revoke",
            limit = 30,
            windowSeconds = 60,
            type = RateLimitIdentifierType.USER_ID,
            message = "Too many session revocation requests. Please try again later."
    )
    @Operation(summary = "Revoke Specific Session", description = "Revokes a designated session by public identifier and invalidates its refresh token chain.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Session revoked successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid session identifier"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session not found or belongs to another user")
    })
    public ResponseEntity<ApiResponse<Void>> revokeSession(
            @AuthenticationPrincipal UserPrincipal principal,
            @Parameter(description = "Public session identifier (e.g., sess_...)") @PathVariable("sessionId") String sessionId) {
        sessionService.revokeSession(principal.getId(), sessionId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(null));
    }

    @PostMapping("/revoke-others")
    @RateLimited(
            category = "auth_session_revoke_others",
            limit = 15,
            windowSeconds = 60,
            type = RateLimitIdentifierType.USER_ID,
            message = "Too many bulk session revocation requests. Please try again later."
    )
    @Operation(summary = "Revoke All Other Sessions", description = "Revokes all other active sessions and refresh tokens for the user while preserving the caller's current session.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Other sessions revoked successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Current session identifier missing"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<Void>> revokeAllOtherSessions(
            @AuthenticationPrincipal UserPrincipal principal) {
        sessionService.revokeAllOtherSessions(principal.getId(), principal.getSessionIdentifier());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(null));
    }

    @PostMapping("/revoke-all")
    @RateLimited(
            category = "auth_session_revoke_all",
            limit = 10,
            windowSeconds = 60,
            type = RateLimitIdentifierType.USER_ID,
            message = "Too many global session revocation requests. Please try again later."
    )
    @Operation(summary = "Revoke All Sessions", description = "Globally revokes all active sessions and refresh tokens for the authenticated user.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "All sessions revoked successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<Void>> revokeAllSessions(
            @AuthenticationPrincipal UserPrincipal principal) {
        sessionService.revokeAllSessions(principal.getId());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(null));
    }
}
