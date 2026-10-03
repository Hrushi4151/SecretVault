package com.secretvault.repository;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.repository.entity.FindingRemediationJob;
import com.secretvault.repository.entity.SecretFinding;
import com.secretvault.repository.model.RemediationAction;
import com.secretvault.repository.model.RemediationStatus;
import com.secretvault.repository.model.RepoFindingSeverity;
import com.secretvault.repository.model.RepoFindingStatus;
import com.secretvault.repository.model.SecretType;
import com.secretvault.repository.repository.FindingAllowlistRepository;
import com.secretvault.repository.repository.FindingRemediationJobRepository;
import com.secretvault.repository.repository.RepositoryEntityRepository;
import com.secretvault.repository.repository.SecretFindingOccurrenceRepository;
import com.secretvault.repository.repository.SecretFindingRepository;
import com.secretvault.repository.service.FindingRemediationService;
import com.secretvault.repository.service.SecretFindingService;
import com.secretvault.repository.validator.LiveCredentialValidator;
import com.secretvault.rotation.dto.RotationDtos.TriggerRotationRequest;
import com.secretvault.rotation.service.RotationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 12: Zero Plaintext Invariant, Finding Lifecycle & Rotation Cascade")
class ZeroPlaintextInvariantAndRemediationTest {

    @Mock
    private SecretFindingRepository findingRepository;
    @Mock
    private SecretFindingOccurrenceRepository occurrenceRepository;
    @Mock
    private RepositoryEntityRepository repoRepository;
    @Mock
    private FindingAllowlistRepository allowlistRepository;
    @Mock
    private LiveCredentialValidator liveValidator;
    @Mock
    private EffectiveAccessService accessService;
    @Mock
    private AuditService auditService;
    @Mock
    private FindingRemediationJobRepository remediationJobRepository;
    @Mock
    private RotationService rotationService;

    private SecretFindingService findingService;
    private FindingRemediationService remediationService;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID findingId = UUID.randomUUID();
    private final UUID repositoryId = UUID.randomUUID();
    private final UUID correlatedSecretId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        findingService = new SecretFindingService(
                findingRepository,
                occurrenceRepository,
                repoRepository,
                allowlistRepository,
                liveValidator,
                accessService,
                auditService
        );

        remediationService = new FindingRemediationService(
                findingRepository,
                remediationJobRepository,
                rotationService,
                findingService,
                accessService,
                auditService
        );
    }

    @Test
    @DisplayName("Zero Plaintext Invariant: Finding entity stores only masked evidence and SHA-256 fingerprint")
    void testZeroPlaintextStoredInFinding() {
        SecretFinding finding = new SecretFinding();
        finding.setId(findingId);
        finding.setWorkspaceId(workspaceId);
        finding.setRepositoryId(repositoryId);
        finding.setDetectorType("AWS Access Key");
        finding.setSecretType(SecretType.AWS_ACCESS_KEY);
        finding.setSeverity(RepoFindingSeverity.CRITICAL);
        finding.setStatus(RepoFindingStatus.DETECTED);
        finding.setFilePath("deploy/aws-config.env");
        finding.setLineNumber(14);
        finding.setMaskedValue("AKI************PLE");
        finding.setFingerprint("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

        when(findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)).thenReturn(Optional.of(finding));

        SecretFindingService.WhyExposedExplanation explanation = findingService.generateWhyExposed(workspaceId, findingId, actorId);
        assertNotNull(explanation);
        assertEquals("AKI************PLE", explanation.maskedValue());
        assertTrue(explanation.maskedValue().contains("************"));
        assertFalse(explanation.maskedValue().contains("AKIAIOSFODNN7EXAMPLE"));
        assertEquals("deploy/aws-config.env", explanation.filePath());
    }

    @Test
    @DisplayName("Finding status updates enforce RBAC and trigger audit logging")
    void testUpdateFindingStatusEnforcesRbacAndAudits() {
        SecretFinding finding = new SecretFinding();
        finding.setId(findingId);
        finding.setWorkspaceId(workspaceId);
        finding.setStatus(RepoFindingStatus.DETECTED);

        when(findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)).thenReturn(Optional.of(finding));
        when(findingRepository.save(any(SecretFinding.class))).thenAnswer(inv -> inv.getArgument(0));

        SecretFinding updated = findingService.updateStatus(
                workspaceId,
                findingId,
                RepoFindingStatus.CONFIRMED,
                "Confirmed compromised AWS key",
                actorId
        );

        assertEquals(RepoFindingStatus.CONFIRMED, updated.getStatus());
        verify(accessService).requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_FINDING_MANAGE);
        verify(auditService).record(eq(workspaceId), eq(actorId), eq(AuditAction.FINDING_CONFIRMED), any(), any(), any());
    }

    @Test
    @DisplayName("Remediating finding with ROTATE_SECRET invokes RotationService and logs audit")
    void testRemediateFindingEmergencyRotationCascade() {
        SecretFinding finding = new SecretFinding();
        finding.setId(findingId);
        finding.setWorkspaceId(workspaceId);
        finding.setStatus(RepoFindingStatus.CONFIRMED);
        finding.setMatchedSecretId(correlatedSecretId);
        finding.setMaskedValue("AKI************PLE");

        when(findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)).thenReturn(Optional.of(finding));
        when(remediationJobRepository.save(any(FindingRemediationJob.class))).thenAnswer(inv -> {
            FindingRemediationJob j = inv.getArgument(0);
            j.setId(UUID.randomUUID());
            return j;
        });

        FindingRemediationJob job = remediationService.remediateFinding(
                workspaceId,
                findingId,
                RemediationAction.ROTATE_SECRET,
                "Triggering zero-downtime rotation for exposed key",
                actorId
        );

        assertNotNull(job);
        assertEquals(RemediationAction.ROTATE_SECRET, job.getAction());

        // Verify cascading call to RotationService
        verify(rotationService).triggerRotation(
                eq(workspaceId),
                eq(correlatedSecretId),
                any(TriggerRotationRequest.class),
                eq(actorId),
                isNull()
        );

        verify(accessService).requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_REMEDIATE);
        verify(auditService).record(eq(workspaceId), eq(actorId), eq(AuditAction.REMEDIATION_STARTED), any(), any(), any());
    }
}
