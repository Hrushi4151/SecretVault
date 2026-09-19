package com.secretvault.access.review.service;

import com.secretvault.access.grant.repository.AccessGrantRepository;
import com.secretvault.access.jit.repository.JitAccessRequestRepository;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.model.AccessSourceType;
import com.secretvault.access.review.dto.AccessReviewCampaignResponse;
import com.secretvault.access.review.dto.AccessReviewItemResponse;
import com.secretvault.access.review.dto.CreateCampaignRequest;
import com.secretvault.access.review.dto.DecideReviewItemRequest;
import com.secretvault.access.review.entity.AccessReviewCampaign;
import com.secretvault.access.review.entity.AccessReviewItem;
import com.secretvault.access.review.entity.CampaignStatus;
import com.secretvault.access.review.entity.ReviewDecision;
import com.secretvault.access.review.repository.AccessReviewCampaignRepository;
import com.secretvault.access.review.repository.AccessReviewItemRepository;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.environment.access.repository.EnvironmentAccessRepository;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.project.access.repository.ProjectAccessRepository;
import com.secretvault.project.repository.ProjectRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private AccessReviewService reviewService;

    private UUID workspaceId;
    private UUID actorUserId;
    private UUID targetUserId;
    private Workspace workspace;
    private User user;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        actorUserId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();

        workspace = new Workspace(UUID.randomUUID(), "Test Workspace", "test-ws", true);
        workspace.setId(workspaceId);

        user = new User("reviewer@example.com", "hash", "Reviewer");
    }

    @Test
    @DisplayName("Create Campaign: Snapshots effective permissions across workspace")
    void testCreateCampaignSuccess() {
        CreateCampaignRequest request = new CreateCampaignRequest(
                "Q3 SOC2 Access Review", "Periodic certification", AccessScope.WORKSPACE, null, null,
                Instant.now().plus(14, ChronoUnit.DAYS)
        );

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(membershipRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of(
                new WorkspaceMembership(workspaceId, targetUserId, WorkspaceRole.DEVELOPER)
        ));
        User targetUser = new User("dev@example.com", "hash", "Dev User");
        targetUser.setId(targetUserId);
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(projectRepository.findByWorkspaceId(workspaceId)).thenReturn(Collections.emptyList());
        when(accessGrantRepository.findByWorkspaceId(workspaceId)).thenReturn(Collections.emptyList());
        when(jitRepository.findByWorkspaceIdAndStatus(workspaceId, com.secretvault.access.jit.entity.JitStatus.APPROVED)).thenReturn(Collections.emptyList());

        AccessReviewCampaign savedCampaign = new AccessReviewCampaign(
                workspaceId, request.name(), request.description(), request.scopeType(), null, null, actorUserId, request.dueDate()
        );
        savedCampaign.setId(UUID.randomUUID());
        when(campaignRepository.save(any(AccessReviewCampaign.class))).thenReturn(savedCampaign);
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(user));

        AccessReviewCampaignResponse response = reviewService.createCampaign(workspaceId, request, actorUserId);

        assertNotNull(response);
        assertEquals("Q3 SOC2 Access Review", response.name());
        verify(itemRepository).saveAll(anyList());
        verify(effectiveAccessService).checkPermission(workspaceId, null, null, null, AccessPermission.ACCESS_REVIEW_MANAGE, actorUserId);
    }

    @Test
    @DisplayName("Decide Review Item: KEEP decision records certification")
    void testDecideReviewItemKeep() {
        UUID campaignId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        AccessReviewCampaign campaign = new AccessReviewCampaign(
                workspaceId, "Campaign", "desc", AccessScope.WORKSPACE, null, null, actorUserId, Instant.now().plus(7, ChronoUnit.DAYS)
        );
        campaign.setId(campaignId);

        AccessReviewItem item = new AccessReviewItem(
                campaignId, targetUserId, "dev@example.com", "Dev User", AccessScope.WORKSPACE, "Workspace", workspaceId,
                AccessSourceType.WORKSPACE_ROLE, UUID.randomUUID(), "Workspace Role: DEVELOPER"
        );
        item.setId(itemId);

        when(campaignRepository.findByIdAndWorkspaceId(campaignId, workspaceId)).thenReturn(Optional.of(campaign));
        when(itemRepository.findByIdAndCampaignIdForUpdate(itemId, campaignId)).thenReturn(Optional.of(item));
        when(itemRepository.countByCampaignIdAndDecisionNot(campaignId, ReviewDecision.PENDING)).thenReturn(1L);
        when(userRepository.findById(actorUserId)).thenReturn(Optional.of(user));

        AccessReviewItemResponse response = reviewService.decideItem(
                workspaceId, campaignId, itemId, new DecideReviewItemRequest(ReviewDecision.KEEP, "Certified legitimate"), actorUserId
        );

        assertNotNull(response);
        assertEquals(ReviewDecision.KEEP, response.decision());
        assertEquals("Certified legitimate", response.decisionReason());
    }
}
