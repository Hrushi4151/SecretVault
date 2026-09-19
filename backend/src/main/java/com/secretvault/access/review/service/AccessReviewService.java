package com.secretvault.access.review.service;

import com.secretvault.access.dto.EffectiveAccessExplanation;
import com.secretvault.access.grant.entity.AccessGrant;
import com.secretvault.access.grant.repository.AccessGrantRepository;
import com.secretvault.access.jit.entity.JitAccessRequest;
import com.secretvault.access.jit.entity.JitStatus;
import com.secretvault.access.jit.repository.JitAccessRequestRepository;
import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.model.AccessScope;
import com.secretvault.access.model.AccessSourceType;
import com.secretvault.access.review.dto.*;
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
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import com.secretvault.workspace.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enterprise service managing Access Review and Certification Campaigns.
 * Provides point-in-time privilege snapshotting, targeted remediation/revocation,
 * anti-self-review protection, and immutable attestation auditing.
 */
@Service
public class AccessReviewService {

    private static final Logger log = LoggerFactory.getLogger(AccessReviewService.class);

    private static final Set<String> ALLOWED_CAMPAIGN_SORT_FIELDS = Set.of("createdAt", "updatedAt", "status", "dueDate", "name");
    private static final Set<String> ALLOWED_ITEM_SORT_FIELDS = Set.of("createdAt", "decision", "resourceName", "userEmail");
    private static final int MAX_PAGE_SIZE = 100;

    private final AccessReviewCampaignRepository campaignRepository;
    private final AccessReviewItemRepository itemRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final SecretRepository secretRepository;
    private final ProjectAccessRepository projectAccessRepository;
    private final EnvironmentAccessRepository environmentAccessRepository;
    private final AccessGrantRepository accessGrantRepository;
    private final JitAccessRequestRepository jitRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final EffectiveAccessService effectiveAccessService;
    private final Clock clock;

    public AccessReviewService(
            AccessReviewCampaignRepository campaignRepository,
            AccessReviewItemRepository itemRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            SecretRepository secretRepository,
            ProjectAccessRepository projectAccessRepository,
            EnvironmentAccessRepository environmentAccessRepository,
            AccessGrantRepository accessGrantRepository,
            JitAccessRequestRepository jitRepository,
            UserRepository userRepository,
            AuditService auditService,
            EffectiveAccessService effectiveAccessService
    ) {
        this(campaignRepository, itemRepository, workspaceRepository, membershipRepository,
             projectRepository, environmentRepository, secretRepository, projectAccessRepository,
             environmentAccessRepository, accessGrantRepository, jitRepository, userRepository,
             auditService, effectiveAccessService, Clock.systemUTC());
    }

    @Autowired
    public AccessReviewService(
            AccessReviewCampaignRepository campaignRepository,
            AccessReviewItemRepository itemRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            SecretRepository secretRepository,
            ProjectAccessRepository projectAccessRepository,
            EnvironmentAccessRepository environmentAccessRepository,
            AccessGrantRepository accessGrantRepository,
            JitAccessRequestRepository jitRepository,
            UserRepository userRepository,
            AuditService auditService,
            EffectiveAccessService effectiveAccessService,
            Clock clock
    ) {
        this.campaignRepository = campaignRepository;
        this.itemRepository = itemRepository;
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.secretRepository = secretRepository;
        this.projectAccessRepository = projectAccessRepository;
        this.environmentAccessRepository = environmentAccessRepository;
        this.accessGrantRepository = accessGrantRepository;
        this.jitRepository = jitRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.effectiveAccessService = effectiveAccessService;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Transactional
    public AccessReviewCampaignResponse createCampaign(UUID workspaceId, CreateCampaignRequest request, UUID actorUserId) {
        // 1. Authorize Actor
        effectiveAccessService.checkPermission(
                workspaceId, request.projectId(), request.environmentId(), null,
                AccessPermission.ACCESS_REVIEW_MANAGE, actorUserId
        );

        // 2. Validate Due Date in Future
        if (request.dueDate() == null || !request.dueDate().isAfter(clock.instant())) {
            throw ApiException.badRequest("Campaign due date must be in the future");
        }

        // 3. Validate Target Scope Hierarchy
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> ApiException.notFound("Workspace not found"));

        if (request.scopeType() == AccessScope.PROJECT && request.projectId() != null) {
            projectRepository.findByIdAndWorkspaceId(request.projectId(), workspaceId)
                    .orElseThrow(() -> ApiException.badRequest("Project not found in this workspace"));
        } else if (request.scopeType() == AccessScope.ENVIRONMENT && request.environmentId() != null) {
            Environment env = environmentRepository.findById(request.environmentId())
                    .orElseThrow(() -> ApiException.badRequest("Environment not found"));
            projectRepository.findByIdAndWorkspaceId(env.getProjectId(), workspaceId)
                    .orElseThrow(() -> ApiException.badRequest("Environment does not belong to this workspace"));
        }

        // 4. Create Campaign Entity
        AccessReviewCampaign campaign = new AccessReviewCampaign(
                workspaceId,
                request.name().trim(),
                request.description() != null ? request.description().trim() : null,
                request.scopeType(),
                request.projectId(),
                request.environmentId(),
                actorUserId,
                request.dueDate()
        );
        AccessReviewCampaign savedCampaign = campaignRepository.save(campaign);

        // 5. Snapshot Effective Access Across Scope
        List<AccessReviewItem> snapshotItems = snapshotEffectiveAccess(workspace, savedCampaign);
        savedCampaign.setTotalItemsCount(snapshotItems.size());
        itemRepository.saveAll(snapshotItems);

        campaignRepository.save(savedCampaign);

        auditService.logSuccess(
                AuditAction.ACCESS_REVIEW_CAMPAIGN_CREATED,
                "ACCESS_REVIEW_CAMPAIGN",
                savedCampaign.getId(),
                actorUserId,
                workspaceId,
                "Created access review campaign [" + savedCampaign.getName() + "] with " + snapshotItems.size() + " review items snapshotted"
        );

        log.info("Access review campaign [{}] created by [{}] in workspace [{}] with [{}] snapshotted items",
                savedCampaign.getId(), actorUserId, workspaceId, snapshotItems.size());

        return mapToResponse(savedCampaign);
    }

    @Transactional
    public AccessReviewItemResponse decideItem(
            UUID workspaceId,
            UUID campaignId,
            UUID itemId,
            DecideReviewItemRequest request,
            UUID actorUserId
    ) {
        AccessReviewCampaign campaign = campaignRepository.findByIdAndWorkspaceId(campaignId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Access review campaign not found in this workspace"));

        if (campaign.getStatus() == CampaignStatus.COMPLETED || campaign.getStatus() == CampaignStatus.EXPIRED) {
            throw ApiException.badRequest("Campaign is already " + campaign.getStatus() + " and cannot be modified");
        }

        effectiveAccessService.checkPermission(
                workspaceId, campaign.getProjectId(), campaign.getEnvironmentId(), null,
                AccessPermission.ACCESS_REVIEW_MANAGE, actorUserId
        );

        AccessReviewItem item = itemRepository.findByIdAndCampaignIdForUpdate(itemId, campaignId)
                .orElseThrow(() -> ApiException.notFound("Access review item not found in this campaign"));

        // Anti-Self-Review Guard for high-privilege permissions
        if (item.getUserId().equals(actorUserId) && request.decision() == ReviewDecision.KEEP) {
            if (isHighPrivilege(item)) {
                throw ApiException.forbidden("Anti-Self-Review Violation: You cannot certify your own high-privilege access");
            }
        }

        item.setDecision(request.decision());
        item.setDecisionReason(request.decisionReason() != null ? request.decisionReason().trim() : null);
        item.setDecidedBy(actorUserId);
        item.setDecidedAt(clock.instant());

        // Targeted Revocation Execution
        if (request.decision() == ReviewDecision.REVOKE) {
            executeTargetedRevocation(workspaceId, item, actorUserId);
        }

        itemRepository.save(item);

        // Update Campaign Progress
        long decidedCount = itemRepository.countByCampaignIdAndDecisionNot(campaignId, ReviewDecision.PENDING);
        campaign.setDecidedItemsCount((int) decidedCount);
        if (decidedCount > 0 && campaign.getStatus() == CampaignStatus.OPEN) {
            campaign.setStatus(CampaignStatus.IN_PROGRESS);
        }
        if (decidedCount >= campaign.getTotalItemsCount() && campaign.getTotalItemsCount() > 0) {
            campaign.setStatus(CampaignStatus.COMPLETED);
            campaign.setCompletedAt(clock.instant());
        }
        campaignRepository.save(campaign);

        auditService.logSuccess(
                AuditAction.ACCESS_REVIEW_ITEM_DECIDED,
                "ACCESS_REVIEW_ITEM",
                item.getId(),
                actorUserId,
                workspaceId,
                "Recorded decision [" + request.decision() + "] for user [" + item.getUserId() + "] on resource [" + item.getResourceName() + "]"
        );

        String decidedByEmail = userRepository.findById(actorUserId).map(User::getEmail).orElse(null);
        return mapItemToResponse(item, decidedByEmail, workspaceId);
    }

    @Transactional
    public CampaignAttestationReportResponse completeCampaign(UUID workspaceId, UUID campaignId, UUID actorUserId) {
        AccessReviewCampaign campaign = campaignRepository.findByIdAndWorkspaceIdForUpdate(campaignId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Access review campaign not found in this workspace"));

        effectiveAccessService.checkPermission(
                workspaceId, campaign.getProjectId(), campaign.getEnvironmentId(), null,
                AccessPermission.ACCESS_REVIEW_MANAGE, actorUserId
        );

        campaign.setStatus(CampaignStatus.COMPLETED);
        campaign.setCompletedAt(clock.instant());
        campaignRepository.save(campaign);

        auditService.logSuccess(
                AuditAction.ACCESS_REVIEW_CAMPAIGN_COMPLETED,
                "ACCESS_REVIEW_CAMPAIGN",
                campaign.getId(),
                actorUserId,
                workspaceId,
                "Finalized access certification campaign [" + campaign.getName() + "]"
        );

        return getAttestationReport(workspaceId, campaignId, actorUserId);
    }

    @Transactional
    public void cancelCampaign(UUID workspaceId, UUID campaignId, UUID actorUserId) {
        AccessReviewCampaign campaign = campaignRepository.findByIdAndWorkspaceIdForUpdate(campaignId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Access review campaign not found in this workspace"));

        effectiveAccessService.checkPermission(
                workspaceId, campaign.getProjectId(), campaign.getEnvironmentId(), null,
                AccessPermission.ACCESS_REVIEW_MANAGE, actorUserId
        );

        if (campaign.getStatus() == CampaignStatus.COMPLETED) {
            throw ApiException.badRequest("Cannot cancel an already completed campaign");
        }

        campaign.setStatus(CampaignStatus.EXPIRED);
        campaignRepository.save(campaign);

        log.info("Access review campaign [{}] cancelled/expired by [{}]", campaignId, actorUserId);
    }

    @Transactional(readOnly = true)
    public PageResponse<AccessReviewCampaignResponse> listCampaignsPaginated(
            UUID workspaceId,
            CampaignStatus status,
            Pageable pageable,
            UUID actorUserId
    ) {
        if (!membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, actorUserId)) {
            throw ApiException.forbidden("You are not an active member of this workspace");
        }

        Pageable safePageable = sanitizeCampaignPageable(pageable);
        Page<AccessReviewCampaign> page = campaignRepository.findFilteredCampaigns(workspaceId, status, safePageable);

        List<AccessReviewCampaignResponse> content = page.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    @Transactional(readOnly = true)
    public List<AccessReviewCampaignResponse> listCampaigns(UUID workspaceId, UUID actorUserId) {
        if (!membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, actorUserId)) {
            throw ApiException.forbidden("You are not an active member of this workspace");
        }

        List<AccessReviewCampaign> campaigns = campaignRepository.findByWorkspaceId(workspaceId);
        return campaigns.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AccessReviewCampaignResponse getCampaign(UUID workspaceId, UUID campaignId, UUID actorUserId) {
        if (!membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, actorUserId)) {
            throw ApiException.forbidden("You are not an active member of this workspace");
        }

        AccessReviewCampaign campaign = campaignRepository.findByIdAndWorkspaceId(campaignId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Access review campaign not found in this workspace"));

        return mapToResponse(campaign);
    }

    @Transactional(readOnly = true)
    public PageResponse<AccessReviewItemResponse> listCampaignItemsPaginated(
            UUID workspaceId,
            UUID campaignId,
            ReviewDecision decision,
            UUID userId,
            Pageable pageable,
            UUID actorUserId
    ) {
        if (!membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, actorUserId)) {
            throw ApiException.forbidden("You are not an active member of this workspace");
        }

        campaignRepository.findByIdAndWorkspaceId(campaignId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Access review campaign not found in this workspace"));

        Pageable safePageable = sanitizeItemPageable(pageable);
        Page<AccessReviewItem> page = itemRepository.findFilteredItems(campaignId, decision, userId, safePageable);

        List<AccessReviewItemResponse> content = page.getContent().stream().map(item -> {
            String decidedByEmail = item.getDecidedBy() != null
                    ? userRepository.findById(item.getDecidedBy()).map(User::getEmail).orElse(null)
                    : null;
            return mapItemToResponse(item, decidedByEmail, workspaceId);
        }).collect(Collectors.toList());

        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    @Transactional(readOnly = true)
    public List<AccessReviewItemResponse> listCampaignItems(UUID workspaceId, UUID campaignId, UUID actorUserId) {
        if (!membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, actorUserId)) {
            throw ApiException.forbidden("You are not an active member of this workspace");
        }

        AccessReviewCampaign campaign = campaignRepository.findByIdAndWorkspaceId(campaignId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Access review campaign not found in this workspace"));

        List<AccessReviewItem> items = itemRepository.findByCampaignId(campaignId);
        return items.stream().map(item -> {
            String decidedByEmail = item.getDecidedBy() != null
                    ? userRepository.findById(item.getDecidedBy()).map(User::getEmail).orElse(null)
                    : null;
            return mapItemToResponse(item, decidedByEmail, workspaceId);
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CampaignAttestationReportResponse getAttestationReport(UUID workspaceId, UUID campaignId, UUID actorUserId) {
        if (!membershipRepository.existsByWorkspaceIdAndUserId(workspaceId, actorUserId)) {
            throw ApiException.forbidden("You are not an active member of this workspace");
        }

        AccessReviewCampaign campaign = campaignRepository.findByIdAndWorkspaceId(campaignId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Access review campaign not found in this workspace"));

        List<AccessReviewItem> items = itemRepository.findByCampaignId(campaignId);
        List<AccessReviewItemResponse> itemResponses = items.stream().map(item -> {
            String email = item.getDecidedBy() != null
                    ? userRepository.findById(item.getDecidedBy()).map(User::getEmail).orElse(null)
                    : null;
            return mapItemToResponse(item, email, workspaceId);
        }).collect(Collectors.toList());

        int kept = (int) items.stream().filter(i -> i.getDecision() == ReviewDecision.KEEP).count();
        int revoked = (int) items.stream().filter(i -> i.getDecision() == ReviewDecision.REVOKE).count();

        String certifierEmail = userRepository.findById(actorUserId).map(User::getEmail).orElse("unknown");

        return new CampaignAttestationReportResponse(
                campaign.getId(),
                workspaceId,
                campaign.getName(),
                campaign.getStatus(),
                campaign.getCompletedAt() != null ? campaign.getCompletedAt() : clock.instant(),
                certifierEmail,
                items.size(),
                kept,
                revoked,
                itemResponses
        );
    }

    private void executeTargetedRevocation(UUID workspaceId, AccessReviewItem item, UUID actorUserId) {
        log.info("Executing targeted revocation for review item [{}] with sourceType [{}] ref [{}]",
                item.getId(), item.getSourceType(), item.getSourceReferenceId());

        if (item.getSourceReferenceId() == null) {
            return;
        }

        switch (item.getSourceType()) {
            case GRANULAR_GRANT -> {
                accessGrantRepository.findByIdAndWorkspaceId(item.getSourceReferenceId(), workspaceId)
                        .ifPresent(accessGrantRepository::delete);
            }
            case ENVIRONMENT_ACCESS -> {
                environmentAccessRepository.findById(item.getSourceReferenceId())
                        .ifPresent(environmentAccessRepository::delete);
            }
            case PROJECT_ACCESS -> {
                projectAccessRepository.findById(item.getSourceReferenceId())
                        .ifPresent(projectAccessRepository::delete);
            }
            case JIT_GRANT -> {
                jitRepository.findByIdAndWorkspaceId(item.getSourceReferenceId(), workspaceId).ifPresent(req -> {
                    req.setStatus(JitStatus.REVOKED);
                    req.setRevokedAt(clock.instant());
                    jitRepository.save(req);
                });
            }
            case WORKSPACE_ROLE -> {
                log.warn("Workspace role revocation flagged in review item [{}]. Role downgrade required.", item.getId());
            }
        }
    }

    private List<AccessReviewItem> snapshotEffectiveAccess(Workspace workspace, AccessReviewCampaign campaign) {
        List<AccessReviewItem> items = new ArrayList<>();
        UUID wsId = workspace.getId();

        // 1. Snapshot Workspace Memberships
        List<WorkspaceMembership> members = membershipRepository.findByWorkspaceId(wsId);
        for (WorkspaceMembership m : members) {
            User user = userRepository.findById(m.getUserId()).orElse(null);
            if (user == null) continue;

            items.add(new AccessReviewItem(
                    campaign.getId(),
                    user.getId(),
                    user.getEmail(),
                    user.getFullName(),
                    AccessScope.WORKSPACE,
                    workspace.getName(),
                    wsId,
                    AccessSourceType.WORKSPACE_ROLE,
                    m.getId(),
                    "Workspace Role: " + m.getRole().name()
            ));
        }

        // 2. Snapshot Scoped Project Access
        List<Project> projects = projectRepository.findByWorkspaceId(wsId);
        for (Project p : projects) {
            if (campaign.getProjectId() != null && !p.getId().equals(campaign.getProjectId())) {
                continue;
            }
            List<ProjectAccess> projGrants = projectAccessRepository.findByProjectId(p.getId());
            for (ProjectAccess pa : projGrants) {
                User user = userRepository.findById(pa.getUserId()).orElse(null);
                if (user == null) continue;

                items.add(new AccessReviewItem(
                        campaign.getId(),
                        user.getId(),
                        user.getEmail(),
                        user.getFullName(),
                        AccessScope.PROJECT,
                        p.getName(),
                        p.getId(),
                        AccessSourceType.PROJECT_ACCESS,
                        pa.getId(),
                        "Project Scope: " + pa.getRole().name()
                ));
            }

            // 3. Snapshot Scoped Environment Access
            List<Environment> envs = environmentRepository.findByProjectId(p.getId());
            for (Environment env : envs) {
                if (campaign.getEnvironmentId() != null && !env.getId().equals(campaign.getEnvironmentId())) {
                    continue;
                }
                List<EnvironmentAccess> envGrants = environmentAccessRepository.findByEnvironmentId(env.getId());
                for (EnvironmentAccess ea : envGrants) {
                    User user = userRepository.findById(ea.getUserId()).orElse(null);
                    if (user == null) continue;

                    items.add(new AccessReviewItem(
                            campaign.getId(),
                            user.getId(),
                            user.getEmail(),
                            user.getFullName(),
                            AccessScope.ENVIRONMENT,
                            env.getName() + " (" + p.getName() + ")",
                            env.getId(),
                            AccessSourceType.ENVIRONMENT_ACCESS,
                            ea.getId(),
                            "Environment Permission: " + ea.getPermissionLevel().name()
                    ));
                }
            }
        }

        // 4. Snapshot Granular Access Grants
        List<AccessGrant> granularGrants = accessGrantRepository.findByWorkspaceId(wsId);
        for (AccessGrant g : granularGrants) {
            User user = userRepository.findById(g.getUserId()).orElse(null);
            if (user == null) continue;

            String resourceName = "Workspace Grant";
            UUID resourceId = wsId;
            if (g.getSecretId() != null) {
                resourceName = secretRepository.findById(g.getSecretId()).map(Secret::getName).orElse("Secret");
                resourceId = g.getSecretId();
            } else if (g.getEnvironmentId() != null) {
                resourceName = environmentRepository.findById(g.getEnvironmentId()).map(Environment::getName).orElse("Environment");
                resourceId = g.getEnvironmentId();
            } else if (g.getProjectId() != null) {
                resourceName = projectRepository.findById(g.getProjectId()).map(Project::getName).orElse("Project");
                resourceId = g.getProjectId();
            }

            items.add(new AccessReviewItem(
                    campaign.getId(),
                    user.getId(),
                    user.getEmail(),
                    user.getFullName(),
                    g.getScopeType(),
                    resourceName,
                    resourceId,
                    AccessSourceType.GRANULAR_GRANT,
                    g.getId(),
                    "Granular: " + g.getPermission().getCode()
            ));
        }

        // 5. Snapshot Active JIT Grants
        List<JitAccessRequest> activeJit = jitRepository.findByWorkspaceIdAndStatus(wsId, JitStatus.APPROVED);
        for (JitAccessRequest jit : activeJit) {
            if (jit.getExpiresAt() != null && jit.getExpiresAt().isAfter(clock.instant())) {
                User user = userRepository.findById(jit.getUserId()).orElse(null);
                if (user == null) continue;

                String envName = environmentRepository.findById(jit.getEnvironmentId()).map(Environment::getName).orElse("Environment");
                items.add(new AccessReviewItem(
                        campaign.getId(),
                        user.getId(),
                        user.getEmail(),
                        user.getFullName(),
                        AccessScope.ENVIRONMENT,
                        envName + " (Temporary JIT)",
                        jit.getEnvironmentId(),
                        AccessSourceType.JIT_GRANT,
                        jit.getId(),
                        "JIT Elevation: " + jit.getRequestedPermission().getCode() + " (TTL until " + jit.getExpiresAt() + ")"
                ));
            }
        }

        return items;
    }

    private boolean isHighPrivilege(AccessReviewItem item) {
        String summary = item.getPermissionSummary();
        return summary != null && (
                summary.contains("ADMIN") ||
                summary.contains("OWNER") ||
                summary.contains("access.manage") ||
                summary.contains("secret.reveal") ||
                summary.contains("access_review.manage") ||
                summary.contains("jit.approve")
        );
    }

    private AccessReviewItemResponse mapItemToResponse(AccessReviewItem item, String decidedByEmail, UUID workspaceId) {
        return AccessReviewItemResponse.fromEntity(item, decidedByEmail);
    }

    private Pageable sanitizeCampaignPageable(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        int pageSize = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        if (pageSize <= 0) pageSize = 20;
        int pageNumber = Math.max(pageable.getPageNumber(), 0);

        Sort safeSort = Sort.by(Sort.Direction.DESC, "createdAt");
        if (pageable.getSort().isSorted()) {
            List<Sort.Order> safeOrders = new ArrayList<>();
            for (Sort.Order order : pageable.getSort()) {
                if (ALLOWED_CAMPAIGN_SORT_FIELDS.contains(order.getProperty())) {
                    safeOrders.add(order);
                }
            }
            if (!safeOrders.isEmpty()) {
                safeSort = Sort.by(safeOrders);
            }
        }
        return PageRequest.of(pageNumber, pageSize, safeSort);
    }

    private Pageable sanitizeItemPageable(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "resourceName"));
        }
        int pageSize = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        if (pageSize <= 0) pageSize = 50;
        int pageNumber = Math.max(pageable.getPageNumber(), 0);

        Sort safeSort = Sort.by(Sort.Direction.ASC, "resourceName");
        if (pageable.getSort().isSorted()) {
            List<Sort.Order> safeOrders = new ArrayList<>();
            for (Sort.Order order : pageable.getSort()) {
                if (ALLOWED_ITEM_SORT_FIELDS.contains(order.getProperty())) {
                    safeOrders.add(order);
                }
            }
            if (!safeOrders.isEmpty()) {
                safeSort = Sort.by(safeOrders);
            }
        }
        return PageRequest.of(pageNumber, pageSize, safeSort);
    }

    private AccessReviewCampaignResponse mapToResponse(AccessReviewCampaign campaign) {
        String projectName = campaign.getProjectId() != null
                ? projectRepository.findById(campaign.getProjectId()).map(Project::getName).orElse(null)
                : null;
        String envName = campaign.getEnvironmentId() != null
                ? environmentRepository.findById(campaign.getEnvironmentId()).map(Environment::getName).orElse(null)
                : null;
        String creatorEmail = userRepository.findById(campaign.getCreatedBy()).map(User::getEmail).orElse("unknown");

        return AccessReviewCampaignResponse.fromEntity(campaign, projectName, envName, creatorEmail);
    }
}
