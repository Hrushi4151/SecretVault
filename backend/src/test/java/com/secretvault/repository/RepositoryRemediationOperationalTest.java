package com.secretvault.repository;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.repository.dto.RepositoryDtos.FindingAllowlistDto;
import com.secretvault.repository.dto.RepositoryDtos.RemediateFindingRequest;
import com.secretvault.repository.dto.RepositoryDtos.RemediationTargetInfo;
import com.secretvault.repository.entity.FindingAllowlist;
import com.secretvault.repository.entity.FindingRemediationJob;
import com.secretvault.repository.entity.SecretFinding;
import com.secretvault.repository.model.*;
import com.secretvault.repository.repository.FindingAllowlistRepository;
import com.secretvault.repository.repository.FindingRemediationJobRepository;
import com.secretvault.repository.repository.RepositoryEntityRepository;
import com.secretvault.repository.repository.SecretFindingOccurrenceRepository;
import com.secretvault.repository.repository.SecretFindingRepository;
import com.secretvault.repository.service.FindingRemediationService;
import com.secretvault.repository.service.SecretFindingService;
import com.secretvault.repository.service.SecretInventoryCorrelator;
import com.secretvault.repository.validator.LiveCredentialValidator;
import com.secretvault.rotation.dto.RotationDtos.MarkCompromisedRequest;
import com.secretvault.rotation.dto.RotationDtos.RotationJobResponse;
import com.secretvault.rotation.dto.RotationDtos.TriggerRotationRequest;
import com.secretvault.rotation.model.RotationStatus;
import com.secretvault.rotation.model.RotationStrategy;
import com.secretvault.rotation.service.RotationService;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.repository.SecretRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecretVault — Operational Repository Finding Remediation Test Suite")
class RepositoryRemediationOperationalTest {

    @Mock
    private SecretFindingRepository findingRepository;
    @Mock
    private FindingRemediationJobRepository remediationJobRepository;
    @Mock
    private FindingAllowlistRepository allowlistRepository;
    @Mock
    private SecretFindingOccurrenceRepository occurrenceRepository;
    @Mock
    private RepositoryEntityRepository repoRepository;
    @Mock
    private RotationService rotationService;
    @Mock
    private EffectiveAccessService accessService;
    @Mock
    private AuditService auditService;
    @Mock
    private SecretRepository secretRepository;
    @Mock
    private EnvironmentRepository environmentRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private SecretInventoryCorrelator correlator;
    @Mock
    private LiveCredentialValidator liveValidator;

    private SecretFindingService secretFindingService;
    private FindingRemediationService remediationService;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID findingId = UUID.randomUUID();
    private final UUID repositoryId = UUID.randomUUID();
    private final UUID matchedSecretId = UUID.randomUUID();
    private final UUID environmentId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        secretFindingService = new SecretFindingService(
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
                secretFindingService,
                accessService,
                auditService,
                secretRepository,
                environmentRepository,
                projectRepository,
                correlator,
                liveValidator
        );
    }

    private SecretFinding createFinding(boolean isMatched) {
        SecretFinding finding = new SecretFinding();
        finding.setId(findingId);
        finding.setWorkspaceId(workspaceId);
        finding.setRepositoryId(repositoryId);
        finding.setDetectorType("AWS Access Key");
        finding.setSecretType(SecretType.AWS_ACCESS_KEY);
        finding.setSeverity(RepoFindingSeverity.CRITICAL);
        finding.setConfidence(RepoFindingConfidence.HIGH);
        finding.setStatus(RepoFindingStatus.DETECTED);
        finding.setRemediationStatus(RemediationStatus.PENDING);
        finding.setFilePath("src/config/aws.js");
        finding.setLineNumber(12);
        finding.setMaskedValue("AK************12");
        finding.setFingerprint("6b86b273ff34fce19d6b804eff5a3f5747ada4eaa22f1d49c01e52ddb7875b4b");
        finding.setCommitSha("commit_sha_12345");
        finding.setBranch("main");
        if (isMatched) {
            finding.setMatchedSecretId(matchedSecretId);
        }
        return finding;
    }

    private Secret createManagedSecret() {
        Secret secret = new Secret();
        secret.setId(matchedSecretId);
        secret.setEnvironmentId(environmentId);
        secret.setName("PROD_AWS_ACCESS_KEY");
        secret.setCurrentVersionNumber(3);
        return secret;
    }

    @Test
    @DisplayName("Action A: Successful Emergency Rotation for Managed SecretVault Credential")
    void testSuccessfulEmergencyRotationForManagedSecret() {
        SecretFinding finding = createFinding(true);
        when(findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)).thenReturn(Optional.of(finding));
        when(remediationJobRepository.existsByFindingIdAndStatusIn(eq(findingId), anyList())).thenReturn(false);
        when(remediationJobRepository.save(any(FindingRemediationJob.class))).thenAnswer(inv -> {
            FindingRemediationJob j = inv.getArgument(0);
            if (j.getId() == null) j.setId(UUID.randomUUID());
            return j;
        });

        RotationJobResponse rotJob = new RotationJobResponse(
                UUID.randomUUID(),
                workspaceId,
                matchedSecretId,
                null,
                RotationStatus.COMPLETED,
                RotationStrategy.EMERGENCY,
                0,
                3,
                4,
                3,
                actorId,
                null,
                "AUTOMATED",
                Instant.now(),
                Instant.now(),
                Instant.now(),
                null,
                Instant.now(),
                null,
                null,
                Instant.now(),
                Instant.now()
        );

        when(rotationService.triggerRotation(eq(workspaceId), eq(matchedSecretId), any(TriggerRotationRequest.class), eq(actorId), isNull()))
                .thenReturn(rotJob);

        FindingRemediationJob job = remediationService.remediateFinding(
                workspaceId, findingId, RemediationAction.ROTATE_SECRET, "Approved emergency rotation after leak", actorId
        );

        assertNotNull(job);
        assertEquals(RemediationStatus.ROTATION_SUCCEEDED, job.getStatus());
        assertEquals(RepoFindingStatus.RESOLVED, finding.getStatus());
        assertEquals(RemediationStatus.ROTATED, finding.getRemediationStatus());

        verify(rotationService).triggerRotation(eq(workspaceId), eq(matchedSecretId), any(TriggerRotationRequest.class), eq(actorId), isNull());
        verify(auditService).record(eq(workspaceId), eq(actorId), eq(AuditAction.SECRET_ROTATION_TRIGGERED), eq("SECRET"), eq(matchedSecretId.toString()), anyMap());
        verify(auditService).record(eq(workspaceId), eq(actorId), eq(AuditAction.REMEDIATION_COMPLETED), eq("FINDING_REMEDIATION"), anyString(), anyMap());
    }

    @Test
    @DisplayName("Action A: Failed Rotation Updates Finding to ROTATION_FAILED without Fake Success")
    void testFailedEmergencyRotationHandledCleanly() {
        SecretFinding finding = createFinding(true);
        when(findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)).thenReturn(Optional.of(finding));
        when(remediationJobRepository.existsByFindingIdAndStatusIn(eq(findingId), anyList())).thenReturn(false);
        when(remediationJobRepository.save(any(FindingRemediationJob.class))).thenAnswer(inv -> {
            FindingRemediationJob j = inv.getArgument(0);
            if (j.getId() == null) j.setId(UUID.randomUUID());
            return j;
        });

        RotationJobResponse rotJob = new RotationJobResponse(
                UUID.randomUUID(),
                workspaceId,
                matchedSecretId,
                null,
                RotationStatus.FAILED,
                RotationStrategy.EMERGENCY,
                1,
                3,
                null,
                3,
                actorId,
                "Provider KMS key access denied",
                "AUTOMATED",
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now()
        );

        when(rotationService.triggerRotation(eq(workspaceId), eq(matchedSecretId), any(TriggerRotationRequest.class), eq(actorId), isNull()))
                .thenReturn(rotJob);

        FindingRemediationJob job = remediationService.remediateFinding(
                workspaceId, findingId, RemediationAction.ROTATE_SECRET, "Emergency rotation attempt", actorId
        );

        assertNotNull(job);
        assertEquals(RemediationStatus.ROTATION_FAILED, job.getStatus());
        assertEquals("Provider KMS key access denied", job.getErrorMessage());
        // Finding must remain CONFIRMED/DETECTED and NOT resolved!
        assertEquals(RepoFindingStatus.CONFIRMED, finding.getStatus());
        assertEquals(RemediationStatus.ROTATION_FAILED, finding.getRemediationStatus());

        verify(auditService).record(eq(workspaceId), eq(actorId), eq(AuditAction.ROTATION_FAILED), eq("SECRET"), eq(matchedSecretId.toString()), anyMap());
    }

    @Test
    @DisplayName("Action A: Unmanaged Credential Rotation provides Actionable Manual Playbook")
    void testUnmanagedSecretRotationProvidesManualPlaybook() {
        SecretFinding finding = createFinding(false);
        when(findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)).thenReturn(Optional.of(finding));
        when(remediationJobRepository.existsByFindingIdAndStatusIn(eq(findingId), anyList())).thenReturn(false);
        when(remediationJobRepository.save(any(FindingRemediationJob.class))).thenAnswer(inv -> {
            FindingRemediationJob j = inv.getArgument(0);
            if (j.getId() == null) j.setId(UUID.randomUUID());
            return j;
        });

        FindingRemediationJob job = remediationService.remediateFinding(
                workspaceId, findingId, RemediationAction.ROTATE_SECRET, "Attempting rotation on unmanaged key", actorId
        );

        assertNotNull(job);
        assertEquals(RemediationStatus.MANUAL_ACTION_REQUIRED, job.getStatus());
        assertTrue(job.getErrorMessage().contains("Credential is not managed in SecretVault"));
        assertTrue(job.getDetailsJson().contains("aws iam create-access-key"));
        assertEquals(RepoFindingStatus.CONFIRMED, finding.getStatus());
        assertEquals(RemediationStatus.MANUAL_ACTION_REQUIRED, finding.getRemediationStatus());

        verify(rotationService, never()).triggerRotation(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Action B: Revoke Secret and Mark Compromised for Managed Secret")
    void testSuccessfulRevocationForManagedSecret() {
        SecretFinding finding = createFinding(true);
        when(findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)).thenReturn(Optional.of(finding));
        when(remediationJobRepository.existsByFindingIdAndStatusIn(eq(findingId), anyList())).thenReturn(false);
        when(remediationJobRepository.save(any(FindingRemediationJob.class))).thenAnswer(inv -> {
            FindingRemediationJob j = inv.getArgument(0);
            if (j.getId() == null) j.setId(UUID.randomUUID());
            return j;
        });

        FindingRemediationJob job = remediationService.remediateFinding(
                workspaceId, findingId, RemediationAction.REVOKE_SECRET, "Token confirmed leaked in public repo", actorId
        );

        assertNotNull(job);
        assertEquals(RemediationStatus.REVOCATION_SUCCEEDED, job.getStatus());
        assertEquals(RepoFindingStatus.RESOLVED, finding.getStatus());
        assertEquals(RemediationStatus.REVOCATION_SUCCEEDED, finding.getRemediationStatus());

        verify(rotationService).markCompromised(eq(workspaceId), eq(matchedSecretId), any(MarkCompromisedRequest.class), eq(actorId));
        verify(auditService).record(eq(workspaceId), eq(actorId), eq(AuditAction.SECRET_MARKED_COMPROMISED), eq("SECRET_FINDING"), eq(findingId.toString()), anyMap());
        verify(auditService).record(eq(workspaceId), eq(actorId), eq(AuditAction.SECRET_REVOKED), eq("SECRET"), eq(matchedSecretId.toString()), anyMap());
    }

    @Test
    @DisplayName("Action B: Unmanaged Token Revocation Distinguishes MANUAL_ACTION_REQUIRED")
    void testUnmanagedSecretRevocationDistinguishesManualActionRequired() {
        SecretFinding finding = createFinding(false);
        when(findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)).thenReturn(Optional.of(finding));
        when(remediationJobRepository.existsByFindingIdAndStatusIn(eq(findingId), anyList())).thenReturn(false);
        when(remediationJobRepository.save(any(FindingRemediationJob.class))).thenAnswer(inv -> {
            FindingRemediationJob j = inv.getArgument(0);
            if (j.getId() == null) j.setId(UUID.randomUUID());
            return j;
        });

        FindingRemediationJob job = remediationService.remediateFinding(
                workspaceId, findingId, RemediationAction.REVOKE_SECRET, "Unmanaged external key leaked", actorId
        );

        assertNotNull(job);
        assertEquals(RemediationStatus.MANUAL_ACTION_REQUIRED, job.getStatus());
        assertEquals(RepoFindingStatus.COMPROMISED, finding.getStatus());
        assertEquals(RemediationStatus.MANUAL_ACTION_REQUIRED, finding.getRemediationStatus());

        verify(auditService).record(eq(workspaceId), eq(actorId), eq(AuditAction.SECRET_MARKED_COMPROMISED), eq("SECRET_FINDING"), eq(findingId.toString()), anyMap());
    }

    @Test
    @DisplayName("Action C: False Positive Allowlisting with Narrow Scope, Reason Enforcement, and Subsequent Scan Revocation")
    void testFalsePositiveAllowlistingNarrowScopeAndSuppression() {
        SecretFinding finding = createFinding(false);
        when(findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)).thenReturn(Optional.of(finding));
        when(findingRepository.findByWorkspaceIdAndFingerprint(workspaceId, finding.getFingerprint()))
                .thenReturn(List.of(finding));
        when(remediationJobRepository.existsByFindingIdAndStatusIn(eq(findingId), anyList())).thenReturn(false);
        when(remediationJobRepository.save(any(FindingRemediationJob.class))).thenAnswer(inv -> {
            FindingRemediationJob j = inv.getArgument(0);
            if (j.getId() == null) j.setId(UUID.randomUUID());
            return j;
        });
        when(allowlistRepository.save(any(FindingAllowlist.class))).thenAnswer(inv -> {
            FindingAllowlist al = inv.getArgument(0);
            if (al.getId() == null) al.setId(UUID.randomUUID());
            return al;
        });

        RemediateFindingRequest request = new RemediateFindingRequest(
                RemediationAction.MARK_FALSE_POSITIVE,
                "Synthetic non-functional unit test fixture in mock directory",
                "src/config/aws.js",
                Instant.now().plusSeconds(86400 * 90),
                null
        );

        FindingRemediationJob job = remediationService.remediateFinding(workspaceId, findingId, request, actorId);

        assertNotNull(job);
        assertEquals(RemediationStatus.COMPLETED, job.getStatus());
        assertEquals(RepoFindingStatus.FALSE_POSITIVE, finding.getStatus());
        assertEquals(RemediationStatus.RESOLVED, finding.getRemediationStatus());

        // Verify narrow allowlist scope created
        verify(allowlistRepository).save(argThat(al ->
                al.getWorkspaceId().equals(workspaceId)
                        && al.getFingerprint().equals(finding.getFingerprint())
                        && al.getDetectorType().equals("AWS Access Key")
                        && al.getFilePattern().equals("src/config/aws.js")
                        && al.isActive()
        ));

        // Test Revocation of Allowlist Entry
        UUID allowlistId = UUID.randomUUID();
        FindingAllowlist activeAllowlist = new FindingAllowlist(
                workspaceId, repositoryId, finding.getFingerprint(), "AWS Access Key", "src/config/aws.js", "Approved rule", actorId, null
        );
        activeAllowlist.setId(allowlistId);

        when(allowlistRepository.findByIdAndWorkspaceId(allowlistId, workspaceId)).thenReturn(Optional.of(activeAllowlist));

        FindingAllowlist revoked = secretFindingService.revokeAllowlist(workspaceId, allowlistId, "Audit rule expired", actorId);
        assertFalse(revoked.isActive());
        assertEquals(RepoFindingStatus.REOPENED, finding.getStatus());
        assertEquals(RemediationStatus.PENDING, finding.getRemediationStatus());

        verify(auditService).record(eq(workspaceId), eq(actorId), eq(AuditAction.ALLOWLIST_REVOKED), eq("FINDING_ALLOWLIST"), eq(allowlistId.toString()), anyMap());
    }

    @Test
    @DisplayName("Action C: Allowlist and Suppression Rejects Short or Meaningless Reasons")
    void testFalsePositiveRequiresMeaningfulReason() {
        SecretFinding finding = createFinding(false);
        when(findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)).thenReturn(Optional.of(finding));
        when(remediationJobRepository.existsByFindingIdAndStatusIn(eq(findingId), anyList())).thenReturn(false);

        // Empty reason
        assertThrows(ApiException.class, () ->
                remediationService.remediateFinding(workspaceId, findingId, RemediationAction.MARK_FALSE_POSITIVE, "", actorId)
        );

        // Short reason (<10 chars)
        assertThrows(ApiException.class, () ->
                remediationService.remediateFinding(workspaceId, findingId, RemediationAction.MARK_FALSE_POSITIVE, "test", actorId)
        );

        // Meaningless filler
        assertThrows(ApiException.class, () ->
                remediationService.remediateFinding(workspaceId, findingId, RemediationAction.MARK_FALSE_POSITIVE, "false positive", actorId)
        );
    }

    @Test
    @DisplayName("Safety Guard: Duplicate / Concurrent Remediation Requests are Blocked")
    void testDuplicateRemediationRequestPrevented() {
        SecretFinding finding = createFinding(true);
        when(findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)).thenReturn(Optional.of(finding));
        when(remediationJobRepository.existsByFindingIdAndStatusIn(eq(findingId), anyList())).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () ->
                remediationService.remediateFinding(workspaceId, findingId, RemediationAction.ROTATE_SECRET, "Valid reason with enough length", actorId)
        );

        assertEquals(409, ex.getStatus().value());
        assertTrue(ex.getMessage().contains("already in progress"));
    }

    @Test
    @DisplayName("Security Guard: Cross-Workspace IDOR and Unauthorized Actions are Rejected")
    void testUnauthorizedRemediationAndCrossWorkspaceIdor() {
        UUID otherWorkspaceId = UUID.randomUUID();

        // 1. Cross-workspace finding lookup
        when(findingRepository.findByIdAndWorkspaceId(findingId, otherWorkspaceId)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () ->
                remediationService.remediateFinding(otherWorkspaceId, findingId, RemediationAction.ROTATE_SECRET, "Valid notes here", actorId)
        );

        // 2. Permission check
        doThrow(ApiException.forbidden("Access denied"))
                .when(accessService).requireWorkspacePermission(actorId, workspaceId, AccessPermission.REPOSITORY_REMEDIATE);

        assertThrows(ApiException.class, () ->
                remediationService.remediateFinding(workspaceId, findingId, RemediationAction.ROTATE_SECRET, "Valid notes here", actorId)
        );
    }

    @Test
    @DisplayName("Target Info Inspection: Returns Accurate SecretVault Ownership & Playbook")
    void testRemediationTargetInfoInspection() {
        SecretFinding finding = createFinding(true);
        Secret secret = createManagedSecret();
        Environment env = new Environment();
        env.setId(environmentId);
        env.setName("production");
        env.setProjectId(projectId);

        Project project = new Project();
        project.setId(projectId);
        project.setName("Core Payments");

        when(findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)).thenReturn(Optional.of(finding));
        when(secretRepository.findById(matchedSecretId)).thenReturn(Optional.of(secret));
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(env));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        RemediationTargetInfo targetInfo = remediationService.getRemediationTargetInfo(workspaceId, findingId, actorId);

        assertNotNull(targetInfo);
        assertTrue(targetInfo.isManagedInSecretVault());
        assertEquals("PROD_AWS_ACCESS_KEY", targetInfo.matchedSecretName());
        assertEquals("production", targetInfo.matchedEnvironmentName());
        assertEquals("Core Payments", targetInfo.matchedProjectName());
        assertEquals(3, targetInfo.currentVersionNumber());
        assertTrue(targetInfo.rotationSupported());
        assertTrue(targetInfo.revocationSupported());
        assertTrue(targetInfo.impactWarning().contains("Version 4"));
        assertFalse(targetInfo.maskedEvidence().contains("AKIAIOSFODNN7EXAMPLE"));
    }

    @Test
    @DisplayName("Zero Plaintext Invariant: Masked Value and SHA-256 Fingerprint Enforced Across Remediation")
    void testZeroPlaintextInvariantInRemediation() {
        SecretFinding finding = createFinding(true);
        assertEquals("AK************12", finding.getMaskedValue());
        assertEquals(64, finding.getFingerprint().length());
        assertFalse(finding.getMaskedValue().contains("AKIAIOSFODNN7EXAMPLE"));

        when(findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)).thenReturn(Optional.of(finding));
        when(remediationJobRepository.existsByFindingIdAndStatusIn(eq(findingId), anyList())).thenReturn(false);
        when(remediationJobRepository.save(any(FindingRemediationJob.class))).thenAnswer(inv -> inv.getArgument(0));

        FindingRemediationJob job = remediationService.remediateFinding(
                workspaceId, findingId, RemediationAction.IGNORE, "Valid test file ignored by secops", actorId
        );

        assertNotNull(job);
        assertNull(job.getErrorMessage());
        assertFalse(job.getNotes().contains("AKIAIOSFODNN7EXAMPLE"));
    }
}
