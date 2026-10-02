package com.secretvault.security.engine.rules;

import com.secretvault.access.grant.entity.AccessGrant;
import com.secretvault.access.grant.repository.AccessGrantRepository;
import com.secretvault.security.engine.SecurityDetectionRule;
import com.secretvault.security.event.entity.SecurityEvent;
import com.secretvault.security.event.repository.SecurityEventRepository;
import com.secretvault.security.finding.dto.SecurityFindingDraft;
import com.secretvault.security.finding.model.FindingCategory;
import com.secretvault.security.finding.model.FindingConfidence;
import com.secretvault.security.finding.model.FindingSeverity;
import com.secretvault.workspace.entity.Workspace;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class UnusedGrantRule implements SecurityDetectionRule {

    private final AccessGrantRepository grantRepository;
    private final SecurityEventRepository eventRepository;

    public UnusedGrantRule(
            AccessGrantRepository grantRepository,
            SecurityEventRepository eventRepository
    ) {
        this.grantRepository = grantRepository;
        this.eventRepository = eventRepository;
    }

    @Override
    public FindingCategory getCategory() {
        return FindingCategory.UNUSED_GRANULAR_GRANT;
    }

    @Override
    public String getRuleName() {
        return "Unused Granular Grant Detection Rule";
    }

    @Override
    public List<SecurityFindingDraft> evaluate(Workspace workspace, Instant evaluationTime) {
        List<SecurityFindingDraft> findings = new ArrayList<>();
        UUID wsId = workspace.getId();
        Instant fourteenDaysAgo = evaluationTime.minus(Duration.ofDays(14));

        List<AccessGrant> grants = grantRepository.findByWorkspaceId(wsId);
        for (AccessGrant grant : grants) {
            if (grant.getCreatedAt() != null && grant.getCreatedAt().isBefore(fourteenDaysAgo)) {
                List<SecurityEvent> userEvents = eventRepository.findByWorkspaceIdAndActorUserIdSince(
                        wsId, grant.getUserId(), fourteenDaysAgo
                );

                if (userEvents.isEmpty()) {
                    Map<String, Object> evidence = Map.of(
                            "grantId", grant.getId().toString(),
                            "userId", grant.getUserId().toString(),
                            "permission", grant.getPermission().getCode(),
                            "scopeType", grant.getScopeType().name(),
                            "createdAt", grant.getCreatedAt().toString()
                    );

                    findings.add(new SecurityFindingDraft(
                            wsId,
                            grant.getProjectId(),
                            grant.getEnvironmentId(),
                            FindingCategory.UNUSED_GRANULAR_GRANT,
                            FindingSeverity.LOW,
                            FindingConfidence.HIGH,
                            "Stale Granular Permission Grant: " + grant.getPermission().getCode(),
                            "Granular access grant for permission [" + grant.getPermission().getCode() + "] has remained unexercised for over 14 days.",
                            "Revoke the granular grant if temporary operational access is no longer required.",
                            "grant:" + grant.getId(),
                            evidence
                    ));
                }
            }
        }

        return findings;
    }
}
