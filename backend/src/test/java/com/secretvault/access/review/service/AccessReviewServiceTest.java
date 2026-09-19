package com.secretvault.access.review.service;

import com.secretvault.access.grant.entity.AccessGrant;
import com.secretvault.access.grant.repository.AccessGrantRepository;
import com.secretvault.access.jit.entity.JitAccessRequest;
import com.secretvault.access.jit.entity.JitStatus;
import com.secretvault.access.jit.repository.JitAccessRequestRepository;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.model.AccessSourceType;
import com.secretvault.access.review.dto.AccessReviewCampaignResponse;
import com.secretvault.access.review.dto.AccessReviewItemResponse;
import com.secretvault.access.review.dto.CampaignAttestationReportResponse;
import com.secretvault.access.review.dto.CreateCampaignRequest;
import com.secretvault.access.review.dto.DecideReviewItemRequest;
import com.secretvault.access.review.entity.AccessReviewCampaign;
import com.secretvault.access.review.entity.AccessReviewItem;
import com.secretvault.access.review.entity.CampaignStatus;
import com.secretvault.access.review.entity.ReviewDecision;
import com.secretvault.access.review.repository.AccessReviewCampaignRepository;
import com.secretvault.access.review.repository.AccessReviewItemRepository;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.common.dto.PageResponse;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.access.entity.EnvironmentAccess;
import com.secretvault.environment.access.repository.EnvironmentAccessRepository;
import com.secretvault.environment.entity.EnvType;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.access.entity.ProjectAccess;
import com.secretvault.project.access.repository.ProjectAccessRepository;
import com.secretvault.project.entity.Project;
import com.secretvault.project.repository.ProjectRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessReviewServiceTest {

    @Mock
    private AccessReviewCampaignRepository campaignRepository;

    @Mock
    private AccessReviewItemRepository itemRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMembershipRepository membershipRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private SecretRepository secretRepository;

    @Mock
    private ProjectAccessRepository projectAccessRepository;

    @Mock
    private EnvironmentAccessRepository environmentAccessRepository;

    @Mock
    private AccessGrantRepository accessGrantRepository;

    @Mock
    private JitAccessRequestRepository jitRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private EffectiveAccessService effectiveAccessService;

    private Clock fixedClock;
    private Instant fixedInstant;

    private AccessReviewService reviewService;

    private UUID workspaceId;
    private UUID actorUserId;
    private UUID targetUserId;
    private UUID projectId;
    private UUID environmentId;

    private Workspace workspace;
    private User actorUser;
    private User targetUser;
    private Project project;
    private Environment environment;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        actorUserId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        environmentId = UUID.randomUUID();

        fixedInstant = Instant.parse("2026-09-19T18:00:00Z");
        fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

        reviewService = new AccessReviewService(
                campaignRepository,
                itemRepository,
                workspaceRepository,
                membershipRepository,
                projectRepository,
                environmentRepository,
                secretRepository,
                projectAccessRepository,
                environmentAccessRepository,
                accessGrantRepository,
                jitRepository,
                userRepository,
                auditService,
                effectiveAccessService,
                fixedClock
        );

        workspace = new Workspace(UUID.randomUUID(), "Engineering Org", "engineering-org", true);
        workspace.setId(workspaceId);

        actorUser = new User("admin@example.com", "hash", "Admin Reviewer");
        actorUser.setId(actorUserId);

        targetUser = new User("developer@example.com", "hash", "Developer User");
        targetUser.setId(targetUserId);

        project = new Project(workspaceId, "Billing Service", "billing-service", "desc", actorUserId);
        project.setId(projectId);

        environment = new Environment(projectId, "Production", "prod", EnvType.PRODUCTION, "desc", true, actorUserId);
        environment.setId(environmentId);
    }

    @Test
    @DisplayName("Create Campaign: Successfully snapshots effective access baseline")
    void testCreateCampaignSuccess() {
        CreateCampaignRequest request = new CreateCampaignRequest(
                "Q4 Production Audit", "Quarterly certification", AccessScope.WORKSPACE, null, null, fixedInstant.plus(Duration.ofDays(14))
        );

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(membershipRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(
                new WorkspaceMembership(workspaceId, targetUserId, WorkspaceRole.DEVELOPER)
        ));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(projectRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(project));
        when(environmentRepository.findByProjectId(projectId)).thenReturn(List.of(environment));
        when(accessGrantRepository.findByWorkspaceId(workspaceId)).thenReturn(Collections.emptyList());
        when(jitRepository.findByWorkspaceIdAndStatus(workspaceId, JitStatus.APPROVED)).thenReturn(Collections.emptyList());

        AccessReviewCampaign savedCampaign = new AccessReviewCampaign(
                workspaceId, "Q4 Production Audit", "desc", AccessScope.WORKSPACE, null, null, actorUserId, request.dueDate()
        );
        savedCampaign.setId(UUID.randomUUID());
        when(campaignRepository.save(any(AccessReviewCampaign.class))).thenReturn(savedCampaign);
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));

        AccessReviewCampaignResponse response = reviewService.createCampaign(workspaceId, request, actorUserId);

        assertNotNull(response);
        assertEquals(CampaignStatus.OPEN, response.status());
        verify(itemRepository).saveAll(any());
        verify(effectiveAccessService).checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_REVIEW_MANAGE, actorUserId);
        verify(auditService).logSuccess(eq(AuditAction.ACCESS_REVIEW_CAMPAIGN_CREATED), any(), any(), eq(actorUserId), eq(workspaceId), any());
    }

    @Test
    @DisplayName("Validation: Past due date rejected")
    void testCreateCampaignPastDueDateRejected() {
        CreateCampaignRequest request = new CreateCampaignRequest(
                "Invalid Due Date", "Past due date", AccessScope.WORKSPACE, null, null, fixedInstant.minus(Duration.ofDays(1))
        );

        ApiException ex = assertThrows(ApiException.class, () ->
                reviewService.createCampaign(workspaceId, request, actorUserId));

        assertEquals("BAD_REQUEST", ex.getCode());
        assertTrue(ex.getMessage().contains("due date must be in the future"));
    }

    @Test
    @DisplayName("Decide Item: Successfully certifies access (KEEP)")
    void testDecideItemKeepSuccess() {
        UUID campaignId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        AccessReviewCampaign campaign = new AccessReviewCampaign(
                workspaceId, "Review", "desc", AccessScope.WORKSPACE, null, null, actorUserId, fixedInstant.plus(Duration.ofDays(14))
        );
        campaign.setId(campaignId);
        campaign.setTotalItemsCount(1);

        AccessReviewItem item = new AccessReviewItem(
                campaignId, targetUserId, "developer@example.com", "Dev User", AccessScope.WORKSPACE, "Eng Workspace", workspaceId,
                AccessSourceType.WORKSPACE_ROLE, UUID.randomUUID(), "Workspace Role: DEVELOPER"
        );
        item.setId(itemId);

        when(campaignRepository.findByIdAndWorkspaceId(campaignId, workspaceId)).thenReturn(Optional.of(campaign));
        when(itemRepository.findByIdAndCampaignIdForUpdate(itemId, campaignId)).thenReturn(Optional.of(item));
        when(itemRepository.countByCampaignIdAndDecisionNot(campaignId, ReviewDecision.PENDING)).thenReturn(1L);
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));

        AccessReviewItemResponse response = reviewService.decideItem(
                workspaceId, campaignId, itemId, new DecideReviewItemRequest(ReviewDecision.KEEP, "Certified legitimate"), actorUserId
        );

        assertNotNull(response);
        assertEquals(ReviewDecision.KEEP, response.decision());
        assertEquals("Certified legitimate", response.decisionReason());
        assertEquals(CampaignStatus.COMPLETED, campaign.getStatus());
        verify(auditService).logSuccess(eq(AuditAction.ACCESS_REVIEW_ITEM_DECIDED), any(), eq(itemId), eq(actorUserId), eq(workspaceId), any());
    }

    @Test
    @DisplayName("Decide Item: Targeted revocation deletes exact granular grant")
    void testDecideItemRevokeTargetedGrant() {
        UUID campaignId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();

        AccessReviewCampaign campaign = new AccessReviewCampaign(
                workspaceId, "Review", "desc", AccessScope.WORKSPACE, null, null, actorUserId, fixedInstant.plus(Duration.ofDays(14))
        );
        campaign.setId(campaignId);
        campaign.setTotalItemsCount(1);

        AccessReviewItem item = new AccessReviewItem(
                campaignId, targetUserId, "developer@example.com", "Dev User", AccessScope.ENVIRONMENT, "Production", environmentId,
                AccessSourceType.GRANULAR_GRANT, grantId, "Granular: secret.reveal"
        );
        item.setId(itemId);

        AccessGrant grant = new AccessGrant(
                workspaceId, targetUserId, AccessScope.ENVIRONMENT, projectId, environmentId, null, AccessPermission.SECRET_REVEAL, actorUserId
        );
        grant.setId(grantId);

        when(campaignRepository.findByIdAndWorkspaceId(campaignId, workspaceId)).thenReturn(Optional.of(campaign));
        when(itemRepository.findByIdAndCampaignIdForUpdate(itemId, campaignId)).thenReturn(Optional.of(item));
        when(accessGrantRepository.findByIdAndWorkspaceId(grantId, workspaceId)).thenReturn(Optional.of(grant));
        when(itemRepository.countByCampaignIdAndDecisionNot(campaignId, ReviewDecision.PENDING)).thenReturn(1L);
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));

        AccessReviewItemResponse response = reviewService.decideItem(
                workspaceId, campaignId, itemId, new DecideReviewItemRequest(ReviewDecision.REVOKE, "Revoked excessive permission"), actorUserId
        );

        assertNotNull(response);
        assertEquals(ReviewDecision.REVOKE, response.decision());
        verify(accessGrantRepository).delete(grant);
    }

    @Test
    @DisplayName("Anti-Self-Review: Reviewer attempting to certify own high-privilege permission is blocked with 403")
    void testAntiSelfReviewViolation() {
        UUID campaignId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        AccessReviewCampaign campaign = new AccessReviewCampaign(
                workspaceId, "Review", "desc", AccessScope.WORKSPACE, null, null, actorUserId, fixedInstant.plus(Duration.ofDays(14))
        );
        campaign.setId(campaignId);

        AccessReviewItem item = new AccessReviewItem(
                campaignId, actorUserId, "admin@example.com", "Admin User", AccessScope.WORKSPACE, "Eng Workspace", workspaceId,
                AccessSourceType.WORKSPACE_ROLE, UUID.randomUUID(), "Workspace Role: ADMIN"
        );
        item.setId(itemId);

        when(campaignRepository.findByIdAndWorkspaceId(campaignId, workspaceId)).thenReturn(Optional.of(campaign));
        when(itemRepository.findByIdAndCampaignIdForUpdate(itemId, campaignId)).thenReturn(Optional.of(item));

        ApiException ex = assertThrows(ApiException.class, () ->
                reviewService.decideItem(workspaceId, campaignId, itemId, new DecideReviewItemRequest(ReviewDecision.KEEP, "Self certifying"), actorUserId));

        assertEquals("FORBIDDEN", ex.getCode());
        assertTrue(ex.getMessage().contains("Anti-Self-Review Violation"));
    }

    @Test
    @DisplayName("Attestation Ledger: Completing campaign generates immutable attestation report")
    void testCompleteCampaignAttestationReport() {
        UUID campaignId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        AccessReviewCampaign campaign = new AccessReviewCampaign(
                workspaceId, "SOC 2 Q4 Audit", "desc", AccessScope.WORKSPACE, null, null, actorUserId, fixedInstant.plus(Duration.ofDays(14))
        );
        campaign.setId(campaignId);

        AccessReviewItem item = new AccessReviewItem(
                campaignId, targetUserId, "developer@example.com", "Dev User", AccessScope.WORKSPACE, "Eng Workspace", workspaceId,
                AccessSourceType.WORKSPACE_ROLE, UUID.randomUUID(), "Workspace Role: DEVELOPER"
        );
        item.setId(itemId);
        item.setDecision(ReviewDecision.KEEP);
        item.setDecidedBy(actorUserId);

        when(campaignRepository.findByIdAndWorkspaceIdForUpdate(campaignId, workspaceId)).thenReturn(Optional.of(campaign));
        when(campaignRepository.findByIdAndWorkspaceId(campaignId, workspaceId)).thenReturn(Optional.of(campaign));
        when(membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, actorUserId)).thenReturn(true);
        when(itemRepository.findByCampaignId(campaignId)).thenReturn(List.of(item));
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(actorUser));

        CampaignAttestationReportResponse report = reviewService.completeCampaign(workspaceId, campaignId, actorUserId);

        assertNotNull(report);
        assertEquals(CampaignStatus.COMPLETED, report.status());
        assertEquals("SOC 2 Q4 Audit", report.campaignName());
        assertEquals(1, report.totalItemsReviewed());
        assertEquals(1, report.keptCount());
        assertEquals(0, report.revokedCount());
        assertEquals("admin@example.com", report.certifiedByEmail());
        verify(auditService).logSuccess(eq(AuditAction.ACCESS_REVIEW_CAMPAIGN_COMPLETED), any(), eq(campaignId), eq(actorUserId), eq(workspaceId), any());
    }
}
