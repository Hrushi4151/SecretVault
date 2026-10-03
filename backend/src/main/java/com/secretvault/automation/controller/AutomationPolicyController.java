package com.secretvault.automation.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.automation.entity.AutomationPolicy;
import com.secretvault.automation.entity.AutomationPolicyScope;
import com.secretvault.automation.model.SimulationResult;
import com.secretvault.automation.service.AutomationPolicyService;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.events.model.BaseDomainEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/automation-policies")
@Tag(name = "Automation Policies", description = "Security automation and policy operations APIs")
@SecurityRequirement(name = "BearerAuth")
public class AutomationPolicyController {

    private final AutomationPolicyService policyService;

    public AutomationPolicyController(AutomationPolicyService policyService) {
        this.policyService = policyService;
    }

    public record CreateAutomationPolicyRequest(
            @NotBlank String name,
            String description,
            boolean enabled,
            int priority,
            AutomationPolicyScope scopeType,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            String triggerEventTypes,
            String conditionsJson,
            String actionsJson,
            boolean dryRun,
            boolean approvalRequired
    ) {}

    public record UpdateAutomationPolicyRequest(
            String name,
            String description,
            Boolean enabled,
            Integer priority,
            AutomationPolicyScope scopeType,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            String triggerEventTypes,
            String conditionsJson,
            String actionsJson,
            Boolean dryRun,
            Boolean approvalRequired
    ) {}

    @PostMapping
    @Operation(summary = "Create automation policy")
    public ResponseEntity<ApiResponse<AutomationPolicy>> createPolicy(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateAutomationPolicyRequest req,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        AutomationPolicy policy = policyService.createPolicy(
                workspaceId,
                req.name(),
                req.description(),
                req.enabled(),
                req.priority(),
                req.scopeType(),
                req.projectId(),
                req.environmentId(),
                req.secretId(),
                req.triggerEventTypes(),
                req.conditionsJson(),
                req.actionsJson(),
                req.dryRun(),
                req.approvalRequired(),
                actorId
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(policy, "Automation policy created successfully"));
    }

    @GetMapping
    @Operation(summary = "List automation policies in workspace")
    public ResponseEntity<ApiResponse<Page<AutomationPolicy>>> listPolicies(
            @PathVariable UUID workspaceId,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        Page<AutomationPolicy> policies = policyService.listPolicies(workspaceId, pageable, actorId);
        return ResponseEntity.ok(ApiResponse.success(policies));
    }

    @GetMapping("/{policyId}")
    @Operation(summary = "Get automation policy details")
    public ResponseEntity<ApiResponse<AutomationPolicy>> getPolicy(
            @PathVariable UUID workspaceId,
            @PathVariable UUID policyId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        AutomationPolicy policy = policyService.getPolicy(workspaceId, policyId, actorId);
        return ResponseEntity.ok(ApiResponse.success(policy));
    }

    @PutMapping("/{policyId}")
    @Operation(summary = "Update automation policy")
    public ResponseEntity<ApiResponse<AutomationPolicy>> updatePolicy(
            @PathVariable UUID workspaceId,
            @PathVariable UUID policyId,
            @RequestBody UpdateAutomationPolicyRequest req,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        AutomationPolicy policy = policyService.updatePolicy(
                workspaceId,
                policyId,
                req.name(),
                req.description(),
                req.enabled(),
                req.priority(),
                req.scopeType(),
                req.projectId(),
                req.environmentId(),
                req.secretId(),
                req.triggerEventTypes(),
                req.conditionsJson(),
                req.actionsJson(),
                req.dryRun(),
                req.approvalRequired(),
                actorId
        );
        return ResponseEntity.ok(ApiResponse.success(policy, "Automation policy updated successfully"));
    }

    @PostMapping("/{policyId}/enable")
    @Operation(summary = "Enable automation policy")
    public ResponseEntity<ApiResponse<AutomationPolicy>> enablePolicy(
            @PathVariable UUID workspaceId,
            @PathVariable UUID policyId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        AutomationPolicy policy = policyService.togglePolicy(workspaceId, policyId, true, actorId);
        return ResponseEntity.ok(ApiResponse.success(policy, "Automation policy enabled"));
    }

    @PostMapping("/{policyId}/disable")
    @Operation(summary = "Disable automation policy")
    public ResponseEntity<ApiResponse<AutomationPolicy>> disablePolicy(
            @PathVariable UUID workspaceId,
            @PathVariable UUID policyId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        AutomationPolicy policy = policyService.togglePolicy(workspaceId, policyId, false, actorId);
        return ResponseEntity.ok(ApiResponse.success(policy, "Automation policy disabled"));
    }

    @DeleteMapping("/{policyId}")
    @Operation(summary = "Delete automation policy")
    public ResponseEntity<ApiResponse<Void>> deletePolicy(
            @PathVariable UUID workspaceId,
            @PathVariable UUID policyId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        policyService.deletePolicy(workspaceId, policyId, actorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Automation policy deleted successfully"));
    }

    @PostMapping("/simulate")
    @Operation(summary = "Simulate automation policies against sample event without side effects")
    public ResponseEntity<ApiResponse<List<SimulationResult>>> simulate(
            @PathVariable UUID workspaceId,
            @RequestBody BaseDomainEvent sampleEvent,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        List<SimulationResult> results = policyService.simulate(workspaceId, sampleEvent, actorId);
        return ResponseEntity.ok(ApiResponse.success(results));
    }
}
