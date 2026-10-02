package com.secretvault.security.engine.rules;

import com.secretvault.security.engine.SecurityDetectionRule;
import com.secretvault.security.event.entity.SecurityEvent;
import com.secretvault.security.event.repository.SecurityEventRepository;
import com.secretvault.security.finding.dto.SecurityFindingDraft;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.model.FindingConfidence;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.workspace.entity.Workspace;
import com.secretvault.workspace.entity.WorkspaceMembership;
import com.secretvault.workspace.entity.WorkspaceRole;
import com.secretvault.workspace.repository.WorkspaceMembershipRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class DormantPrivilegedAccessRule implements SecurityDetectionRule {

    private final WorkspaceMembershipRepository membershipRepository;
    private final SecurityEventRepository eventRepository;

    public DormantPrivilegedAccessRule(
            WorkspaceMembershipRepository membershipRepository,
            SecurityEventRepository eventRepository
    ) {
        this.membershipRepository = membershipRepository;
        this.eventRepository = eventRepository;
    }

    @Override
    public FindingCategory getCategory() {
        return FindingCategory.DORMANT_PRIVILEGED_ACCESS;
    }

    @Override
    public String getRuleName() {
        return "Dormant Privileged Account Detection Rule";
    }

    @Override
    public List<SecurityFindingDraft> evaluate(Workspace workspace, Instant evaluationTime) {
        List<SecurityFindingDraft> findings = new ArrayList<>();
        UUID wsId = workspace.getId();
        Instant thirtyDaysAgo = evaluationTime.minus(Duration.ofDays(30));

        List<WorkspaceMembership> members = membershipRepository.findByWorkspaceId(wsId);
        for (WorkspaceMembership member : members) {
            if (member.getRole() == WorkspaceRole.OWNER || member.getRole() == WorkspaceRole.ADMIN) {
                // If member was created more than 30 days ago
                if (member.getCreatedAt().isBefore(thirtyDaysAgo)) {
                    List<SecurityEvent> recentActivity = eventRepository.findByWorkspaceIdAndActorUserIdSince(
                            wsId, member.getUserId(), thirtyDaysAgo
                    );

                    if (recentActivity.isEmpty()) {
                        Map<String, Object> evidence = Map.of(
                                "userId", member.getUserId().toString(),
                                "role", member.getRole().name(),
                                "memberSince", member.getCreatedAt().toString(),
                                "daysDormant", 30
                        );

                        findings.add(new SecurityFindingDraft(
                                wsId,
                                null,
                                null,
                                FindingCategory.DORMANT_PRIVILEGED_ACCESS,
                                member.getRole() == WorkspaceRole.OWNER ? FindingSeverity.HIGH : FindingSeverity.MEDIUM,
                                FindingConfidence.HIGH,
                                "Dormant Privileged Account (" + member.getRole() + ")",
                                "User holds standing " + member.getRole() + " privileges with zero observed activity across the past 30 days.",
                                "Evaluate whether standing administrative privileges can be demoted to standard member role with JIT elevation on demand.",
                                "user:" + member.getUserId(),
                                evidence
                        ));
                    }
                }
            }
        }

        return findings;
    }
}
