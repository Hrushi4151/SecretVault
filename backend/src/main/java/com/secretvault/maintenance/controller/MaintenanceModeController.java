package com.secretvault.maintenance.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.maintenance.dto.EnableMaintenanceRequest;
import com.secretvault.maintenance.dto.MaintenanceStatusResponse;
import com.secretvault.maintenance.service.MaintenanceModeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/maintenance")
@Tag(name = "Platform Maintenance Operations", description = "Enterprise controls for system-wide maintenance mode and safe operational locking")
public class MaintenanceModeController {

    private final MaintenanceModeService maintenanceModeService;

    public MaintenanceModeController(MaintenanceModeService maintenanceModeService) {
        this.maintenanceModeService = maintenanceModeService;
    }

    @GetMapping("/status")
    @Operation(summary = "Get current maintenance mode status", description = "Returns active maintenance mode parameters, scope, and read-only allowance")
    public ResponseEntity<ApiResponse<MaintenanceStatusResponse>> getStatus() {
        MaintenanceStatusResponse status = maintenanceModeService.getStatus();
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @PostMapping("/enable")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCOPE_PLATFORM_ADMIN')")
    @Operation(summary = "Enable operational maintenance mode", description = "Activates maintenance mode to block mutating traffic during deployments or disaster recovery")
    public ResponseEntity<ApiResponse<MaintenanceStatusResponse>> enableMaintenance(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody EnableMaintenanceRequest request
    ) {
        UUID actorId = principal != null ? principal.getId() : UUID.nameUUIDFromBytes("SYSTEM_ADMIN".getBytes());
        MaintenanceStatusResponse status = maintenanceModeService.enableMaintenance(actorId, request);
        return ResponseEntity.ok(ApiResponse.success(status, "Maintenance mode enabled successfully"));
    }

    @PostMapping("/disable")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCOPE_PLATFORM_ADMIN')")
    @Operation(summary = "Disable operational maintenance mode", description = "Deactivates maintenance mode and restores normal traffic processing")
    public ResponseEntity<ApiResponse<MaintenanceStatusResponse>> disableMaintenance(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) Map<String, String> body
    ) {
        UUID actorId = principal != null ? principal.getId() : UUID.nameUUIDFromBytes("SYSTEM_ADMIN".getBytes());
        String reason = body != null ? body.get("reason") : "Administrative resumption";
        MaintenanceStatusResponse status = maintenanceModeService.disableMaintenance(actorId, reason);
        return ResponseEntity.ok(ApiResponse.success(status, "Maintenance mode disabled successfully"));
    }
}
