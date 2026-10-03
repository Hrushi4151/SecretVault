package com.secretvault.secret.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.common.exception.ApiException;
import com.secretvault.secret.dto.RollbackSecretRequest;
import com.secretvault.secret.dto.SecretDiffResponse;
import com.secretvault.secret.dto.SecretRevealResponse;
import com.secretvault.secret.dto.SecretValueDiffResponse;
import com.secretvault.secret.dto.SecretVersionResponse;
import com.secretvault.secret.dto.SecretVersionTagRequest;
import com.secretvault.secret.dto.SecretVersionTagResponse;
import com.secretvault.secret.entity.VersionType;
import com.secretvault.secret.service.SecretDiffService;
import com.secretvault.secret.service.SecretRollbackService;
import com.secretvault.secret.service.SecretVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}/secrets/{secretId}")
@Tag(name = "Secret Versions", description = "Immutable secret version control, tagging, diffing, and rollback APIs")
@SecurityRequirement(name = "BearerAuth")
public class SecretVersionController {

    private final SecretVersionService versionService;
    private final SecretDiffService diffService;
    private final SecretRollbackService rollbackService;
    private final com.secretvault.secret.reveal.service.SecretRevealService secretRevealService;

    public SecretVersionController(
            SecretVersionService versionService,
            SecretDiffService diffService,
            SecretRollbackService rollbackService,
            com.secretvault.secret.reveal.service.SecretRevealService secretRevealService
    ) {
        this.versionService = versionService;
        this.diffService = diffService;
        this.rollbackService = rollbackService;
        this.secretRevealService = secretRevealService;
    }

    @GetMapping("/versions")
    @Operation(summary = "Get paginated immutable version history for a secret")
    public ResponseEntity<ApiResponse<Page<SecretVersionResponse>>> getVersions(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @RequestParam(required = false) VersionType versionType,
            @PageableDefault(size = 20, sort = "versionNumber", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        Page<SecretVersionResponse> versions = versionService.getVersions(
                workspaceId, projectId, environmentId, secretId, versionType, pageable, principal.getId()
        );
        return ResponseEntity.ok(ApiResponse.success(versions));
    }

    @GetMapping("/versions/{versionNumber}")
    @Operation(summary = "Get specific version metadata by version number")
    public ResponseEntity<ApiResponse<SecretVersionResponse>> getVersion(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @PathVariable Integer versionNumber,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        SecretVersionResponse version = versionService.getVersion(
                workspaceId, projectId, environmentId, secretId, versionNumber, principal.getId()
        );
        return ResponseEntity.ok(ApiResponse.success(version));
    }

    @PostMapping("/versions/{versionNumber}/reveal-intent")
    @com.secretvault.common.ratelimit.RateLimited(
            category = "secret_reveal_intent",
            limit = 60,
            windowSeconds = 60,
            type = com.secretvault.common.ratelimit.RateLimitIdentifierType.IP_AND_USER,
            message = "Too many reveal intent requests. Please try again later."
    )
    @Operation(summary = "Request a short-lived single-use reveal intent token for a historical version")
    public ResponseEntity<ApiResponse<com.secretvault.secret.reveal.dto.SecretRevealIntentResponse>> createHistoricalRevealIntent(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @PathVariable Integer versionNumber,
            @RequestBody(required = false) com.secretvault.secret.reveal.dto.CreateRevealIntentRequest request,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        String requestId = resolveRequestId();
        String ipAddress = servletRequest.getRemoteAddr();

        com.secretvault.secret.reveal.dto.CreateRevealIntentRequest req =
                new com.secretvault.secret.reveal.dto.CreateRevealIntentRequest(
                        versionNumber,
                        request != null ? request.reason() : null,
                        request != null ? request.stepUpProof() : null
                );

        com.secretvault.secret.reveal.dto.SecretRevealIntentResponse response = secretRevealService.createRevealIntent(
                workspaceId, projectId, environmentId, secretId, req,
                principal.getId(), principal.getSessionIdentifier(), requestId, ipAddress
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, private");
        headers.set(HttpHeaders.PRAGMA, "no-cache");
        headers.set(HttpHeaders.EXPIRES, "0");

        return ResponseEntity.ok().headers(headers).body(ApiResponse.success(response));
    }

    @PostMapping("/versions/{versionNumber}/reveal")
    @com.secretvault.common.ratelimit.RateLimited(
            category = "secret_reveal",
            limit = 60,
            windowSeconds = 60,
            type = com.secretvault.common.ratelimit.RateLimitIdentifierType.IP_AND_USER,
            message = "Too many secret reveal attempts. Please try again later."
    )
    @Operation(summary = "Explicitly decrypt and reveal a historical secret version value in memory")
    public ResponseEntity<ApiResponse<SecretRevealResponse>> revealHistoricalVersion(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @PathVariable Integer versionNumber,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @RequestHeader(value = "X-Step-Up-Proof", required = false) String stepUpProofHeader,
            @RequestHeader(value = "X-Reveal-Intent-Token", required = false) String intentTokenHeader,
            @RequestBody(required = false) com.secretvault.secret.reveal.dto.ExecuteRevealRequest requestBody,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        String requestId = resolveRequestId();
        String ipAddress = httpRequest.getRemoteAddr();

        String intentToken = (requestBody != null && StringUtils.hasText(requestBody.intentToken()))
                ? requestBody.intentToken()
                : intentTokenHeader;

        String stepUpProof = (requestBody != null && StringUtils.hasText(requestBody.stepUpProof()))
                ? requestBody.stepUpProof()
                : stepUpProofHeader;

        String reason = (requestBody != null && StringUtils.hasText(requestBody.reason()))
                ? requestBody.reason()
                : null;

        com.secretvault.secret.reveal.dto.ExecuteRevealRequest req =
                new com.secretvault.secret.reveal.dto.ExecuteRevealRequest(intentToken, versionNumber, reason, stepUpProof);

        SecretRevealResponse response = secretRevealService.revealHistoricalVersion(
                workspaceId, projectId, environmentId, secretId, versionNumber, req,
                principal.getId(), principal.getSessionIdentifier(), requestId, ipAddress
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, private");
        headers.set(HttpHeaders.PRAGMA, "no-cache");
        headers.set(HttpHeaders.EXPIRES, "0");

        return ResponseEntity.ok().headers(headers).body(ApiResponse.success(response));
    }

    @GetMapping("/versions/compare")
    @Operation(summary = "Compare two historical versions (metadata-level safe comparison)")
    public ResponseEntity<ApiResponse<SecretDiffResponse>> compareVersions(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @RequestParam Integer from,
            @RequestParam Integer to,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        SecretDiffResponse response = diffService.compareVersions(
                workspaceId, projectId, environmentId, secretId, from, to, principal.getId()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/versions/diff")
    @Operation(summary = "Compute secure value-level diff between two versions")
    public ResponseEntity<ApiResponse<SecretValueDiffResponse>> computeValueDiff(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @RequestParam Integer from,
            @RequestParam Integer to,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        SecretValueDiffResponse response = diffService.computeValueDiff(
                workspaceId, projectId, environmentId, secretId, from, to, principal.getId()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, private");
        headers.set(HttpHeaders.PRAGMA, "no-cache");

        return ResponseEntity.ok().headers(headers).body(ApiResponse.success(response));
    }

    @PostMapping("/rollback")
    @com.secretvault.common.ratelimit.RateLimited(
            category = "secret_rollback",
            limit = 20,
            windowSeconds = 60,
            type = com.secretvault.common.ratelimit.RateLimitIdentifierType.IP_AND_USER,
            message = "Too many secret rollback requests. Please try again later."
    )
    @Operation(summary = "Rollback secret to a historical version as a brand new version (vN+1)")
    public ResponseEntity<ApiResponse<SecretVersionResponse>> rollbackSecret(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @Valid @RequestBody RollbackSecretRequest request,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        String requestId = resolveRequestId();
        String ipAddress = httpRequest.getRemoteAddr();

        SecretVersionResponse response = rollbackService.rollbackSecret(
                workspaceId, projectId, environmentId, secretId, request, principal.getId(), requestId, ipAddress
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Secret rolled back successfully"));
    }

    @PostMapping("/versions/{versionNumber}/tags")
    @Operation(summary = "Add an immutable metadata tag to a secret version")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<SecretVersionTagResponse>> addTag(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @PathVariable Integer versionNumber,
            @Valid @RequestBody SecretVersionTagRequest request,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        String requestId = resolveRequestId();
        String ipAddress = httpRequest.getRemoteAddr();

        SecretVersionTagResponse tag = versionService.addTag(
                workspaceId, projectId, environmentId, secretId, versionNumber, request.name(), principal.getId(), requestId, ipAddress
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(tag, "Tag added successfully"));
    }

    @GetMapping("/versions/{versionNumber}/tags")
    @Operation(summary = "List tags on a secret version")
    public ResponseEntity<ApiResponse<List<SecretVersionTagResponse>>> getTags(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @PathVariable Integer versionNumber,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        List<SecretVersionTagResponse> tags = versionService.getTags(
                workspaceId, projectId, environmentId, secretId, versionNumber, principal.getId()
        );
        return ResponseEntity.ok(ApiResponse.success(tags));
    }

    @DeleteMapping("/versions/{versionNumber}/tags/{tagName}")
    @Operation(summary = "Remove a tag from a secret version")
    public ResponseEntity<ApiResponse<Void>> removeTag(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @PathVariable UUID secretId,
            @PathVariable Integer versionNumber,
            @PathVariable String tagName,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        String requestId = resolveRequestId();
        String ipAddress = httpRequest.getRemoteAddr();

        versionService.removeTag(
                workspaceId, projectId, environmentId, secretId, versionNumber, tagName, principal.getId(), requestId, ipAddress
        );
        return ResponseEntity.ok(ApiResponse.success(null, "Tag removed successfully"));
    }

    private void validateWorkspaceHeader(UUID pathWorkspaceId, UUID headerWorkspaceId) {
        if (headerWorkspaceId != null && !headerWorkspaceId.equals(pathWorkspaceId)) {
            throw ApiException.badRequest("X-Workspace-ID header does not match path workspace ID");
        }
    }

    private String resolveRequestId() {
        String reqId = MDC.get("correlationId");
        return StringUtils.hasText(reqId) ? reqId : UUID.randomUUID().toString();
    }
}
