package com.secretvault.audit.controller;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditLog;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
public class AuditController {

    private final AuditService auditService;
    private final EffectiveAccessService effectiveAccessService;

    public AuditController(AuditService auditService, EffectiveAccessService effectiveAccessService) {
        this.auditService = auditService;
        this.effectiveAccessService = effectiveAccessService;
    }

    @GetMapping({"/api/v1/workspaces/{workspaceId}/audit", "/api/v1/audit/workspaces/{workspaceId}"})
    public ResponseEntity<ApiResponse<List<AuditLog>>> getWorkspaceAuditLogs(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false, defaultValue = "100") int limit,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.SECURITY_VIEW, principal.getId());
        List<AuditLog> logs = auditService.getWorkspaceAuditLogs(workspaceId);
        if (logs.size() > limit) {
            logs = logs.subList(0, limit);
        }
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    @GetMapping("/api/v1/secrets/{secretId}/audit")
    public ResponseEntity<ApiResponse<List<AuditLog>>> getSecretAuditLogs(
            @PathVariable UUID secretId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<AuditLog> logs = auditService.getSecretAuditLogs(secretId);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}
