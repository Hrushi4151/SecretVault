package com.secretvault.system.controller;

import com.secretvault.common.dto.ApiResponse;
import com.secretvault.system.dto.SloStatusResponse;
import com.secretvault.system.dto.SystemHealthResponse;
import com.secretvault.system.service.SystemHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("platformOperationsController")
@RequestMapping("/api/v1/system")
@Tag(name = "System Operations & SLOs", description = "Endpoints for platform health score, infrastructure tier diagnostics, and enterprise SLO monitoring")
public class PlatformOperationsController {

    private final SystemHealthService systemHealthService;

    public PlatformOperationsController(SystemHealthService systemHealthService) {
        this.systemHealthService = systemHealthService;
    }

    @GetMapping("/health-score")
    @Operation(summary = "Get overall system health score", description = "Calculates unified infrastructure health score across PostgreSQL, Redis, KMS, and workers")
    public ResponseEntity<ApiResponse<SystemHealthResponse>> getHealthScore() {
        SystemHealthResponse health = systemHealthService.evaluateSystemHealth();
        return ResponseEntity.ok(ApiResponse.success(health));
    }

    @GetMapping("/slos")
    @Operation(summary = "Get enterprise SLO compliance status", description = "Returns status of availability, latency, rotation success, and provider synchronization SLOs")
    public ResponseEntity<ApiResponse<SloStatusResponse>> getSlos() {
        SloStatusResponse slos = systemHealthService.evaluateSlos();
        return ResponseEntity.ok(ApiResponse.success(slos));
    }
}
