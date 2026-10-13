package com.secretvault.auth.controller;

import com.secretvault.auth.dto.AuthResponse;
import com.secretvault.auth.dto.LoginRequest;
import com.secretvault.auth.dto.ForgotPasswordRequest;
import com.secretvault.auth.dto.PasswordResetStatusResponse;
import com.secretvault.auth.dto.RefreshTokenRequest;
import com.secretvault.auth.dto.RegisterRequest;
import com.secretvault.auth.dto.ResetPasswordRequest;
import com.secretvault.auth.dto.SendRegistrationOtpRequest;
import com.secretvault.auth.dto.UserResponse;
import com.secretvault.auth.dto.VerifyPasswordResetMfaRequest;
import com.secretvault.auth.dto.VerifyPasswordResetOtpRequest;
import com.secretvault.auth.dto.VerifyRegistrationOtpRequest;
import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.auth.service.AuthService;
import com.secretvault.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication and Session Control API.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Endpoints for user registration, authentication, token refresh, and session management")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/otp-preview")
    @Operation(summary = "Get OTP Preview & Diagnostics", description = "Retrieves latest dispatched OTP and delivery status.")
    public ResponseEntity<ApiResponse<com.secretvault.common.email.EmailService.OtpPreview>> getOtpPreview(
            @org.springframework.web.bind.annotation.RequestParam String email,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "reg") String type) {
        return ResponseEntity.ok(ApiResponse.success(authService.getOtpPreview(email, type)));
    }

    @PostMapping("/register/send-otp")
    @com.secretvault.common.ratelimit.RateLimited(
            category = "auth_reg_otp",
            limit = 5,
            windowSeconds = 60,
            type = com.secretvault.common.ratelimit.RateLimitIdentifierType.IP,
            failClosed = true,
            message = "Too many registration verification attempts. Please try again later."
    )
    @Operation(summary = "Send Registration OTP", description = "Dispatches a 6-digit verification code to the specified Gmail address.")
    public ResponseEntity<ApiResponse<Void>> sendRegistrationOtp(@Valid @RequestBody SendRegistrationOtpRequest request) {
        authService.sendRegistrationOtp(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/register/verify-otp")
    @com.secretvault.common.ratelimit.RateLimited(
            category = "auth_reg_verify",
            limit = 10,
            windowSeconds = 60,
            type = com.secretvault.common.ratelimit.RateLimitIdentifierType.IP,
            failClosed = true,
            message = "Too many verification attempts. Please try again later."
    )
    @Operation(summary = "Verify Registration OTP & Create Account", description = "Verifies the 6-digit code sent to Gmail and activates the user account.")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyRegistrationOtp(@Valid @RequestBody VerifyRegistrationOtpRequest request) {
        AuthResponse response = authService.verifyRegistrationOtp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/forgot-password/request")
    @com.secretvault.common.ratelimit.RateLimited(
            category = "auth_pwd_reset_req",
            limit = 5,
            windowSeconds = 60,
            type = com.secretvault.common.ratelimit.RateLimitIdentifierType.IP,
            failClosed = true,
            message = "Too many password reset requests. Please try again later."
    )
    @Operation(summary = "Request Password Reset OTP", description = "Sends a 6-digit reset code to the registered Gmail account.")
    public ResponseEntity<ApiResponse<Void>> requestPasswordReset(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/forgot-password/verify-otp")
    @com.secretvault.common.ratelimit.RateLimited(
            category = "auth_pwd_reset_otp",
            limit = 10,
            windowSeconds = 60,
            type = com.secretvault.common.ratelimit.RateLimitIdentifierType.IP,
            failClosed = true,
            message = "Too many verification attempts. Please try again later."
    )
    @Operation(summary = "Verify Password Reset OTP", description = "Validates the OTP sent to Gmail and checks if MFA is enforced.")
    public ResponseEntity<ApiResponse<PasswordResetStatusResponse>> verifyPasswordResetOtp(@Valid @RequestBody VerifyPasswordResetOtpRequest request) {
        PasswordResetStatusResponse response = authService.verifyPasswordResetOtp(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/forgot-password/verify-mfa")
    @com.secretvault.common.ratelimit.RateLimited(
            category = "auth_pwd_reset_mfa",
            limit = 10,
            windowSeconds = 60,
            type = com.secretvault.common.ratelimit.RateLimitIdentifierType.IP,
            failClosed = true,
            message = "Too many MFA attempts. Please try again later."
    )
    @Operation(summary = "Verify Step-Up MFA for Password Reset", description = "Validates TOTP or recovery code if user has MFA enabled.")
    public ResponseEntity<ApiResponse<PasswordResetStatusResponse>> verifyPasswordResetMfa(@Valid @RequestBody VerifyPasswordResetMfaRequest request) {
        PasswordResetStatusResponse response = authService.verifyPasswordResetMfa(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/forgot-password/reset")
    @com.secretvault.common.ratelimit.RateLimited(
            category = "auth_pwd_reset",
            limit = 5,
            windowSeconds = 60,
            type = com.secretvault.common.ratelimit.RateLimitIdentifierType.IP,
            failClosed = true,
            message = "Too many password reset attempts. Please try again later."
    )
    @Operation(summary = "Reset Password", description = "Sets new password and revokes all active user sessions.")
    public ResponseEntity<ApiResponse<PasswordResetStatusResponse>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        PasswordResetStatusResponse response = authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/register")
    @Operation(summary = "Register User & Bootstrap Organization", description = "Creates a new user account, personal organization, default workspace, and issues authentication tokens.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User registered successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already in use")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/login")
    @com.secretvault.common.ratelimit.RateLimited(
            category = "auth_login",
            limit = 10,
            windowSeconds = 60,
            type = com.secretvault.common.ratelimit.RateLimitIdentifierType.IP,
            failClosed = true,
            message = "Too many login attempts. Please try again later."
    )
    @Operation(summary = "Authenticate User", description = "Validates credentials and issues access and refresh tokens.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authentication successful"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/refresh")
    @com.secretvault.common.ratelimit.RateLimited(
            category = "auth_refresh",
            limit = 30,
            windowSeconds = 60,
            type = com.secretvault.common.ratelimit.RateLimitIdentifierType.IP,
            message = "Too many token refresh attempts. Please try again later."
    )
    @Operation(summary = "Rotate & Refresh Token", description = "Exchanges a valid refresh token for a newly rotated access and refresh token pair.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Get Current User Profile", description = "Retrieves profile information for the authenticated caller.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        UserResponse response = authService.getCurrentUser(principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Sign Out & Invalidate Session", description = "Revokes active session and refresh tokens for the authenticated user.")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal UserPrincipal principal) {
        authService.logout(principal.getId(), principal.getSessionIdentifier());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
