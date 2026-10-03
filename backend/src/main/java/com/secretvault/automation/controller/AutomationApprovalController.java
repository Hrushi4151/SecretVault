package com.secretvault.automation.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.automation.entity.AutomationApproval;
import com.secretvault.automation.entity.AutomationApprovalStatus;
import com.secretvault.automation.service.AutomationApprovalService;
import com.secretvault.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/automation-approvals")
@Tag(name = "Automation Approvals", description = "Four-eyes approval governance for sensitive automation actions")
@SecurityRequirement(name = "BearerAuth")
public class AutomationApprovalController {

    private final AutomationApprovalService approvalService;

    public AutomationApprovalController(AutomationApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    public record DecideApprovalRequest(
            boolean approve,
            String rejectionReason
    ) {}

    @GetMapping
    @Operation(summary = "List pending or decided automation approvals")
    public ResponseEntity<ApiResponse<Page<AutomationApproval>>> listApprovals(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) AutomationApprovalStatus status,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        Page<AutomationApproval> approvals = approvalService.listApprovals(workspaceId, status, pageable, actorId);
        return ResponseEntity.ok(ApiResponse.success(approvals));
    }

    @PostMapping("/{approvalId}/decide")
    @Operation(summary = "Approve or reject a sensitive automation action")
    public ResponseEntity<ApiResponse<AutomationApproval>> decide(
            @PathVariable UUID workspaceId,
            @PathVariable UUID approvalId,
            @RequestBody DecideApprovalRequest req,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        AutomationApproval decided = approvalService.decideApproval(
                workspaceId,
                approvalId,
                req.approve(),
                req.rejectionReason(),
                actorId
        );
        String msg = req.approve() ? "Automation action approved and executed" : "Automation action rejected";
        return ResponseEntity.ok(ApiResponse.success(decided, msg));
    }
}
