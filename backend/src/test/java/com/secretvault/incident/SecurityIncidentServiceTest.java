package com.secretvault.incident;

import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.events.publisher.EventPublisher;
import com.secretvault.incident.dto.IncidentDtos.IncidentRemediationRequest;
import com.secretvault.incident.dto.IncidentDtos.IncidentRemediationResult;
import com.secretvault.incident.dto.IncidentDtos.RemediationPlaybookAction;
import com.secretvault.incident.entity.IncidentSeverity;
import com.secretvault.incident.entity.IncidentStatus;
import com.secretvault.incident.entity.SecurityIncident;
import com.secretvault.incident.repository.SecurityIncidentEventRepository;
import com.secretvault.incident.repository.SecurityIncidentRepository;
import com.secretvault.incident.service.SecurityIncidentService;
import com.secretvault.machine.service.MachineIdentityService;
import com.secretvault.notification.service.NotificationService;
import com.secretvault.rotation.service.RotationService;
import com.secretvault.machine.dto.MachineDtos.MachineIdentityResponse;
import com.secretvault.machine.model.MachineStatus;
import com.secretvault.rotation.dto.RotationDtos.RotationJobResponse;
import com.secretvault.rotation.dto.RotationDtos.SecretLeaseResponse;
import com.secretvault.rotation.model.RotationStatus;
import com.secretvault.rotation.service.SecretLeaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.incident.entity.IncidentApprovalStatus;
import com.secretvault.incident.entity.IncidentRemediationStatus;
import com.secretvault.incident.entity.SecurityIncidentApproval;
import com.secretvault.incident.entity.SecurityIncidentRemediation;
import com.secretvault.incident.repository.SecurityIncidentApprovalRepository;
import com.secretvault.incident.repository.SecurityIncidentRemediationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 13: Security Incident Service Unit Tests")
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
    @Mock
    private RotationService rotationService;
    @Mock
    private SecretLeaseService leaseService;
    @Mock
    private MachineIdentityService machineService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private SecurityIncidentApprovalRepository approvalRepository;
    @Mock
    private SecurityIncidentRemediationRepository remediationRepository;

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
                auditService,
                rotationService,
                leaseService,
                machineService,
                notificationService,
                approvalRepository,
                remediationRepository,
                new ObjectMapper()
        );

        workspaceId = UUID.randomUUID();
        actorId = UUID.randomUUID();

        lenient().when(effectiveAccessService.evaluateAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(AccessDecision.allow("Allowed"));
    }

    @Test
    @DisplayName("Create incident persists incident with generated incident number and publishes domain event")
    void testCreateIncident() {
        when(incidentRepository.countByWorkspaceId(workspaceId)).thenReturn(0L);
        when(incidentRepository.findByWorkspaceIdAndIncidentNumber(eq(workspaceId), anyString()))
                .thenReturn(Optional.empty());
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
    @DisplayName("Create incident rejects unauthorized user when SECURITY_MANAGE is missing")
    void testCreateIncident_Unauthorized() {
        when(effectiveAccessService.evaluateAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(AccessDecision.deny(com.secretvault.access.model.AccessPermission.SECURITY_MANAGE, "Denied"));

        assertThatThrownBy(() -> incidentService.createIncident(
                workspaceId,
                "Title",
                "Desc",
                IncidentSeverity.HIGH,
                "ANOMALY",
                null,
                actorId
        )).isInstanceOf(ApiException.class)
                .hasMessageContaining("missing SECURITY_MANAGE permission");
    }

    @Test
    @DisplayName("Update incident status from OPEN to RESOLVED with valid summary")
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
        assertThat(updated.getResolutionSummary()).isEqualTo("Rotated credentials and verified consumers");
    }

    @Test
    @DisplayName("Update incident status rejects empty resolution summary on RESOLVED")
    void testUpdateIncidentStatus_RequiresResolutionSummary() {
        UUID incidentId = UUID.randomUUID();
        SecurityIncident incident = new SecurityIncident();
        incident.setId(incidentId);
        incident.setWorkspaceId(workspaceId);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setIncidentNumber("INC-0001");

        when(incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId))
                .thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> incidentService.updateIncidentStatus(
                workspaceId,
                incidentId,
                IncidentStatus.RESOLVED,
                "   ",
                actorId
        )).isInstanceOf(ApiException.class)
                .hasMessageContaining("Resolution summary is required");
    }

    @Test
    @DisplayName("Update incident status rejects invalid transition from CLOSED to RESOLVED")
    void testUpdateIncidentStatus_InvalidTransition() {
        UUID incidentId = UUID.randomUUID();
        SecurityIncident incident = new SecurityIncident();
        incident.setId(incidentId);
        incident.setWorkspaceId(workspaceId);
        incident.setStatus(IncidentStatus.CLOSED);
        incident.setIncidentNumber("INC-0001");

        when(incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId))
                .thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> incidentService.updateIncidentStatus(
                workspaceId,
                incidentId,
                IncidentStatus.RESOLVED,
                "Try resolving closed incident",
                actorId
        )).isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid incident status transition");
    }

    @Test
    @DisplayName("Remediate incident with dry-run returns preview without executing mutations")
    void testRemediateIncident_DryRun() {
        UUID incidentId = UUID.randomUUID();
        SecurityIncident incident = new SecurityIncident();
        incident.setId(incidentId);
        incident.setWorkspaceId(workspaceId);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setIncidentNumber("INC-0001");

        when(incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId))
                .thenReturn(Optional.of(incident));

        IncidentRemediationRequest req = new IncidentRemediationRequest(
                RemediationPlaybookAction.ROTATE_SECRET,
                UUID.randomUUID(),
                null,
                "Dry run test",
                true
        );

        IncidentRemediationResult res = incidentService.remediateIncident(workspaceId, incidentId, req, actorId);

        assertThat(res.dryRun()).isTrue();
        assertThat(res.executed()).isFalse();
        assertThat(res.status()).isEqualTo("DRY_RUN_PREVIEW");
        verifyNoInteractions(rotationService);
    }

    @Test
    @DisplayName("Remediate incident executes ROTATE_SECRET and links remediation event")
    void testRemediateIncident_LiveExecution() {
        UUID incidentId = UUID.randomUUID();
        UUID secretId = UUID.randomUUID();

        SecurityIncident incident = new SecurityIncident();
        incident.setId(incidentId);
        incident.setWorkspaceId(workspaceId);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setIncidentNumber("INC-0001");

        when(incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId))
                .thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(SecurityIncident.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        IncidentRemediationRequest req = new IncidentRemediationRequest(
                RemediationPlaybookAction.ROTATE_SECRET,
                secretId,
                null,
                "Emergency secret rotation",
                false
        );

        IncidentRemediationResult res = incidentService.remediateIncident(workspaceId, incidentId, req, actorId);

        assertThat(res.dryRun()).isFalse();
        assertThat(res.executed()).isTrue();
        assertThat(res.status()).isEqualTo("EXECUTED");
        assertThat(res.updatedIncidentStatus()).isEqualTo(IncidentStatus.REMEDIATION);

        verify(rotationService).triggerRotation(eq(workspaceId), eq(secretId), any(), eq(actorId), any());
        verify(incidentEventRepository).save(any());
        verify(auditService).recordAudit(any(), eq(workspaceId), eq(actorId), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Remediate incident dry-run validates and rejects missing targetSecretId")
    void testRemediateIncident_DryRun_RejectsMissingTargetSecret() {
        UUID incidentId = UUID.randomUUID();
        SecurityIncident incident = new SecurityIncident();
        incident.setId(incidentId);
        incident.setWorkspaceId(workspaceId);
        incident.setStatus(IncidentStatus.OPEN);

        when(incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId))
                .thenReturn(Optional.of(incident));

        IncidentRemediationRequest req = new IncidentRemediationRequest(
                RemediationPlaybookAction.ROTATE_SECRET,
                null, // Missing targetSecretId
                null,
                "Dry run without target",
                true
        );

        assertThatThrownBy(() -> incidentService.remediateIncident(workspaceId, incidentId, req, actorId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("targetSecretId is required");
    }

    @Test
    @DisplayName("Remediate incident dry-run validates and rejects cross-workspace secret")
    void testRemediateIncident_DryRun_RejectsCrossWorkspaceSecret() {
        UUID incidentId = UUID.randomUUID();
        UUID crossTenantSecretId = UUID.randomUUID();

        SecurityIncident incident = new SecurityIncident();
        incident.setId(incidentId);
        incident.setWorkspaceId(workspaceId);
        incident.setStatus(IncidentStatus.OPEN);

        when(incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId))
                .thenReturn(Optional.of(incident));
        when(effectiveAccessService.evaluateAccess(eq(workspaceId), isNull(), isNull(), eq(crossTenantSecretId), any(), eq(actorId)))
                .thenReturn(AccessDecision.deny(com.secretvault.access.model.AccessPermission.SECRET_ROTATION_EMERGENCY,
                        com.secretvault.access.model.AccessScope.PROJECT, "Project not found in this workspace"));

        IncidentRemediationRequest req = new IncidentRemediationRequest(
                RemediationPlaybookAction.ROTATE_SECRET,
                crossTenantSecretId,
                null,
                "Dry run target from other tenant",
                true
        );

        assertThatThrownBy(() -> incidentService.remediateIncident(workspaceId, incidentId, req, actorId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Target secret does not belong to this workspace");
    }

    @Test
    @DisplayName("Remediate incident rejects attempts on CLOSED incident")
    void testRemediateIncident_ClosedIncident_Rejected() {
        UUID incidentId = UUID.randomUUID();
        SecurityIncident incident = new SecurityIncident();
        incident.setId(incidentId);
        incident.setWorkspaceId(workspaceId);
        incident.setStatus(IncidentStatus.CLOSED);

        when(incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId))
                .thenReturn(Optional.of(incident));

        IncidentRemediationRequest req = new IncidentRemediationRequest(
                RemediationPlaybookAction.CONTAIN_INCIDENT,
                null,
                null,
                "Attempt to contain closed incident",
                false
        );

        assertThatThrownBy(() -> incidentService.remediateIncident(workspaceId, incidentId, req, actorId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Cannot execute remediation on a CLOSED incident");
    }

    @Test
    @DisplayName("Remediate incident ROTATE_SECRET captures rotation job outcome and uses secret-scoped idempotency key")
    void testRemediateIncident_RotateSecret_CapturesJobMetadataAndDistinctIdempotencyKey() {
        UUID incidentId = UUID.randomUUID();
        UUID secretId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        SecurityIncident incident = new SecurityIncident();
        incident.setId(incidentId);
        incident.setWorkspaceId(workspaceId);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setIncidentNumber("INC-0042");

        when(incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId))
                .thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(SecurityIncident.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RotationJobResponse mockJob = mock(RotationJobResponse.class);
        when(mockJob.id()).thenReturn(jobId);
        when(mockJob.status()).thenReturn(RotationStatus.QUEUED);

        String expectedIdempotencyKey = "INC-ROT-" + incidentId + "-" + secretId;
        when(rotationService.triggerRotation(eq(workspaceId), eq(secretId), any(), eq(actorId), eq(expectedIdempotencyKey)))
                .thenReturn(mockJob);

        IncidentRemediationRequest req = new IncidentRemediationRequest(
                RemediationPlaybookAction.ROTATE_SECRET,
                secretId,
                null,
                "Compromised credential remediation",
                false
        );

        IncidentRemediationResult res = incidentService.remediateIncident(workspaceId, incidentId, req, actorId);

        assertThat(res.executed()).isTrue();
        assertThat(res.metadata()).containsEntry("rotationJobId", jobId.toString());
        assertThat(res.metadata()).containsEntry("rotationStatus", "QUEUED");
        assertThat(res.details()).contains("Job ID: " + jobId);
    }

    @Test
    @DisplayName("Remediate incident REVOKE_SECRET_LEASES propagates actorId and captures revoked lease count")
    void testRemediateIncident_RevokeLeases_PropagatesActorIdAndCapturesRevokedCount() {
        UUID incidentId = UUID.randomUUID();
        UUID secretId = UUID.randomUUID();
        UUID leaseId1 = UUID.randomUUID();
        UUID leaseId2 = UUID.randomUUID();

        SecurityIncident incident = new SecurityIncident();
        incident.setId(incidentId);
        incident.setWorkspaceId(workspaceId);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setIncidentNumber("INC-0043");

        when(incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId))
                .thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(SecurityIncident.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SecretLeaseResponse lease1 = mock(SecretLeaseResponse.class);
        when(lease1.id()).thenReturn(leaseId1);
        SecretLeaseResponse lease2 = mock(SecretLeaseResponse.class);
        when(lease2.id()).thenReturn(leaseId2);

        when(leaseService.listLeases(eq(workspaceId), eq(secretId), isNull(), any(), eq(actorId)))
                .thenReturn(new PageImpl<>(List.of(lease1, lease2)));

        IncidentRemediationRequest req = new IncidentRemediationRequest(
                RemediationPlaybookAction.REVOKE_SECRET_LEASES,
                secretId,
                null,
                "Revoke exposed leases",
                false
        );

        IncidentRemediationResult res = incidentService.remediateIncident(workspaceId, incidentId, req, actorId);

        assertThat(res.executed()).isTrue();
        assertThat(res.metadata()).containsEntry("revokedLeaseCount", 2);
        assertThat(res.details()).contains("Revoked 2 active lease(s)");

        verify(leaseService).revokeLease(workspaceId, leaseId1, actorId);
        verify(leaseService).revokeLease(workspaceId, leaseId2, actorId);
    }

    @Test
    @DisplayName("Remediate incident DISABLE_MACHINE_IDENTITY pre-validates and captures machine name")
    void testRemediateIncident_DisableMachine_ValidatesAndDisables() {
        UUID incidentId = UUID.randomUUID();
        UUID machineId = UUID.randomUUID();

        SecurityIncident incident = new SecurityIncident();
        incident.setId(incidentId);
        incident.setWorkspaceId(workspaceId);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setIncidentNumber("INC-0044");

        when(incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId))
                .thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(SecurityIncident.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MachineIdentityResponse mockMachine = mock(MachineIdentityResponse.class);
        when(mockMachine.name()).thenReturn("compromised-ci-bot");
        when(mockMachine.status()).thenReturn(MachineStatus.DISABLED);

        when(machineService.getMachineIdentity(machineId, workspaceId)).thenReturn(mockMachine);
        when(machineService.disableMachineIdentity(machineId, workspaceId, actorId)).thenReturn(mockMachine);

        IncidentRemediationRequest req = new IncidentRemediationRequest(
                RemediationPlaybookAction.DISABLE_MACHINE_IDENTITY,
                null,
                machineId,
                "Disable compromised CI runner",
                false
        );

        IncidentRemediationResult res = incidentService.remediateIncident(workspaceId, incidentId, req, actorId);

        assertThat(res.executed()).isTrue();
        assertThat(res.metadata()).containsEntry("machineName", "compromised-ci-bot");
        assertThat(res.details()).contains("compromised-ci-bot");
        verify(machineService).disableMachineIdentity(machineId, workspaceId, actorId);
    }

    @Test
    @DisplayName("Remediate incident with requireApproval creates PENDING approval record and returns PENDING_APPROVAL")
    void testRemediateIncident_RequiresApproval_CreatesPendingApproval() {
        UUID incidentId = UUID.randomUUID();
        UUID secretId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();

        SecurityIncident incident = new SecurityIncident();
        incident.setId(incidentId);
        incident.setWorkspaceId(workspaceId);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setIncidentNumber("INC-0050");

        when(incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId))
                .thenReturn(Optional.of(incident));
        when(approvalRepository.save(any(SecurityIncidentApproval.class))).thenAnswer(inv -> {
            SecurityIncidentApproval a = inv.getArgument(0);
            a.setId(approvalId);
            return a;
        });

        IncidentRemediationRequest req = new IncidentRemediationRequest(
                RemediationPlaybookAction.ROTATE_SECRET,
                secretId,
                null,
                "Requires dual authorization",
                false,
                true, // requireApproval
                null,
                false
        );

        IncidentRemediationResult res = incidentService.remediateIncident(workspaceId, incidentId, req, actorId);

        assertThat(res.executed()).isFalse();
        assertThat(res.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(res.approvalId()).isEqualTo(approvalId);
        verify(approvalRepository).save(any(SecurityIncidentApproval.class));
        verifyNoInteractions(rotationService);
    }

    @Test
    @DisplayName("Decide approval rejects requester self-approval with 403 Forbidden")
    void testDecideApproval_SelfApproval_Forbidden() {
        UUID incidentId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();

        SecurityIncident incident = new SecurityIncident();
        incident.setId(incidentId);
        incident.setWorkspaceId(workspaceId);

        SecurityIncidentApproval approval = new SecurityIncidentApproval();
        approval.setId(approvalId);
        approval.setWorkspaceId(workspaceId);
        approval.setIncidentId(incidentId);
        approval.setStatus(IncidentApprovalStatus.PENDING);
        approval.setRequestedBy(actorId); // actorId requested it
        approval.setExpiresAt(Instant.now().plusSeconds(3600));

        when(incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId))
                .thenReturn(Optional.of(incident));
        when(approvalRepository.findByIdAndIncidentIdAndWorkspaceId(approvalId, incidentId, workspaceId))
                .thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> incidentService.decideRemediationApproval(workspaceId, incidentId, approvalId, true, "Self approval attempt", actorId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Requester cannot approve their own remediation action");
    }

    @Test
    @DisplayName("Decide approval rejects incident creator approval with 403 Forbidden (separation of duties)")
    void testDecideApproval_IncidentCreator_Forbidden() {
        UUID incidentId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        SecurityIncident incident = new SecurityIncident();
        incident.setId(incidentId);
        incident.setWorkspaceId(workspaceId);
        incident.setCreatedBy(creatorId); // creatorId created incident

        SecurityIncidentApproval approval = new SecurityIncidentApproval();
        approval.setId(approvalId);
        approval.setWorkspaceId(workspaceId);
        approval.setIncidentId(incidentId);
        approval.setStatus(IncidentApprovalStatus.PENDING);
        approval.setRequestedBy(requesterId);
        approval.setExpiresAt(Instant.now().plusSeconds(3600));

        when(incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId))
                .thenReturn(Optional.of(incident));
        when(approvalRepository.findByIdAndIncidentIdAndWorkspaceId(approvalId, incidentId, workspaceId))
                .thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> incidentService.decideRemediationApproval(workspaceId, incidentId, approvalId, true, "Creator approving", creatorId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Incident creator cannot approve high-impact remediation action");
    }

    @Test
    @DisplayName("Decide approval by independent authorized second operator approves request")
    void testDecideApproval_SecondApprover_Success() {
        UUID incidentId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID secondApproverId = UUID.randomUUID();

        SecurityIncident incident = new SecurityIncident();
        incident.setId(incidentId);
        incident.setWorkspaceId(workspaceId);
        incident.setCreatedBy(UUID.randomUUID());

        SecurityIncidentApproval approval = new SecurityIncidentApproval();
        approval.setId(approvalId);
        approval.setWorkspaceId(workspaceId);
        approval.setIncidentId(incidentId);
        approval.setAction(RemediationPlaybookAction.ROTATE_SECRET);
        approval.setStatus(IncidentApprovalStatus.PENDING);
        approval.setRequestedBy(requesterId);
        approval.setExpiresAt(Instant.now().plusSeconds(3600));

        when(incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId))
                .thenReturn(Optional.of(incident));
        when(approvalRepository.findByIdAndIncidentIdAndWorkspaceId(approvalId, incidentId, workspaceId))
                .thenReturn(Optional.of(approval));
        when(approvalRepository.save(any(SecurityIncidentApproval.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        SecurityIncidentApproval decided = incidentService.decideRemediationApproval(
                workspaceId,
                incidentId,
                approvalId,
                true,
                "Approved after review",
                secondApproverId
        );

        assertThat(decided.getStatus()).isEqualTo(IncidentApprovalStatus.APPROVED);
        assertThat(decided.getDecidedBy()).isEqualTo(secondApproverId);
        assertThat(decided.getDecisionReason()).isEqualTo("Approved after review");
    }

    @Test
    @DisplayName("Remediate incident with valid approved approval executes and transitions approval to EXECUTED")
    void testRemediateIncident_WithApprovedApproval_ExecutesSuccessfullyAndMarksApprovalExecuted() {
        UUID incidentId = UUID.randomUUID();
        UUID secretId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        String notes = "Emergency secret rotation approved";

        SecurityIncident incident = new SecurityIncident();
        incident.setId(incidentId);
        incident.setWorkspaceId(workspaceId);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setIncidentNumber("INC-0051");

        String payloadHash = SecurityIncidentService.computeActionPayloadHash(
                RemediationPlaybookAction.ROTATE_SECRET,
                secretId,
                null,
                notes
        );

        SecurityIncidentApproval approval = new SecurityIncidentApproval();
        approval.setId(approvalId);
        approval.setWorkspaceId(workspaceId);
        approval.setIncidentId(incidentId);
        approval.setAction(RemediationPlaybookAction.ROTATE_SECRET);
        approval.setTargetSecretId(secretId);
        approval.setTargetMachineIdentityId(null);
        approval.setNotes(notes);
        approval.setActionPayloadHash(payloadHash);
        approval.setStatus(IncidentApprovalStatus.APPROVED);
        approval.setRequestedBy(UUID.randomUUID());
        approval.setDecidedBy(UUID.randomUUID());
        approval.setExpiresAt(Instant.now().plusSeconds(3600));

        when(incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId))
                .thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(SecurityIncident.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(approvalRepository.findByIdAndIncidentIdAndWorkspaceId(approvalId, incidentId, workspaceId))
                .thenReturn(Optional.of(approval));
        when(approvalRepository.save(any(SecurityIncidentApproval.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        IncidentRemediationRequest req = new IncidentRemediationRequest(
                RemediationPlaybookAction.ROTATE_SECRET,
                secretId,
                null,
                notes,
                false,
                false,
                approvalId,
                false
        );

        IncidentRemediationResult res = incidentService.remediateIncident(workspaceId, incidentId, req, actorId);

        assertThat(res.executed()).isTrue();
        assertThat(res.status()).isEqualTo("EXECUTED");
        verify(rotationService).triggerRotation(eq(workspaceId), eq(secretId), any(), eq(actorId), any());
        verify(approvalRepository).save(argThat(a -> a.getStatus() == IncidentApprovalStatus.EXECUTED));
    }

    @Test
    @DisplayName("Remediate incident with approved approval rejects request if payload parameters were tampered")
    void testRemediateIncident_WithApproval_RejectsPayloadTampering() {
        UUID incidentId = UUID.randomUUID();
        UUID secretIdOriginal = UUID.randomUUID();
        UUID secretIdTampered = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();

        SecurityIncident incident = new SecurityIncident();
        incident.setId(incidentId);
        incident.setWorkspaceId(workspaceId);
        incident.setStatus(IncidentStatus.OPEN);

        String originalHash = SecurityIncidentService.computeActionPayloadHash(
                RemediationPlaybookAction.ROTATE_SECRET,
                secretIdOriginal,
                null,
                "Original notes"
        );

        SecurityIncidentApproval approval = new SecurityIncidentApproval();
        approval.setId(approvalId);
        approval.setWorkspaceId(workspaceId);
        approval.setIncidentId(incidentId);
        approval.setAction(RemediationPlaybookAction.ROTATE_SECRET);
        approval.setTargetSecretId(secretIdOriginal);
        approval.setActionPayloadHash(originalHash);
        approval.setStatus(IncidentApprovalStatus.APPROVED);
        approval.setExpiresAt(Instant.now().plusSeconds(3600));

        when(incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId))
                .thenReturn(Optional.of(incident));
        when(approvalRepository.findByIdAndIncidentIdAndWorkspaceId(approvalId, incidentId, workspaceId))
                .thenReturn(Optional.of(approval));

        // Attempting to execute with tampered secretId
        IncidentRemediationRequest req = new IncidentRemediationRequest(
                RemediationPlaybookAction.ROTATE_SECRET,
                secretIdTampered,
                null,
                "Original notes",
                false,
                false,
                approvalId,
                false
        );

        assertThatThrownBy(() -> incidentService.remediateIncident(workspaceId, incidentId, req, actorId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("hash mismatch");
    }

    @Test
    @DisplayName("Remediate incident rejects expired approval request")
    void testRemediateIncident_WithExpiredApproval_Rejected() {
        UUID incidentId = UUID.randomUUID();
        UUID secretId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();

        SecurityIncident incident = new SecurityIncident();
        incident.setId(incidentId);
        incident.setWorkspaceId(workspaceId);
        incident.setStatus(IncidentStatus.OPEN);

        SecurityIncidentApproval approval = new SecurityIncidentApproval();
        approval.setId(approvalId);
        approval.setWorkspaceId(workspaceId);
        approval.setIncidentId(incidentId);
        approval.setAction(RemediationPlaybookAction.ROTATE_SECRET);
        approval.setTargetSecretId(secretId);
        approval.setStatus(IncidentApprovalStatus.APPROVED);
        approval.setExpiresAt(Instant.now().minusSeconds(120)); // Expired

        when(incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId))
                .thenReturn(Optional.of(incident));
        when(approvalRepository.findByIdAndIncidentIdAndWorkspaceId(approvalId, incidentId, workspaceId))
                .thenReturn(Optional.of(approval));

        IncidentRemediationRequest req = new IncidentRemediationRequest(
                RemediationPlaybookAction.ROTATE_SECRET,
                secretId,
                null,
                "Expired run",
                false,
                false,
                approvalId,
                false
        );

        assertThatThrownBy(() -> incidentService.remediateIncident(workspaceId, incidentId, req, actorId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Remediate incident with async=true queues task in repository and returns QUEUED")
    void testRemediateIncident_Async_QueuesRemediationRecord() {
        UUID incidentId = UUID.randomUUID();
        UUID secretId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();

        SecurityIncident incident = new SecurityIncident();
        incident.setId(incidentId);
        incident.setWorkspaceId(workspaceId);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setIncidentNumber("INC-0052");

        when(incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId))
                .thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(SecurityIncident.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(remediationRepository.save(any(SecurityIncidentRemediation.class))).thenAnswer(inv -> {
            SecurityIncidentRemediation r = inv.getArgument(0);
            r.setId(executionId);
            return r;
        });

        IncidentRemediationRequest req = new IncidentRemediationRequest(
                RemediationPlaybookAction.ROTATE_SECRET,
                secretId,
                null,
                "Queued async execution",
                false,
                false,
                null,
                true // async
        );

        IncidentRemediationResult res = incidentService.remediateIncident(workspaceId, incidentId, req, actorId);

        assertThat(res.executed()).isFalse();
        assertThat(res.status()).isEqualTo("QUEUED");
        assertThat(res.executionId()).isEqualTo(executionId);
        verify(remediationRepository).save(any(SecurityIncidentRemediation.class));
        verifyNoInteractions(rotationService);
    }

    @Test
    @DisplayName("Execute remediation task claims atomically and completes playbook with verified effects")
    void testExecuteRemediationTask_WorkerClaimsAndCompletes() {
        UUID executionId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        UUID secretId = UUID.randomUUID();

        SecurityIncident incident = new SecurityIncident();
        incident.setId(incidentId);
        incident.setWorkspaceId(workspaceId);
        incident.setStatus(IncidentStatus.REMEDIATION);
        incident.setIncidentNumber("INC-0053");

        SecurityIncidentRemediation rem = new SecurityIncidentRemediation();
        rem.setId(executionId);
        rem.setWorkspaceId(workspaceId);
        rem.setIncidentId(incidentId);
        rem.setAction(RemediationPlaybookAction.ROTATE_SECRET);
        rem.setTargetSecretId(secretId);
        rem.setActorId(actorId);
        rem.setStatus(IncidentRemediationStatus.CLAIMED);

        when(remediationRepository.claimRemediationAtomic(eq(executionId), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(remediationRepository.findById(executionId))
                .thenReturn(Optional.of(rem));
        when(incidentRepository.findById(incidentId))
                .thenReturn(Optional.of(incident));

        incidentService.executeRemediationTask(executionId, "worker-node-1");

        verify(rotationService).triggerRotation(eq(workspaceId), eq(secretId), any(), eq(actorId), any());
        verify(remediationRepository).completeRemediationGuarded(
                eq(executionId),
                eq(IncidentRemediationStatus.COMPLETED),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Remediate incident REVOKE_SECRET_LEASES records PARTIAL_FAILURE when some leases fail")
    void testRemediateIncident_RevokeLeases_PartialFailure() {
        UUID incidentId = UUID.randomUUID();
        UUID secretId = UUID.randomUUID();
        UUID lease1 = UUID.randomUUID();
        UUID lease2 = UUID.randomUUID();

        SecurityIncident incident = new SecurityIncident();
        incident.setId(incidentId);
        incident.setWorkspaceId(workspaceId);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setIncidentNumber("INC-0054");

        SecretLeaseResponse l1 = mock(SecretLeaseResponse.class);
        when(l1.id()).thenReturn(lease1);
        SecretLeaseResponse l2 = mock(SecretLeaseResponse.class);
        when(l2.id()).thenReturn(lease2);

        when(incidentRepository.findByIdAndWorkspaceId(incidentId, workspaceId))
                .thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(SecurityIncident.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(leaseService.listLeases(eq(workspaceId), eq(secretId), any(), any(), eq(actorId)))
                .thenReturn(new PageImpl<>(List.of(l1, l2)));

        // lease1 succeeds, lease2 fails
        doNothing().when(leaseService).revokeLease(workspaceId, lease1, actorId);
        doThrow(new RuntimeException("Database timeout on lease 2")).when(leaseService).revokeLease(workspaceId, lease2, actorId);

        IncidentRemediationRequest req = new IncidentRemediationRequest(
                RemediationPlaybookAction.REVOKE_SECRET_LEASES,
                secretId,
                null,
                "Partial lease revocation test",
                false
        );

        IncidentRemediationResult res = incidentService.remediateIncident(workspaceId, incidentId, req, actorId);

        assertThat(res.status()).isEqualTo("PARTIAL_FAILURE");
        assertThat(res.executed()).isTrue();
        assertThat(res.details()).contains("Partially revoked 1 of 2 leases");
        assertThat(res.metadata()).containsEntry("revokedLeaseCount", 1);
        assertThat(res.metadata()).containsEntry("failedLeaseCount", 1);
    }

    @Test
    @DisplayName("Recover stale remediations atomically resets claimed tasks past timeout")
    void testRecoverStaleRemediations_ResetsStaleClaimedTasks() {
        when(remediationRepository.recoverStaleRemediationsAtomic(any(), any(), any(), any()))
                .thenReturn(4);

        int recovered = incidentService.recoverStaleRemediations(java.time.Duration.ofMinutes(5));

        assertThat(recovered).isEqualTo(4);
        verify(remediationRepository).recoverStaleRemediationsAtomic(
                eq(IncidentRemediationStatus.QUEUED),
                eq(IncidentRemediationStatus.CLAIMED),
                any(),
                any()
        );
    }
}
