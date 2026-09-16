package com.secretvault.health.controller;

import com.secretvault.health.dto.SystemHealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public system health controller providing high-level operational status.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health & Diagnostics", description = "Public system health and liveness checks")
public class SystemHealthController {

    private final String applicationVersion;

    public SystemHealthController(@Value("${info.app.version:0.1.0-SNAPSHOT}") String applicationVersion) {
        this.applicationVersion = applicationVersion;
    }

    @GetMapping
    @Operation(summary = "Get System Health", description = "Returns the operational status and version of the SecretVault backend service")
    public ResponseEntity<SystemHealthResponse> getHealth() {
        return ResponseEntity.ok(SystemHealthResponse.up(applicationVersion));
    }
}
