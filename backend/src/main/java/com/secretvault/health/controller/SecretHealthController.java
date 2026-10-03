package com.secretvault.health.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.health.model.SecretHealthEvaluation;
import com.secretvault.health.service.SecretHealthEvaluator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/secrets/{secretId}")
@Tag(name = "Secret Health & Intelligence", description = "Explainable secret health, posture scoring, and risk analytics")
@SecurityRequirement(name = "BearerAuth")
public class SecretHealthController {

    private final SecretHealthEvaluator healthEvaluator;

    public SecretHealthController(SecretHealthEvaluator healthEvaluator) {
        this.healthEvaluator = healthEvaluator;
    }

    @GetMapping("/health")
    @Operation(summary = "Get explainable health evaluation and risk factors for a secret")
    public ResponseEntity<ApiResponse<SecretHealthEvaluation>> getSecretHealth(
            @PathVariable UUID workspaceId,
            @PathVariable UUID secretId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        SecretHealthEvaluation health = healthEvaluator.evaluateSecret(workspaceId, secretId, actorId);
        return ResponseEntity.ok(ApiResponse.success(health));
    }
}
