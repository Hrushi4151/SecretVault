package com.secretvault.audit.controller;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.entity.AuditLog;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.common.dto.PageResponse;
import com.secretvault.security.util.PaginationUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping
public class AuditController {

    private final AuditService auditService;
    private final EffectiveAccessService effectiveAccessService;

    private static final Set<String> ALLOWED_AUDIT_SORT_FIELDS = Set.of(
            "createdAt", "action", "outcome", "resourceType", "actorType"
    );

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
        List<AuditLog> logs = auditService.getWorkspaceAuditLogs(workspaceId, limit);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/audit/search")
    public ResponseEntity<ApiResponse<PageResponse<AuditLog>>> searchAuditLogs(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.SECURITY_VIEW, principal.getId());
        Pageable safePageable = PaginationUtils.sanitizePageable(
                pageable, ALLOWED_AUDIT_SORT_FIELDS, "createdAt", Sort.Direction.DESC
        );

        Page<AuditLog> page = auditService.searchAuditLogs(
                workspaceId, action, outcome, resourceType, actorId, search, from, to, safePageable
        );

        PageResponse<AuditLog> pageResponse = new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
        return ResponseEntity.ok(ApiResponse.success(pageResponse));
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/audit/export")
    public ResponseEntity<?> exportAuditLogs(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false, defaultValue = "csv") String format,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false, defaultValue = "1000") int limit,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        effectiveAccessService.checkPermission(workspaceId, null, null, null, AccessPermission.SECURITY_VIEW, principal.getId());
        List<AuditLog> logs = auditService.exportAuditLogs(
                workspaceId, action, outcome, resourceType, actorId, search, from, to, limit
        );

        if ("json".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit_logs_" + workspaceId + ".json\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(logs);
        }

        // CSV formatting
        StringBuilder csv = new StringBuilder();
        csv.append("id,timestamp,action,outcome,actorType,actorId,resourceType,resourceId,ipAddress,requestId\n");
        for (AuditLog log : logs) {
            csv.append(escapeCsv(log.getId())).append(",")
               .append(escapeCsv(log.getCreatedAt())).append(",")
               .append(escapeCsv(log.getAction())).append(",")
               .append(escapeCsv(log.getOutcome())).append(",")
               .append(escapeCsv(log.getActorType())).append(",")
               .append(escapeCsv(log.getActorId())).append(",")
               .append(escapeCsv(log.getResourceType())).append(",")
               .append(escapeCsv(log.getResourceId())).append(",")
               .append(escapeCsv(log.getIpAddress())).append(",")
               .append(escapeCsv(log.getRequestId())).append("\n");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit_logs_" + workspaceId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toString());
    }

    private String escapeCsv(Object val) {
        if (val == null) return "";
        String str = val.toString();
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
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
