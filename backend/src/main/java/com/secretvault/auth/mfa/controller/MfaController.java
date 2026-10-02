package com.secretvault.auth.mfa.controller;

import com.secretvault.auth.dto.AuthResponse;
import com.secretvault.auth.mfa.dto.MfaActivateRequest;
import com.secretvault.auth.mfa.dto.MfaActivateResponse;
import com.secretvault.auth.mfa.dto.MfaDisableRequest;
import com.secretvault.auth.mfa.dto.MfaEnrollResponse;
import com.secretvault.auth.mfa.dto.MfaRecoveryVerifyRequest;
import com.secretvault.auth.mfa.dto.MfaStatusResponse;
import com.secretvault.auth.mfa.dto.MfaTotpVerifyRequest;
import com.secretvault.auth.mfa.model.MfaActivationResult;
import com.secretvault.auth.mfa.model.MfaEnrollmentResponse;
import com.secretvault.auth.mfa.model.MfaStatusInfo;
import com.secretvault.auth.mfa.service.MfaService;
import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.auth.service.AuthService;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.common.ratelimit.RateLimitIdentifierType;
import com.secretvault.common.ratelimit.RateLimited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for Multi-Factor Authentication (MFA) operations:
 * - Pre-auth MFA challenge verification (TOTP & Recovery Code)
 * - Post-auth MFA lifecycle management (Enrollment, Activation, Status, Disable)
 */
@RestController
@RequestMapping("/api/v1/auth/mfa")
@Tag(name = "Multi-Factor Authentication", description = "Endpoints for MFA enrollment, activation, challenge verification, and status")
public class MfaController {

    private final MfaService mfaService;
    private final AuthService authService;

    public MfaController(MfaService mfaService, AuthService authService) {
        this.mfaService = mfaService;
        this.authService = authService;
    }

    @GetMapping("/status")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Get MFA Status", description = "Retrieves the current authenticated user's MFA enrollment status and safe metadata.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "MFA status retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<MfaStatusResponse>> getStatus(@AuthenticationPrincipal UserPrincipal principal) {
        MfaStatusInfo info = mfaService.getStatus(principal.getId());
        return ResponseEntity.ok(ApiResponse.success(MfaStatusResponse.from(info)));
    }

    @PostMapping("/enroll")
    @SecurityRequirement(name = "BearerAuth")
    @RateLimited(
            category = "mfa_enroll",
            limit = 5,
            windowSeconds = 60,
            type = RateLimitIdentifierType.USER_ID,
            message = "Too many MFA enrollment attempts. Please try again later."
    )
    @Operation(summary = "Initiate MFA Enrollment", description = "Generates a new TOTP secret key and provisioning URI for authenticator app configuration.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "MFA enrollment initiated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "MFA is already active or user invalid"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<MfaEnrollResponse>> enroll(@AuthenticationPrincipal UserPrincipal principal) {
        MfaEnrollmentResponse response = mfaService.beginEnrollment(principal.getId());
        return ResponseEntity.ok(ApiResponse.success(MfaEnrollResponse.from(response)));
    }

    @PostMapping("/activate")
    @SecurityRequirement(name = "BearerAuth")
    @RateLimited(
            category = "mfa_activate",
            limit = 10,
            windowSeconds = 60,
            type = RateLimitIdentifierType.USER_ID,
            message = "Too many MFA activation attempts. Please try again later."
    )
    @Operation(summary = "Activate MFA", description = "Verifies initial code from authenticator app, activates MFA, and generates 10 single-use backup recovery codes.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "MFA activated successfully and recovery codes generated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid code or enrollment state"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<MfaActivateResponse>> activate(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MfaActivateRequest request) {
        MfaActivationResult result = mfaService.activateMfa(principal.getId(), request.code());
        return ResponseEntity.ok(ApiResponse.success(MfaActivateResponse.from(result)));
    }

    @PostMapping("/verify-totp")
    @RateLimited(
            category = "mfa_verify",
            limit = 10,
            windowSeconds = 60,
            type = RateLimitIdentifierType.IP,
            message = "Too many MFA verification attempts. Please try again later."
    )
    @Operation(summary = "Verify TOTP Login Challenge", description = "Validates a time-based verification code against an active MFA challenge and issues session tokens.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "MFA challenge verified successfully and session tokens issued"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid or expired challenge / code")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> verifyTotp(@Valid @RequestBody MfaTotpVerifyRequest request) {
        AuthResponse response = authService.completeMfaTotpLogin(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/verify-recovery")
    @RateLimited(
            category = "mfa_recovery_verify",
            limit = 10,
            windowSeconds = 60,
            type = RateLimitIdentifierType.IP,
            message = "Too many recovery code verification attempts. Please try again later."
    )
    @Operation(summary = "Verify Recovery Code Login Challenge", description = "Validates a single-use backup recovery code against an active MFA challenge and issues session tokens.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Recovery code verified successfully and session tokens issued"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid, expired, or already-used recovery code / challenge")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> verifyRecovery(@Valid @RequestBody MfaRecoveryVerifyRequest request) {
        AuthResponse response = authService.completeMfaRecoveryLogin(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/disable")
    @SecurityRequirement(name = "BearerAuth")
    @RateLimited(
            category = "mfa_disable",
            limit = 5,
            windowSeconds = 60,
            type = RateLimitIdentifierType.USER_ID,
            message = "Too many MFA disable attempts. Please try again later."
    )
    @Operation(summary = "Disable MFA", description = "Disables MFA protection and revokes all active backup recovery codes for the authenticated user.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "MFA disabled successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<Void>> disable(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) MfaDisableRequest request) {
        mfaService.disableMfa(principal.getId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
