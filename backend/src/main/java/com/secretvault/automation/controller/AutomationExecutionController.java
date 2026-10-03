package com.secretvault.automation.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.automation.entity.AutomationExecution;
import com.secretvault.automation.entity.AutomationExecutionStatus;
import com.secretvault.automation.service.AutomationExecutionService;
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
@RequestMapping("/api/v1/workspaces/{workspaceId}/automation-executions")
@Tag(name = "Automation Executions", description = "Audit trail and execution logs for automation policies")
@SecurityRequirement(name = "BearerAuth")
public class AutomationExecutionController {

    private final AutomationExecutionService executionService;

    public AutomationExecutionController(AutomationExecutionService executionService) {
        this.executionService = executionService;
    }

    @GetMapping
    @Operation(summary = "List automation policy executions")
    public ResponseEntity<ApiResponse<Page<AutomationExecution>>> listExecutions(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) UUID policyId,
            @RequestParam(required = false) AutomationExecutionStatus status,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        Page<AutomationExecution> executions = executionService.listExecutions(workspaceId, policyId, status, pageable, actorId);
        return ResponseEntity.ok(ApiResponse.success(executions));
    }

    @GetMapping("/{executionId}")
    @Operation(summary = "Get execution details")
    public ResponseEntity<ApiResponse<AutomationExecution>> getExecution(
            @PathVariable UUID workspaceId,
            @PathVariable UUID executionId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        AutomationExecution execution = executionService.getExecution(workspaceId, executionId, actorId);
        return ResponseEntity.ok(ApiResponse.success(execution));
    }
}
