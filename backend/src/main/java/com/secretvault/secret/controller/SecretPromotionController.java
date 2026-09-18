package com.secretvault.secret.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.common.exception.ApiException;
import com.secretvault.secret.dto.ExecutePromotionRequest;
import com.secretvault.secret.dto.PromotionPreviewRequest;
import com.secretvault.secret.dto.PromotionPreviewResponse;
import com.secretvault.secret.dto.PromotionResultResponse;
import com.secretvault.secret.service.SecretPromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{sourceEnvironmentId}/promote")
@Tag(name = "Secret Promotion", description = "Cross-environment secret promotion dry-run preview and atomic execution APIs")
@SecurityRequirement(name = "BearerAuth")
public class SecretPromotionController {

    private final SecretPromotionService promotionService;

    public SecretPromotionController(SecretPromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @PostMapping("/preview")
    @Operation(summary = "Dry-run preview of cross-environment secret promotion without mutating database")
    public ResponseEntity<ApiResponse<PromotionPreviewResponse>> previewPromotion(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID sourceEnvironmentId,
            @Valid @RequestBody PromotionPreviewRequest request,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        PromotionPreviewResponse preview = promotionService.previewPromotion(
                workspaceId, projectId, sourceEnvironmentId, request, principal.getId()
        );
        return ResponseEntity.ok(ApiResponse.success(preview));
    }

    @PostMapping
    @Operation(summary = "Execute atomic cross-environment secret promotion with fresh destination encryption")
    public ResponseEntity<ApiResponse<PromotionResultResponse>> executePromotion(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID sourceEnvironmentId,
            @Valid @RequestBody ExecutePromotionRequest request,
            @RequestHeader(value = "X-Workspace-ID", required = false) UUID headerWorkspaceId,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest
    ) {
        validateWorkspaceHeader(workspaceId, headerWorkspaceId);
        String requestId = resolveRequestId();
        String ipAddress = httpRequest.getRemoteAddr();

        PromotionResultResponse result = promotionService.executePromotion(
                workspaceId, projectId, sourceEnvironmentId, request, principal.getId(), requestId, ipAddress
        );
        return ResponseEntity.ok(ApiResponse.success(result, "Promotion executed successfully"));
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
