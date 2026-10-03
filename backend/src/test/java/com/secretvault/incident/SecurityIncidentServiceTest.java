package com.secretvault.incident;

import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.events.publisher.EventPublisher;
import com.secretvault.incident.entity.IncidentSeverity;
import com.secretvault.incident.entity.IncidentStatus;
import com.secretvault.incident.entity.SecurityIncident;
import com.secretvault.incident.repository.SecurityIncidentEventRepository;
import com.secretvault.incident.repository.SecurityIncidentRepository;
import com.secretvault.incident.service.SecurityIncidentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 13: Security Incident Service Tests")
class SecurityIncidentServiceTest {

    @Mock
    private SecurityIncidentRepository incidentRepository;
    @Mock
    private SecurityIncidentEventRepository incidentEventRepository;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private EffectiveAccessService effectiveAccessService;
    @Mock
    private AuditService auditService;

    private SecurityIncidentService incidentService;
    private UUID workspaceId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        incidentService = new SecurityIncidentService(
                incidentRepository,
                incidentEventRepository,
                eventPublisher,
                effectiveAccessService,
                auditService
        );

        workspaceId = UUID.randomUUID();
        actorId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Create incident persists incident with generated incident number and publishes domain event")
    void testCreateIncident() {
        when(incidentRepository.countByWorkspaceId(workspaceId)).thenReturn(0L);
        when(incidentRepository.save(any(SecurityIncident.class))).thenAnswer(inv -> {
            SecurityIncident incident = inv.getArgument(0);
            incident.setId(UUID.randomUUID());
            return incident;
        });

        SecurityIncident incident = incidentService.createIncident(
                workspaceId,
                "Potential Credential Leak",
                "Leaked API key detected in build logs",
                IncidentSeverity.HIGH,
                "CREDENTIAL_LEAK",
                UUID.randomUUID(),
                actorId
        );

        assertThat(incident).isNotNull();
        assertThat(incident.getTitle()).isEqualTo("Potential Credential Leak");
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getIncidentNumber()).startsWith("INC-");
        verify(eventPublisher).publish(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Update incident status from OPEN to INVESTIGATING and RESOLVED")
    void testUpdateIncidentStatus() {
        UUID incidentId = UUID.randomUUID();
        SecurityIncident incident = new SecurityIncident();
        incident.setId(incidentId);
        incident.setWorkspaceId(workspaceId);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setIncidentNumber("INC-0001");
        incident.setSeverity(IncidentSeverity.HIGH);

        when(incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId))
                .thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(SecurityIncident.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SecurityIncident updated = incidentService.updateIncidentStatus(
                workspaceId,
                incidentId,
                IncidentStatus.RESOLVED,
                "Rotated credentials and verified consumers",
                actorId
        );

        assertThat(updated.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(updated.getResolvedAt()).isNotNull();
        assertThat(updated.getResolvedBy()).isEqualTo(actorId);
    }
}
