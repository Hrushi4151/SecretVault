package com.secretvault.rotation.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.rotation.dto.RotationDtos.*;
import com.secretvault.rotation.service.SecretConsumerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Tag(name = "Secret Consumers", description = "Workload and application consumer registry and heartbeat tracking APIs")
@SecurityRequirement(name = "BearerAuth")
public class SecretConsumerController {

    private final SecretConsumerService consumerService;

    public SecretConsumerController(SecretConsumerService consumerService) {
        this.consumerService = consumerService;
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/projects/{projectId}/environments/{environmentId}/consumers/register")
    @Operation(summary = "Register runtime workload / SDK consumer")
    public ResponseEntity<ApiResponse<SecretConsumerResponse>> registerConsumer(
            @PathVariable UUID workspaceId,
            @PathVariable UUID projectId,
            @PathVariable UUID environmentId,
            @Valid @RequestBody RegisterSecretConsumerRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        SecretConsumerResponse response = consumerService.registerConsumer(workspaceId, projectId, environmentId, request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Consumer registered successfully"));
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/consumers/{consumerId}/heartbeat")
    @Operation(summary = "Report SDK / workload heartbeat and acknowledge active secret version")
    public ResponseEntity<ApiResponse<SecretConsumerResponse>> heartbeat(
            @PathVariable UUID workspaceId,
            @PathVariable UUID consumerId,
            @RequestBody(required = false) ConsumerHeartbeatRequest request
    ) {
        SecretConsumerResponse response = consumerService.heartbeat(workspaceId, consumerId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/consumers")
    @Operation(summary = "List registered secret consumers in workspace")
    public ResponseEntity<ApiResponse<Page<SecretConsumerResponse>>> listConsumers(
            @PathVariable UUID workspaceId,
            @PageableDefault(size = 20, sort = "registeredAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        Page<SecretConsumerResponse> response = consumerService.listConsumers(workspaceId, pageable, actorId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/consumers/{consumerId}")
    @Operation(summary = "Get secret consumer details by ID")
    public ResponseEntity<ApiResponse<SecretConsumerResponse>> getConsumer(
            @PathVariable UUID workspaceId,
            @PathVariable UUID consumerId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        SecretConsumerResponse response = consumerService.getConsumer(workspaceId, consumerId, actorId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/api/v1/workspaces/{workspaceId}/consumers/{consumerId}")
    @Operation(summary = "Disable consumer instance")
    public ResponseEntity<ApiResponse<Void>> disableConsumer(
            @PathVariable UUID workspaceId,
            @PathVariable UUID consumerId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        consumerService.disableConsumer(workspaceId, consumerId, actorId);
        return ResponseEntity.ok(ApiResponse.success(null, "Consumer disabled"));
    }
}
