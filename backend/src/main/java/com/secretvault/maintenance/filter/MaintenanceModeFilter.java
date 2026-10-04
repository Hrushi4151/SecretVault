package com.secretvault.maintenance.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.maintenance.dto.MaintenanceStatusResponse;
import com.secretvault.maintenance.service.MaintenanceModeService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * HTTP filter enforcing maintenance mode gates.
 * Blocks non-safe mutations with HTTP 503 during platform maintenance windows.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class MaintenanceModeFilter extends OncePerRequestFilter {

    private final MaintenanceModeService maintenanceModeService;
    private final ObjectMapper objectMapper;

    public MaintenanceModeFilter(MaintenanceModeService maintenanceModeService, ObjectMapper objectMapper) {
        this.maintenanceModeService = maintenanceModeService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String uri = request.getRequestURI();
        String method = request.getMethod();

        boolean isPlatformAdmin = false;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            for (GrantedAuthority authority : auth.getAuthorities()) {
                if ("ROLE_ADMIN".equalsIgnoreCase(authority.getAuthority())
                        || "SCOPE_PLATFORM_ADMIN".equalsIgnoreCase(authority.getAuthority())) {
                    isPlatformAdmin = true;
                    break;
                }
            }
        }

        if (maintenanceModeService.isRequestBlocked(uri, method, isPlatformAdmin)) {
            MaintenanceStatusResponse status = maintenanceModeService.getStatus();
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            Map<String, Object> errorPayload = Map.of(
                    "success", false,
                    "error", Map.of(
                            "code", "MAINTENANCE_MODE_ACTIVE",
                            "message", "SecretVault is currently operating in maintenance mode. Non-safe mutations are temporarily suspended.",
                            "details", Map.of(
                                    "scope", status.scope(),
                                    "reason", status.reason() != null ? status.reason() : "Scheduled system maintenance",
                                    "readOnlyAllowed", status.readOnlyAllowed(),
                                    "scheduledEndAt", status.scheduledEndAt() != null ? status.scheduledEndAt().toString() : "UNSPECIFIED"
                            )
                    )
            );

            response.getWriter().write(objectMapper.writeValueAsString(errorPayload));
            return;
        }

        filterChain.doFilter(request, response);
    }
}
