package com.secretvault.maintenance.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.maintenance.dto.EnableMaintenanceRequest;
import com.secretvault.maintenance.dto.MaintenanceStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Enterprise Operational Maintenance Mode Service.
 * Allows platform administrators to place the system or specific scopes into maintenance mode
 * with optional read-only access, ensuring clean deployments, migrations, and disaster recovery.
 */
@Service
public class MaintenanceModeService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceModeService.class);

    private static final Set<String> WHITELISTED_PATHS = Set.of(
            "/actuator/health",
            "/actuator/prometheus",
            "/actuator/info",
            "/api/v1/auth/login",
            "/api/v1/auth/mfa/challenge",
            "/api/v1/auth/mfa/verify",
            "/api/v1/maintenance/status",
            "/api/v1/maintenance/disable"
    );

    private final AuditService auditService;
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final AtomicBoolean readOnlyAllowed = new AtomicBoolean(false);
    private final AtomicReference<String> scopeRef = new AtomicReference<>("GLOBAL");
    private final AtomicReference<String> reasonRef = new AtomicReference<>("");
    private final AtomicReference<UUID> activatedByRef = new AtomicReference<>(null);
    private final AtomicReference<Instant> activatedAtRef = new AtomicReference<>(null);
    private final AtomicReference<Instant> scheduledEndAtRef = new AtomicReference<>(null);

    public MaintenanceModeService(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Activates maintenance mode with reason, optional scope, and read-only allowance.
     */
    public synchronized MaintenanceStatusResponse enableMaintenance(UUID actorId, EnableMaintenanceRequest request) {
        if (!StringUtils.hasText(request.reason())) {
            throw ApiException.badRequest("INVALID_MAINTENANCE_REQUEST", "Maintenance reason is required");
        }

        Instant now = Instant.now();
        Instant endAt = request.estimatedDuration() != null ? now.plus(request.estimatedDuration()) : null;
        String scope = StringUtils.hasText(request.scope()) ? request.scope().trim().toUpperCase() : "GLOBAL";

        this.enabled.set(true);
        this.readOnlyAllowed.set(request.allowReadOnly());
        this.scopeRef.set(scope);
        this.reasonRef.set(request.reason().trim());
        this.activatedByRef.set(actorId);
        this.activatedAtRef.set(now);
        this.scheduledEndAtRef.set(endAt);

        log.warn("MAINTENANCE MODE ACTIVATED by actor [{}] for scope [{}] (readOnlyAllowed={}): {}",
                actorId, scope, request.allowReadOnly(), request.reason());

        try {
            auditService.recordAudit(
                    null,
                    null,
                    actorId,
                    "USER",
                    AuditAction.MAINTENANCE_MODE_ENABLED,
                    "SYSTEM_MAINTENANCE",
                    UUID.nameUUIDFromBytes(scope.getBytes()),
                    null,
                    null,
                    "SUCCESS"
            );
        } catch (Exception e) {
            log.warn("Failed to record audit log for maintenance mode enable: {}", e.getMessage());
        }

        return getStatus();
    }

    /**
     * Deactivates maintenance mode and restores standard operations.
     */
    public synchronized MaintenanceStatusResponse disableMaintenance(UUID actorId, String reason) {
        if (!this.enabled.get()) {
            return getStatus();
        }

        String prevScope = this.scopeRef.get();
        this.enabled.set(false);
        this.readOnlyAllowed.set(false);
        this.scopeRef.set("NONE");
        this.reasonRef.set("");
        this.activatedByRef.set(null);
        this.activatedAtRef.set(null);
        this.scheduledEndAtRef.set(null);

        log.info("MAINTENANCE MODE DEACTIVATED by actor [{}]: {}", actorId, reason != null ? reason : "Standard resume");

        try {
            auditService.recordAudit(
                    null,
                    null,
                    actorId,
                    "USER",
                    AuditAction.MAINTENANCE_MODE_DISABLED,
                    "SYSTEM_MAINTENANCE",
                    UUID.nameUUIDFromBytes(prevScope.getBytes()),
                    null,
                    null,
                    "SUCCESS"
            );
        } catch (Exception e) {
            log.warn("Failed to record audit log for maintenance mode disable: {}", e.getMessage());
        }

        return getStatus();
    }

    /**
     * Returns current maintenance mode status.
     */
    public MaintenanceStatusResponse getStatus() {
        if (!enabled.get()) {
            return MaintenanceStatusResponse.disabled();
        }
        return new MaintenanceStatusResponse(
                enabled.get(),
                readOnlyAllowed.get(),
                scopeRef.get(),
                reasonRef.get(),
                activatedByRef.get(),
                activatedAtRef.get(),
                scheduledEndAtRef.get()
        );
    }

    /**
     * Evaluates whether an incoming HTTP request should be blocked by maintenance mode.
     */
    public boolean isRequestBlocked(String requestUri, String httpMethod, boolean isPlatformAdmin) {
        if (!enabled.get()) {
            return false;
        }

        // Whitelisted diagnostic and authentication paths always pass
        for (String whitelisted : WHITELISTED_PATHS) {
            if (requestUri.startsWith(whitelisted)) {
                return false;
            }
        }

        // Platform administrators carrying break-glass tokens bypass maintenance mode
        if (isPlatformAdmin) {
            return false;
        }

        // If read-only is allowed, safe HTTP methods (GET, HEAD, OPTIONS) pass
        boolean isSafeMethod = "GET".equalsIgnoreCase(httpMethod)
                || "HEAD".equalsIgnoreCase(httpMethod)
                || "OPTIONS".equalsIgnoreCase(httpMethod);

        if (readOnlyAllowed.get() && isSafeMethod) {
            return false;
        }

        // All other requests are blocked
        return true;
    }
}
