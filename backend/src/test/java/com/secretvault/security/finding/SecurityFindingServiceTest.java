package com.secretvault.security.finding;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.security.event.service.SecurityEventService;
import com.secretvault.security.finding.dto.AssignFindingRequest;
import com.secretvault.security.finding.dto.SecurityFindingDraft;
import com.secretvault.security.finding.dto.SecurityFindingResponse;
import com.secretvault.security.finding.dto.UpdateFindingStatusRequest;
import com.secretvault.security.finding.entity.SecurityFinding;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.model.FindingConfidence;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.security.finding.model.FindingStatus;
import com.secretvault.security.finding.repository.SecurityFindingRepository;
import com.secretvault.security.finding.service.SecurityFindingService;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityFindingServiceTest {

    @Mock
    private SecurityFindingRepository findingRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private EffectiveAccessService effectiveAccessService;

    @Mock
    private SecurityEventService eventService;

    private SecurityFindingService findingService;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        findingService = new SecurityFindingService(
                findingRepository,
                workspaceRepository,
                effectiveAccessService,
                eventService
        );
    }

    @Test
    @DisplayName("upsertFinding creates new finding when not previously existing")
    void upsertFinding_createsNewFinding() {
        SecurityFindingDraft draft = new SecurityFindingDraft(
                workspaceId,
                projectId,
                null,
                FindingCategory.EXCESSIVE_PRIVILEGE,
                FindingSeverity.HIGH,
                FindingConfidence.HIGH,
                "Excessive workspace admin roles",
                "Multiple workspace admins detected",
                "Demote unneeded administrators",
                "admin_count",
                Map.of("admins", 5)
        );

        when(findingRepository.findByWorkspaceIdAndFingerprint(eq(workspaceId), any())).thenReturn(Optional.empty());
        when(findingRepository.save(any(SecurityFinding.class))).thenAnswer(inv -> inv.getArgument(0));

        SecurityFinding result = findingService.upsertFinding(draft);

        assertThat(result).isNotNull();
        assertThat(result.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(result.getCategory()).isEqualTo(FindingCategory.EXCESSIVE_PRIVILEGE);
        assertThat(result.getSeverity()).isEqualTo(FindingSeverity.HIGH);
        assertThat(result.getStatus()).isEqualTo(FindingStatus.OPEN);
        assertThat(result.getOccurrenceCount()).isEqualTo(1);

        verify(eventService).recordEvent(
                eq(workspaceId), eq(projectId), isNull(), isNull(),
                any(), any(), any(), eq("ANALYSIS_ENGINE"), isNull(), isNull(), isNull(), any()
        );
    }

    @Test
    @DisplayName("upsertFinding updates occurrence count and timestamp on duplicate finding")
    void upsertFinding_deduplicatesAndIncrementsOccurrence() {
        SecurityFindingDraft draft = new SecurityFindingDraft(
                workspaceId,
                projectId,
                null,
                FindingCategory.EXCESSIVE_PRIVILEGE,
                FindingSeverity.HIGH,
                FindingConfidence.HIGH,
                "Excessive workspace admin roles",
                "Multiple workspace admins detected",
                "Demote unneeded administrators",
                "admin_count",
                Map.of("admins", 5)
        );

        SecurityFinding existing = new SecurityFinding(
                workspaceId, projectId, null,
                FindingCategory.EXCESSIVE_PRIVILEGE, FindingSeverity.HIGH, FindingConfidence.HIGH,
                "Excessive workspace admin roles", "Multiple workspace admins detected",
                "Demote unneeded administrators", "{}", draft.generateFingerprint()
        );
        existing.setStatus(FindingStatus.OPEN);

        when(findingRepository.findByWorkspaceIdAndFingerprint(eq(workspaceId), any())).thenReturn(Optional.of(existing));
        when(findingRepository.save(any(SecurityFinding.class))).thenAnswer(inv -> inv.getArgument(0));

        SecurityFinding result = findingService.upsertFinding(draft);

        assertThat(result.getOccurrenceCount()).isEqualTo(2);
        assertThat(result.getStatus()).isEqualTo(FindingStatus.OPEN);
        verify(findingRepository).save(existing);
    }

    @Test
    @DisplayName("upsertFinding reopens previously resolved finding on active regression")
    void upsertFinding_reopensResolvedFindingOnRegression() {
        SecurityFindingDraft draft = new SecurityFindingDraft(
                workspaceId,
                projectId,
                null,
                FindingCategory.DORMANT_PRIVILEGED_ACCESS,
                FindingSeverity.MEDIUM,
                FindingConfidence.HIGH,
                "Dormant privileged access",
                "Dormant user found",
                "Revoke access",
                "dormant_user_1",
                Map.of("days", 120)
        );

        SecurityFinding existing = new SecurityFinding(
                workspaceId, projectId, null,
                FindingCategory.DORMANT_PRIVILEGED_ACCESS, FindingSeverity.MEDIUM, FindingConfidence.HIGH,
                "Dormant privileged access", "Dormant user found",
                "Revoke access", "{}", draft.generateFingerprint()
        );
        existing.setStatus(FindingStatus.RESOLVED);
        existing.setResolvedAt(Instant.now().minusSeconds(3600));
        existing.setResolutionReason("User removed");

        when(findingRepository.findByWorkspaceIdAndFingerprint(eq(workspaceId), any())).thenReturn(Optional.of(existing));
        when(findingRepository.save(any(SecurityFinding.class))).thenAnswer(inv -> inv.getArgument(0));

        SecurityFinding result = findingService.upsertFinding(draft);

        assertThat(result.getStatus()).isEqualTo(FindingStatus.OPEN);
        assertThat(result.getResolvedAt()).isNull();
        assertThat(result.getResolutionReason()).isNull();
    }

    @Test
    @DisplayName("getFindings enforces SECURITY_VIEW permission and returns page")
    void getFindings_enforcesPermissionAndReturnsPage() {
        Pageable pageable = PageRequest.of(0, 20);
        when(findingRepository.searchFindings(eq(workspaceId), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<SecurityFindingResponse> result = findingService.getFindings(
                workspaceId, null, null, null, null, null, null, userId, pageable
        );

        assertThat(result).isNotNull();
        verify(effectiveAccessService).checkPermission(
                eq(workspaceId), isNull(), isNull(), isNull(),
                eq(AccessPermission.SECURITY_VIEW), eq(userId)
        );
    }

    @Test
    @DisplayName("updateStatus transitions finding from OPEN to ACKNOWLEDGED to RESOLVED")
    void updateStatus_validLifecycleTransitions() {
        UUID findingId = UUID.randomUUID();
        SecurityFinding finding = new SecurityFinding(
                workspaceId, projectId, null,
                FindingCategory.EXCESSIVE_PRIVILEGE, FindingSeverity.HIGH, FindingConfidence.HIGH,
                "Excessive roles", "Desc", "Remediation", "{}", "fp123"
        );
        finding.setStatus(FindingStatus.OPEN);

        when(findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)).thenReturn(Optional.of(finding));
        when(findingRepository.save(any(SecurityFinding.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. OPEN -> ACKNOWLEDGED
        SecurityFindingResponse respAck = findingService.updateStatus(
                workspaceId, findingId, new UpdateFindingStatusRequest(FindingStatus.ACKNOWLEDGED, null), userId
        );
        assertThat(respAck.status()).isEqualTo(FindingStatus.ACKNOWLEDGED);
        assertThat(finding.getAcknowledgedAt()).isNotNull();

        // 2. ACKNOWLEDGED -> RESOLVED
        SecurityFindingResponse respRes = findingService.updateStatus(
                workspaceId, findingId, new UpdateFindingStatusRequest(FindingStatus.RESOLVED, "Fixed role assignment"), userId
        );
        assertThat(respRes.status()).isEqualTo(FindingStatus.RESOLVED);
        assertThat(finding.getResolvedAt()).isNotNull();
        assertThat(finding.getResolutionReason()).isEqualTo("Fixed role assignment");
        assertThat(finding.getResolvedByUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("updateStatus rejects invalid transition from RESOLVED to ACKNOWLEDGED")
    void updateStatus_rejectsInvalidTransition() {
        UUID findingId = UUID.randomUUID();
        SecurityFinding finding = new SecurityFinding(
                workspaceId, projectId, null,
                FindingCategory.EXCESSIVE_PRIVILEGE, FindingSeverity.HIGH, FindingConfidence.HIGH,
                "Excessive roles", "Desc", "Remediation", "{}", "fp123"
        );
        finding.setStatus(FindingStatus.RESOLVED);

        when(findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)).thenReturn(Optional.of(finding));

        assertThatThrownBy(() -> findingService.updateStatus(
                workspaceId, findingId, new UpdateFindingStatusRequest(FindingStatus.ACKNOWLEDGED, null), userId
        )).isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid finding status transition");
    }

    @Test
    @DisplayName("assignFinding updates assigneeUserId and requires SECURITY_MANAGE")
    void assignFinding_updatesAssignee() {
        UUID findingId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        SecurityFinding finding = new SecurityFinding(
                workspaceId, projectId, null,
                FindingCategory.EXCESSIVE_PRIVILEGE, FindingSeverity.HIGH, FindingConfidence.HIGH,
                "Excessive roles", "Desc", "Remediation", "{}", "fp123"
        );

        when(findingRepository.findByIdAndWorkspaceId(findingId, workspaceId)).thenReturn(Optional.of(finding));
        when(findingRepository.save(any(SecurityFinding.class))).thenAnswer(inv -> inv.getArgument(0));

        SecurityFindingResponse response = findingService.assignFinding(
                workspaceId, findingId, new AssignFindingRequest(assigneeId), userId
        );

        assertThat(response.assigneeUserId()).isEqualTo(assigneeId);
        verify(effectiveAccessService).checkPermission(
                eq(workspaceId), isNull(), isNull(), isNull(),
                eq(AccessPermission.SECURITY_MANAGE), eq(userId)
        );
    }
}
