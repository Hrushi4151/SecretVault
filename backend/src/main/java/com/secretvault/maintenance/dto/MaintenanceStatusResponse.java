package com.secretvault.maintenance.dto;

import java.time.Instant;
import java.util.UUID;

public record MaintenanceStatusResponse(
        boolean enabled,
        boolean readOnlyAllowed,
        String scope,
        String reason,
        UUID activatedBy,
        Instant activatedAt,
        Instant scheduledEndAt
) {
    public static MaintenanceStatusResponse disabled() {
        return new MaintenanceStatusResponse(false, false, "NONE", null, null, null, null);
    }
}
