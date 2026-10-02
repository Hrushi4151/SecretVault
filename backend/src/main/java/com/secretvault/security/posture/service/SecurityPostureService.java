package com.secretvault.security.posture.service;

import com.secretvault.access.grant.repository.AccessGrantRepository;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.review.entity.AccessReviewCampaign;
import com.secretvault.access.review.entity.CampaignStatus;
import com.secretvault.access.review.repository.AccessReviewCampaignRepository;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditLog;
import com.secretvault.audit.repository.AuditLogRepository;
import com.secretvault.security.event.entity.SecurityEvent;
import com.secretvault.security.event.repository.SecurityEventRepository;
import com.secretvault.security.event.util.SafeEventMetadataSanitizer;
import com.secretvault.security.finding.dto.SecurityFindingResponse;
import com.secretvault.security.finding.entity.SecurityFinding;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.security.finding.model.FindingStatus;
import com.secretvault.security.finding.repository.SecurityFindingRepository;
import com.secretvault.security.posture.dto.SecurityOverviewResponse;
import com.secretvault.security.posture.dto.SecurityPostureResponse;
import com.secretvault.security.posture.dto.SecurityTimelineEventResponse;
import com.secretvault.security.risk.service.RiskAssessmentEngine;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SecurityPostureService {

    private final SecurityFindingRepository findingRepository;
    private final SecurityEventRepository eventRepository;
    private final AuditLogRepository auditLogRepository;
    private final AccessReviewCampaignRepository campaignRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final AccessGrantRepository grantRepository;
    private final RiskAssessmentEngine riskAssessmentEngine;
    private final EffectiveAccessService effectiveAccessService;

    private static final Set<FindingStatus> UNRESOLVED_STATUSES = Set.of(
            FindingStatus.OPEN, FindingStatus.ACKNOWLEDGED, FindingStatus.IN_PROGRESS
    );

    private static final Set<FindingCategory> ACCESS_FINDING_CATEGORIES = Set.of(
            FindingCategory.EXCESSIVE_PRIVILEGE,
            FindingCategory.PRIVILEGE_ESCALATION_PATTERN,
            FindingCategory.DORMANT_PRIVILEGED_ACCESS,
            FindingCategory.UNUSED_GRANULAR_GRANT,
            FindingCategory.ACCESS_CONCENTRATION,
            FindingCategory.ACCESS_REVIEW_OVERDUE
    );

    public SecurityPostureService(
            SecurityFindingRepository findingRepository,
            SecurityEventRepository eventRepository,
            AuditLogRepository auditLogRepository,
            AccessReviewCampaignRepository campaignRepository,
            WorkspaceMembershipRepository membershipRepository,
            AccessGrantRepository grantRepository,
            RiskAssessmentEngine riskAssessmentEngine,
            EffectiveAccessService effectiveAccessService
    ) {
        this.findingRepository = findingRepository;
        this.eventRepository = eventRepository;
        this.auditLogRepository = auditLogRepository;
        this.campaignRepository = campaignRepository;
        this.membershipRepository = membershipRepository;
        this.grantRepository = grantRepository;
        this.riskAssessmentEngine = riskAssessmentEngine;
        this.effectiveAccessService = effectiveAccessService;
    }

    /**
     * Calculates deep, explainable security posture metrics for a workspace.
     */
    @Transactional(readOnly = true)
    public SecurityPostureResponse getPosture(UUID workspaceId, UUID callerUserId) {
        effectiveAccessService.checkPermission(
                workspaceId, null, null, null,
                AccessPermission.SECURITY_VIEW, callerUserId
        );

        RiskAssessmentEngine.RiskAssessmentResult risk = riskAssessmentEngine.computeRisk(workspaceId);

        long openCount = findingRepository.countByWorkspaceIdAndStatusIn(workspaceId, UNRESOLVED_STATUSES);
        long criticalCount = findingRepository.countByWorkspaceIdAndSeverityAndStatusIn(workspaceId, FindingSeverity.CRITICAL, UNRESOLVED_STATUSES);
        long highCount = findingRepository.countByWorkspaceIdAndSeverityAndStatusIn(workspaceId, FindingSeverity.HIGH, UNRESOLVED_STATUSES);
        long mediumCount = findingRepository.countByWorkspaceIdAndSeverityAndStatusIn(workspaceId, FindingSeverity.MEDIUM, UNRESOLVED_STATUSES);
        long lowCount = findingRepository.countByWorkspaceIdAndSeverityAndStatusIn(workspaceId, FindingSeverity.LOW, UNRESOLVED_STATUSES);
        long unresolvedAccessCount = findingRepository.countByWorkspaceIdAndCategoryInAndStatusIn(workspaceId, ACCESS_FINDING_CATEGORIES, UNRESOLVED_STATUSES);

        Instant last24h = Instant.now().minus(Duration.ofHours(24));
        long jitActivity24h = eventRepository.countJitActivitySince(workspaceId, last24h);
        long denials24h = eventRepository.countAuthorizationDenialsSince(workspaceId, last24h);
        long adminChanges24h = eventRepository.countAdminChangesSince(workspaceId, last24h);

        String accessReviewStatus = determineAccessReviewStatus(workspaceId);

        List<WorkspaceMembership> members = membershipRepository.findByWorkspaceId(workspaceId);
        long privilegedUsers = members.stream()
                .filter(m -> m.getRole() == WorkspaceRole.OWNER || m.getRole() == WorkspaceRole.ADMIN)
                .count();

        Instant fourteenDaysAgo = Instant.now().minus(Duration.ofDays(14));
        long unusedGrants = grantRepository.findByWorkspaceId(workspaceId).stream()
                .filter(g -> g.getCreatedAt().isBefore(fourteenDaysAgo))
                .count();

        Instant thirtyDaysAgo = Instant.now().minus(Duration.ofDays(30));
        long dormantPrivileged = members.stream()
                .filter(m -> (m.getRole() == WorkspaceRole.OWNER || m.getRole() == WorkspaceRole.ADMIN) && m.getCreatedAt().isBefore(thirtyDaysAgo))
                .filter(m -> eventRepository.findByWorkspaceIdAndActorUserIdSince(workspaceId, m.getUserId(), thirtyDaysAgo).isEmpty())
                .count();

        return new SecurityPostureResponse(
                workspaceId,
                risk.score(),
                risk.level(),
                openCount,
                criticalCount,
                highCount,
                mediumCount,
                lowCount,
                unresolvedAccessCount,
                jitActivity24h,
                denials24h,
                adminChanges24h,
                accessReviewStatus,
                privilegedUsers,
                unusedGrants,
                dormantPrivileged,
                risk.factors(),
                Instant.now()
        );
    }

    /**
     * Retrieves an executive overview suitable for the Security Center dashboard.
     */
    @Transactional(readOnly = true)
    public SecurityOverviewResponse getOverview(UUID workspaceId, UUID callerUserId) {
        effectiveAccessService.checkPermission(
                workspaceId, null, null, null,
                AccessPermission.SECURITY_VIEW, callerUserId
        );

        RiskAssessmentEngine.RiskAssessmentResult risk = riskAssessmentEngine.computeRisk(workspaceId);

        List<SecurityFinding> activeFindings = findingRepository.findByWorkspaceIdAndStatusIn(workspaceId, UNRESOLVED_STATUSES);
        List<SecurityFindingResponse> topFindings = activeFindings.stream()
                .sorted(Comparator.comparing(SecurityFinding::getSeverity).reversed()
                        .thenComparing(SecurityFinding::getLastObservedAt, Comparator.reverseOrder()))
                .limit(5)
                .map(SecurityFindingResponse::fromEntity)
                .toList();

        Instant last24h = Instant.now().minus(Duration.ofHours(24));
        List<SecurityTimelineEventResponse> timeline = getTimeline(workspaceId, callerUserId, 10);

        long recentDenials = eventRepository.countAuthorizationDenialsSince(workspaceId, last24h);
        long recentEvents = eventRepository.findRecentEvents(workspaceId, last24h).size();
        long criticals = activeFindings.stream().filter(f -> f.getSeverity() == FindingSeverity.CRITICAL).count();
        long highs = activeFindings.stream().filter(f -> f.getSeverity() == FindingSeverity.HIGH).count();

        return new SecurityOverviewResponse(
                workspaceId,
                risk.score(),
                risk.level(),
                activeFindings.size(),
                criticals,
                highs,
                recentEvents,
                recentDenials,
                determineAccessReviewStatus(workspaceId),
                topFindings,
                timeline,
                Instant.now()
        );
    }

    /**
     * Merges security events, audit logs, and findings into a unified, sanitized security timeline.
     */
    @Transactional(readOnly = true)
    public List<SecurityTimelineEventResponse> getTimeline(UUID workspaceId, UUID callerUserId, int limit) {
        effectiveAccessService.checkPermission(
                workspaceId, null, null, null,
                AccessPermission.SECURITY_VIEW, callerUserId
        );

        int boundLimit = Math.max(1, Math.min(100, limit));
        List<SecurityTimelineEventResponse> timeline = new ArrayList<>();

        // 1. Security Events
        List<SecurityEvent> events = eventRepository.findRecentEvents(workspaceId, Instant.now().minus(Duration.ofDays(7)));
        for (SecurityEvent e : events) {
            timeline.add(new SecurityTimelineEventResponse(
                    e.getId().toString(),
                    "SECURITY_EVENT",
                    e.getEventType().name(),
                    "Security Event: " + e.getEventType() + " (Outcome: " + e.getOutcome() + ")",
                    e.getSeverity().name(),
                    e.getOutcome().name(),
                    e.getActorUserId(),
                    e.getProjectId(),
                    e.getEnvironmentId(),
                    e.getTimestamp(),
                    SafeEventMetadataSanitizer.deserialize(e.getMetadataJson())
            ));
        }

        // 2. Audit Logs (if not duplicated)
        List<AuditLog> auditLogs = auditLogRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        for (AuditLog a : auditLogs) {
            timeline.add(new SecurityTimelineEventResponse(
                    a.getId().toString(),
                    "AUDIT_EVENT",
                    a.getAction().name(),
                    "Audit Action: " + a.getAction() + " on " + a.getResourceType(),
                    "INFO",
                    a.getOutcome(),
                    a.getActorId(),
                    null,
                    null,
                    a.getCreatedAt(),
                    Map.of("resourceType", a.getResourceType(), "resourceId", a.getResourceId().toString())
            ));
        }

        // Sort combined timeline descending by timestamp and apply limit
        return timeline.stream()
                .sorted(Comparator.comparing(SecurityTimelineEventResponse::timestamp).reversed())
                .limit(boundLimit)
                .toList();
    }

    private String determineAccessReviewStatus(UUID workspaceId) {
        List<AccessReviewCampaign> campaigns = campaignRepository.findByWorkspaceId(workspaceId);
        if (campaigns.isEmpty()) {
            return "NONE";
        }

        Instant now = Instant.now();
        boolean hasOverdue = campaigns.stream().anyMatch(c ->
                (c.getStatus() == CampaignStatus.OPEN || c.getStatus() == CampaignStatus.IN_PROGRESS) &&
                c.getDueDate().isBefore(now)
        );
        if (hasOverdue) {
            return "OVERDUE";
        }

        boolean hasActive = campaigns.stream().anyMatch(c ->
                c.getStatus() == CampaignStatus.OPEN || c.getStatus() == CampaignStatus.IN_PROGRESS
        );
        if (hasActive) {
            return "IN_PROGRESS";
        }

        return "HEALTHY";
    }
}
