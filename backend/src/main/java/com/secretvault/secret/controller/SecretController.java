package com.secretvault.secret.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.common.exception.ApiException;
import com.secretvault.secret.dto.CreateSecretRequest;
import com.secretvault.secret.dto.SecretMetadataResponse;
import com.secretvault.secret.dto.SecretRevealResponse;
import com.secretvault.secret.dto.SecretVersionResponse;
import com.secretvault.secret.dto.UpdateSecretRequest;
import com.secretvault.secret.entity.SecretStatus;
import com.secretvault.secret.service.SecretService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for secret management and explicit in-memory reveals.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}/secrets")
@Tag(name = "Secrets", description = "Secret lifecycle management and explicit envelope encryption reveal APIs")
@SecurityRequirement(name = "BearerAuth")
public class SecretController {

    private final SecretService secretService;

    public SecretController(SecretService secretService) {
        this.secretService = secretService;
    }

    @GetMapping
    @Operation(summary = "List secret metadata in an environment (never returns plaintext)")
    public ResponseEntity<ApiResponse<List<SecretMetadataResponse>>> getSecrets(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) SecretStatus status,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        List<SecretMetadataResponse> secrets = secretService.getSecrets(
                workspaceId, projectId, environmentId, search, status, principal.getId()
        );
        return ResponseEntity.ok(ApiResponse.success(secrets));
    }

    @GetMapping("/{secretId}")
    @Operation(summary = "Get secret metadata by ID")
    public ResponseEntity<ApiResponse<SecretMetadataResponse>> getSecretById(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        SecretMetadataResponse response = secretService.getSecretById(
                workspaceId, projectId, environmentId, secretId, principal.getId()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{secretId}/versions")
    @Operation(summary = "Get immutable version history for a secret")
    public ResponseEntity<ApiResponse<List<SecretVersionResponse>>> getSecretVersions(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        List<SecretVersionResponse> versions = secretService.getSecretVersions(
                workspaceId, projectId, environmentId, secretId, principal.getId()
        );
        return ResponseEntity.ok(ApiResponse.success(versions));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new secret and persist version 1")
    public ResponseEntity<ApiResponse<SecretMetadataResponse>> createSecret(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @Valid @RequestBody CreateSecretRequest request,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        String requestId = getRequestId();
        String ipAddress = servletRequest.getRemoteAddr();

        SecretMetadataResponse response = secretService.createSecret(
                workspaceId, projectId, environmentId, request, principal.getId(), requestId, ipAddress
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PatchMapping("/{secretId}")
    @Operation(summary = "Update secret metadata or append new version")
    public ResponseEntity<ApiResponse<SecretMetadataResponse>> updateSecret(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @Valid @RequestBody UpdateSecretRequest request,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        String requestId = getRequestId();
        String ipAddress = servletRequest.getRemoteAddr();

        SecretMetadataResponse response = secretService.updateSecret(
                workspaceId, projectId, environmentId, secretId, request, principal.getId(), requestId, ipAddress
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{secretId}/reveal")
    @Operation(summary = "Explicit reveal endpoint decrypting secret in-memory")
    public ResponseEntity<ApiResponse<SecretRevealResponse>> revealSecret(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @RequestParam(required = false) Integer version,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        String requestId = getRequestId();
        String ipAddress = servletRequest.getRemoteAddr();

        SecretRevealResponse response = secretService.revealSecret(
                workspaceId, projectId, environmentId, secretId, version, principal.getId(), requestId, ipAddress
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, private");
        headers.set(HttpHeaders.PRAGMA, "no-cache");
        headers.set(HttpHeaders.EXPIRES, "0");

        return ResponseEntity.ok().headers(headers).body(ApiResponse.success(response));
    }

    @DeleteMapping("/{secretId}")
    @Operation(summary = "Soft-delete a secret")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteSecret(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        String requestId = getRequestId();
        String ipAddress = servletRequest.getRemoteAddr();

        secretService.deleteSecret(
                workspaceId, projectId, environmentId, secretId, principal.getId(), requestId, ipAddress
        );
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Secret successfully deleted", "secretId", secretId)));
    }

    private void validateWorkspaceHeader(UUID pathWorkspaceId, UUID headerWorkspaceId) {
        if (headerWorkspaceId != null && !pathWorkspaceId.equals(headerWorkspaceId)) {
            throw ApiException.badRequest("X-Workspace-ID header does not match path variable workspace ID");
        }
    }

    private String getRequestId() {
        String requestId = MDC.get("correlationId");
        return StringUtils.hasText(requestId) ? requestId : UUID.randomUUID().toString();
    }
}
