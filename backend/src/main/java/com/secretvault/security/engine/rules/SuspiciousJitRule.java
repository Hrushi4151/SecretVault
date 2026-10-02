package com.secretvault.security.engine.rules;

import com.secretvault.access.jit.entity.JitAccessRequest;
import com.secretvault.access.jit.entity.JitStatus;
import com.secretvault.access.jit.repository.JitAccessRequestRepository;
import com.secretvault.security.engine.SecurityDetectionRule;
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
import java.util.stream.Collectors;

@Component
public class SuspiciousJitRule implements SecurityDetectionRule {

    private final JitAccessRequestRepository jitRepository;

    public SuspiciousJitRule(JitAccessRequestRepository jitRepository) {
        this.jitRepository = jitRepository;
    }

    @Override
    public FindingCategory getCategory() {
        return FindingCategory.SUSPICIOUS_JIT_ACTIVITY;
    }

    @Override
    public String getRuleName() {
        return "Suspicious JIT Activity Detection Rule";
    }

    @Override
    public List<SecurityFindingDraft> evaluate(Workspace workspace, Instant evaluationTime) {
        List<SecurityFindingDraft> findings = new ArrayList<>();
        UUID wsId = workspace.getId();
        Instant since = evaluationTime.minus(Duration.ofHours(24));

        List<JitAccessRequest> allRequests = jitRepository.findByWorkspaceId(wsId);
        List<JitAccessRequest> recentRequests = allRequests.stream()
                .filter(r -> r.getCreatedAt().isAfter(since))
                .toList();

        Map<UUID, List<JitAccessRequest>> userRequests = recentRequests.stream()
                .collect(Collectors.groupingBy(JitAccessRequest::getUserId));

        for (Map.Entry<UUID, List<JitAccessRequest>> entry : userRequests.entrySet()) {
            UUID userId = entry.getKey();
            List<JitAccessRequest> list = entry.getValue();

            long count = list.size();
            long rejectedCount = list.stream().filter(r -> r.getStatus() == JitStatus.REJECTED || r.getStatus() == JitStatus.CANCELLED).count();

            if (count >= 3 || rejectedCount >= 2) {
                Map<String, Object> evidence = Map.of(
                        "userId", userId.toString(),
                        "totalJitRequests24h", count,
                        "rejectedOrCancelledCount", rejectedCount,
                        "requestIds", list.stream().map(r -> r.getId().toString()).toList()
                );

                findings.add(new SecurityFindingDraft(
                        wsId,
                        null,
                        null,
                        FindingCategory.SUSPICIOUS_JIT_ACTIVITY,
                        rejectedCount >= 3 ? FindingSeverity.HIGH : FindingSeverity.MEDIUM,
                        FindingConfidence.HIGH,
                        "High Velocity / Repeated JIT Elevation Requests",
                        "User submitted " + count + " JIT elevation requests within 24 hours (" + rejectedCount + " rejected/cancelled).",
                        "Audit requester operational justification and review approver decisions to verify valid workflow requirements.",
                        "user:" + userId,
                        evidence
                ));
            }
        }

        return findings;
    }
}
