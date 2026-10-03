package com.secretvault.auth.stepup.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.auth.stepup.dto.PasswordStepUpRequest;
import com.secretvault.auth.stepup.dto.RecoveryCodeStepUpRequest;
import com.secretvault.auth.stepup.dto.StepUpChallengeRequest;
import com.secretvault.auth.stepup.dto.StepUpChallengeResponse;
import com.secretvault.auth.stepup.dto.StepUpProofResponse;
import com.secretvault.auth.stepup.dto.TotpStepUpRequest;
import com.secretvault.auth.stepup.service.StepUpAuthenticationService;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.common.ratelimit.RateLimitIdentifierType;
import com.secretvault.common.ratelimit.RateLimited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Generalized Step-Up Authentication:
 * - Creates contextual step-up challenges
 * - Verifies strong authentication factors (Password, TOTP, Recovery Code)
 * - Returns short-lived, server-authoritative step-up proof tokens
 */
@RestController
@RequestMapping("/api/v1/auth/step-up")
@Tag(name = "Step-Up Authentication", description = "Endpoints for step-up re-authentication challenges and factor verification")
public class StepUpController {

    private final StepUpAuthenticationService stepUpService;

    public StepUpController(StepUpAuthenticationService stepUpService) {
        this.stepUpService = stepUpService;
    }

    @PostMapping("/challenges")
    @SecurityRequirement(name = "BearerAuth")
    @RateLimited(
            category = "step_up_challenge",
            limit = 10,
            windowSeconds = 60,
            type = RateLimitIdentifierType.USER_ID,
            message = "Too many step-up challenge requests. Please try again later."
    )
    @Operation(summary = "Create Step-Up Challenge", description = "Creates a short-lived step-up re-authentication challenge bound to the caller's session and action context.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Step-up challenge created successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized or inactive session"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden: user lacks base permission for the requested action")
    })
    public ResponseEntity<ApiResponse<StepUpChallengeResponse>> createChallenge(
            @Valid @RequestBody StepUpChallengeRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        StepUpChallengeResponse response = stepUpService.createChallenge(
                principal.getId(),
                principal.getSessionIdentifier(),
                request.action(),
                request.context()
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response));
    }

    @PostMapping("/challenges/{challengeId}/verify-password")
    @SecurityRequirement(name = "BearerAuth")
    @RateLimited(
            category = "step_up_verify_pwd",
            limit = 5,
            windowSeconds = 60,
            type = RateLimitIdentifierType.USER_ID,
            message = "Too many password verification attempts. Please try again later."
    )
    @Operation(summary = "Verify Password Step-Up", description = "Verifies user password against an active step-up challenge and issues a short-lived proof token.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password verified successfully and step-up proof issued"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid challenge or parameters"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid password or session mismatch")
    })
    public ResponseEntity<ApiResponse<StepUpProofResponse>> verifyPassword(
            @PathVariable String challengeId,
            @Valid @RequestBody PasswordStepUpRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        StepUpProofResponse response = stepUpService.verifyPassword(
                challengeId,
                principal.getId(),
                principal.getSessionIdentifier(),
                request.password()
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response));
    }

    @PostMapping("/challenges/{challengeId}/verify-totp")
    @SecurityRequirement(name = "BearerAuth")
    @RateLimited(
            category = "step_up_verify_totp",
            limit = 5,
            windowSeconds = 60,
            type = RateLimitIdentifierType.USER_ID,
            message = "Too many TOTP verification attempts. Please try again later."
    )
    @Operation(summary = "Verify TOTP Step-Up", description = "Verifies TOTP authenticator code against an active step-up challenge and issues a short-lived proof token.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "TOTP verified successfully and step-up proof issued"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid code or challenge"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized or session mismatch")
    })
    public ResponseEntity<ApiResponse<StepUpProofResponse>> verifyTotp(
            @PathVariable String challengeId,
            @Valid @RequestBody TotpStepUpRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        StepUpProofResponse response = stepUpService.verifyTotp(
                challengeId,
                principal.getId(),
                principal.getSessionIdentifier(),
                request.code()
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response));
    }

    @PostMapping("/challenges/{challengeId}/verify-recovery-code")
    @SecurityRequirement(name = "BearerAuth")
    @RateLimited(
            category = "step_up_verify_recovery",
            limit = 5,
            windowSeconds = 60,
            type = RateLimitIdentifierType.USER_ID,
            message = "Too many recovery code verification attempts. Please try again later."
    )
    @Operation(summary = "Verify Recovery Code Step-Up", description = "Verifies and consumes a backup recovery code against an active step-up challenge and issues a short-lived proof token.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Recovery code verified successfully and step-up proof issued"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or consumed recovery code"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized or session mismatch")
    })
    public ResponseEntity<ApiResponse<StepUpProofResponse>> verifyRecoveryCode(
            @PathVariable String challengeId,
            @Valid @RequestBody RecoveryCodeStepUpRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        StepUpProofResponse response = stepUpService.verifyRecoveryCode(
                challengeId,
                principal.getId(),
                principal.getSessionIdentifier(),
                request.recoveryCode()
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response));
    }
}
