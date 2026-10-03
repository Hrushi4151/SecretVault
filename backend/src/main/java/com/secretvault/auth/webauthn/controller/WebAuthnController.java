package com.secretvault.auth.webauthn.controller;

import com.secretvault.auth.dto.AuthResponse;
import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.auth.webauthn.dto.WebAuthnAuthenticationOptionsRequest;
import com.secretvault.auth.webauthn.dto.WebAuthnAuthenticationOptionsResponse;
import com.secretvault.auth.webauthn.dto.WebAuthnAuthenticationVerifyRequest;
import com.secretvault.auth.webauthn.dto.WebAuthnCredentialResponse;
import com.secretvault.auth.webauthn.dto.WebAuthnCredentialUpdateRequest;
import com.secretvault.auth.webauthn.dto.WebAuthnRegistrationOptionsResponse;
import com.secretvault.auth.webauthn.dto.WebAuthnRegistrationVerifyRequest;
import com.secretvault.auth.webauthn.service.WebAuthnService;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.common.ratelimit.RateLimitIdentifierType;
import com.secretvault.common.ratelimit.RateLimited;
import com.secretvault.common.exception.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller exposing WebAuthn / FIDO2 Passkey ceremonies and credential lifecycle management.
 * Protected endpoints require user authentication; public endpoints are rate limited and protected by no-store cache headers.
 */
@RestController
@RequestMapping("/api/v1/auth/webauthn")
@Tag(name = "WebAuthn & Passkeys", description = "Endpoints for FIDO2 Passkey registration, authentication, and credential management")
public class WebAuthnController {

    private final WebAuthnService webAuthnService;

    public WebAuthnController(WebAuthnService webAuthnService) {
        this.webAuthnService = webAuthnService;
    }

    @PostMapping("/registration/options")
    @Operation(summary = "Start WebAuthn credential registration", description = "Generates PublicKeyCredentialCreationOptions for the authenticated user")
    @SecurityRequirement(name = "bearerAuth")
    @RateLimited(
            category = "webauthn_reg_options",
            limit = 10,
            windowSeconds = 60,
            type = RateLimitIdentifierType.USER_ID,
            message = "Too many registration options requests. Please try again later."
    )
    public ResponseEntity<WebAuthnRegistrationOptionsResponse> startRegistration(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(name = "friendlyName", required = false) String friendlyName
    ) {
        if (principal == null) {
            throw ApiException.unauthorized("Authentication required");
        }

        WebAuthnRegistrationOptionsResponse response = webAuthnService.startRegistration(
                principal.getId(),
                principal.getSessionIdentifier(),
                friendlyName
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @PostMapping("/registration/verify")
    @Operation(summary = "Finish WebAuthn credential registration", description = "Verifies attestation response and persists the public credential")
    @SecurityRequirement(name = "bearerAuth")
    @RateLimited(
            category = "webauthn_reg_verify",
            limit = 10,
            windowSeconds = 60,
            type = RateLimitIdentifierType.USER_ID,
            message = "Too many registration verify requests. Please try again later."
    )
    public ResponseEntity<WebAuthnCredentialResponse> finishRegistration(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody WebAuthnRegistrationVerifyRequest request
    ) {
        if (principal == null) {
            throw ApiException.unauthorized("Authentication required");
        }

        WebAuthnCredentialResponse response = webAuthnService.finishRegistration(
                principal.getId(),
                principal.getSessionIdentifier(),
                request.challengeId(),
                request.friendlyName(),
                request.credentialJson()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @PostMapping("/authentication/options")
    @Operation(summary = "Start WebAuthn authentication ceremony", description = "Generates AssertionRequest / PublicKeyCredentialRequestOptions for passkey login")
    @RateLimited(
            category = "webauthn_auth_options",
            limit = 20,
            windowSeconds = 60,
            type = RateLimitIdentifierType.IP,
            message = "Too many authentication options requests. Please try again later."
    )
    public ResponseEntity<WebAuthnAuthenticationOptionsResponse> startAuthentication(
            @RequestBody(required = false) WebAuthnAuthenticationOptionsRequest request
    ) {
        String email = request != null ? request.email() : null;
        WebAuthnAuthenticationOptionsResponse response = webAuthnService.startAuthentication(email);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @PostMapping("/authentication/verify")
    @Operation(summary = "Finish WebAuthn authentication ceremony", description = "Verifies assertion response, establishes session, and issues tokens")
    @RateLimited(
            category = "webauthn_auth_verify",
            limit = 20,
            windowSeconds = 60,
            type = RateLimitIdentifierType.IP,
            message = "Too many authentication verify requests. Please try again later."
    )
    public ResponseEntity<AuthResponse> finishAuthentication(
            @Valid @RequestBody WebAuthnAuthenticationVerifyRequest request
    ) {
        AuthResponse response = webAuthnService.finishAuthentication(
                request.challengeId(),
                request.credentialJson()
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping("/credentials")
    @Operation(summary = "List registered WebAuthn credentials", description = "Returns safe public metadata for all active passkeys registered to the user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<WebAuthnCredentialResponse>> listCredentials(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            throw ApiException.unauthorized("Authentication required");
        }

        List<WebAuthnCredentialResponse> credentials = webAuthnService.listCredentials(principal.getId());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(credentials);
    }

    @PatchMapping("/credentials/{credentialId}")
    @Operation(summary = "Rename a WebAuthn credential", description = "Updates friendly name for a registered passkey")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<WebAuthnCredentialResponse> renameCredential(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID credentialId,
            @Valid @RequestBody WebAuthnCredentialUpdateRequest request
    ) {
        if (principal == null) {
            throw ApiException.unauthorized("Authentication required");
        }

        WebAuthnCredentialResponse updated = webAuthnService.renameCredential(
                principal.getId(),
                credentialId,
                request.friendlyName()
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(updated);
    }

    @DeleteMapping("/credentials/{credentialId}")
    @Operation(summary = "Revoke a WebAuthn credential", description = "Revokes a registered passkey with lockout prevention checks")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> revokeCredential(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID credentialId
    ) {
        if (principal == null) {
            throw ApiException.unauthorized("Authentication required");
        }

        webAuthnService.revokeCredential(principal.getId(), credentialId);

        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
