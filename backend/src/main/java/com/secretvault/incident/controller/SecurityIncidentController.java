package com.secretvault.incident.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.incident.entity.IncidentSeverity;
import com.secretvault.incident.entity.IncidentStatus;
import com.secretvault.incident.entity.SecurityIncident;
import com.secretvault.incident.entity.SecurityIncidentEvent;
import com.secretvault.incident.service.SecurityIncidentService;
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
@RequestMapping("/api/v1/workspaces/{workspaceId}/incidents")
@Tag(name = "Security Incidents", description = "Security incident management, triage, and automated compromise response operations")
@SecurityRequirement(name = "BearerAuth")
public class SecurityIncidentController {

    private final SecurityIncidentService incidentService;

    public SecurityIncidentController(SecurityIncidentService incidentService) {
        this.incidentService = incidentService;
    }

    public record CreateIncidentRequest(
            @NotBlank String title,
            String description,
            IncidentSeverity severity,
            String category,
            UUID sourceEventId
    ) {}

    public record UpdateIncidentStatusRequest(
            @NotBlank IncidentStatus status,
            String resolutionSummary
    ) {}

    @PostMapping
    @Operation(summary = "Create security incident")
    public ResponseEntity<ApiResponse<SecurityIncident>> createIncident(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateIncidentRequest req,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        SecurityIncident incident = incidentService.createIncident(
                workspaceId,
                req.title(),
                req.description(),
                req.severity(),
                req.category(),
                req.sourceEventId(),
                actorId
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(incident, "Security incident created"));
    }

    @GetMapping
    @Operation(summary = "List security incidents in workspace")
    public ResponseEntity<ApiResponse<Page<SecurityIncident>>> listIncidents(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentSeverity severity,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        Page<SecurityIncident> incidents = incidentService.listIncidents(workspaceId, status, severity, pageable, actorId);
        return ResponseEntity.ok(ApiResponse.success(incidents));
    }

    @GetMapping("/{incidentId}")
    @Operation(summary = "Get security incident details")
    public ResponseEntity<ApiResponse<SecurityIncident>> getIncident(
            @PathVariable UUID workspaceId,
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        SecurityIncident incident = incidentService.getIncident(workspaceId, incidentId, actorId);
        return ResponseEntity.ok(ApiResponse.success(incident));
    }

    @PutMapping("/{incidentId}/status")
    @Operation(summary = "Update incident triage or resolution status")
    public ResponseEntity<ApiResponse<SecurityIncident>> updateStatus(
            @PathVariable UUID workspaceId,
            @PathVariable UUID incidentId,
            @Valid @RequestBody UpdateIncidentStatusRequest req,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        SecurityIncident updated = incidentService.updateIncidentStatus(
                workspaceId,
                incidentId,
                req.status(),
                req.resolutionSummary(),
                actorId
        );
        return ResponseEntity.ok(ApiResponse.success(updated, "Incident status updated"));
    }

    @GetMapping("/{incidentId}/events")
    @Operation(summary = "Get correlated domain events for an incident")
    public ResponseEntity<ApiResponse<List<SecurityIncidentEvent>>> getIncidentEvents(
            @PathVariable UUID workspaceId,
            @PathVariable UUID incidentId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        List<SecurityIncidentEvent> events = incidentService.getIncidentEvents(workspaceId, incidentId, actorId);
        return ResponseEntity.ok(ApiResponse.success(events));
    }
}
