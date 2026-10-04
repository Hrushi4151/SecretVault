package com.secretvault.maintenance;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.maintenance.dto.EnableMaintenanceRequest;
import com.secretvault.maintenance.dto.MaintenanceStatusResponse;
import com.secretvault.maintenance.service.MaintenanceModeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MaintenanceModeServiceTest {

    private AuditService auditService;
    private MaintenanceModeService maintenanceService;
    private final UUID testAdminId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        maintenanceService = new MaintenanceModeService(auditService);
    }

    @Test
    @DisplayName("Initial state: Maintenance mode is disabled by default")
    void testInitialStateDisabled() {
        MaintenanceStatusResponse status = maintenanceService.getStatus();
        assertThat(status.enabled()).isFalse();
        assertThat(status.readOnlyAllowed()).isFalse();
        assertThat(maintenanceService.isRequestBlocked("/api/v1/secrets", "POST", false)).isFalse();
    }

    @Test
    @DisplayName("Enable maintenance: activates lock, records audit, and blocks mutations")
    void testEnableMaintenanceBlocksMutations() {
        EnableMaintenanceRequest request = new EnableMaintenanceRequest(
                "Database engine major version upgrade",
                "GLOBAL",
                true, // read-only allowed
                Duration.ofMinutes(30)
        );

        MaintenanceStatusResponse status = maintenanceService.enableMaintenance(testAdminId, request);

        assertThat(status.enabled()).isTrue();
        assertThat(status.readOnlyAllowed()).isTrue();
        assertThat(status.reason()).isEqualTo("Database engine major version upgrade");
        assertThat(status.activatedBy()).isEqualTo(testAdminId);

        verify(auditService).recordAudit(
                eq(null),
                eq(null),
                eq(testAdminId),
                eq("USER"),
                eq(AuditAction.MAINTENANCE_MODE_ENABLED),
                eq("SYSTEM_MAINTENANCE"),
                any(UUID.class),
                eq(null),
                eq(null),
                eq("SUCCESS")
        );

        // Mutating request should be blocked
        assertThat(maintenanceService.isRequestBlocked("/api/v1/secrets", "POST", false)).isTrue();
        assertThat(maintenanceService.isRequestBlocked("/api/v1/secrets/123", "DELETE", false)).isTrue();

        // Read-only request should be allowed (since allowReadOnly = true)
        assertThat(maintenanceService.isRequestBlocked("/api/v1/secrets", "GET", false)).isFalse();

        // Whitelisted endpoint should be allowed
        assertThat(maintenanceService.isRequestBlocked("/actuator/health", "GET", false)).isFalse();
        assertThat(maintenanceService.isRequestBlocked("/api/v1/auth/login", "POST", false)).isFalse();

        // Platform Admin should bypass maintenance lock
        assertThat(maintenanceService.isRequestBlocked("/api/v1/secrets", "POST", true)).isFalse();
    }

    @Test
    @DisplayName("Disable maintenance: restores normal operation and records audit")
    void testDisableMaintenanceRestoresTraffic() {
        EnableMaintenanceRequest request = new EnableMaintenanceRequest(
                "Emergency maintenance", "GLOBAL", false, Duration.ofMinutes(10)
        );
        maintenanceService.enableMaintenance(testAdminId, request);
        assertThat(maintenanceService.getStatus().enabled()).isTrue();

        MaintenanceStatusResponse postDisable = maintenanceService.disableMaintenance(testAdminId, "Upgrade complete");

        assertThat(postDisable.enabled()).isFalse();
        assertThat(maintenanceService.isRequestBlocked("/api/v1/secrets", "POST", false)).isFalse();

        verify(auditService).recordAudit(
                eq(null),
                eq(null),
                eq(testAdminId),
                eq("USER"),
                eq(AuditAction.MAINTENANCE_MODE_DISABLED),
                eq("SYSTEM_MAINTENANCE"),
                any(UUID.class),
                eq(null),
                eq(null),
                eq("SUCCESS")
        );
    }
}
