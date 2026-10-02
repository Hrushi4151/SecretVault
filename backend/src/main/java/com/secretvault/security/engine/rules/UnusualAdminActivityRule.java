package com.secretvault.security.engine.rules;

import com.secretvault.security.engine.SecurityDetectionRule;
import com.secretvault.security.event.entity.SecurityEvent;
import com.secretvault.security.event.model.SecurityEventType;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class UnusualAdminActivityRule implements SecurityDetectionRule {

    private final SecurityEventRepository eventRepository;

    private static final Set<SecurityEventType> ADMIN_EVENT_TYPES = Set.of(
            SecurityEventType.MEMBER_ADDED,
            SecurityEventType.MEMBER_REMOVED,
            SecurityEventType.MEMBER_ROLE_CHANGED,
            SecurityEventType.PROJECT_ACCESS_CHANGED,
            SecurityEventType.ENVIRONMENT_ACCESS_CHANGED,
            SecurityEventType.ACCESS_GRANT_CREATED,
            SecurityEventType.ACCESS_GRANT_REVOKED,
            SecurityEventType.WORKSPACE_CREATED
    );

    public UnusualAdminActivityRule(SecurityEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    public FindingCategory getCategory() {
        return FindingCategory.UNUSUAL_ADMIN_ACTIVITY;
    }

    @Override
    public String getRuleName() {
        return "Unusual Administrative Activity Detection Rule";
    }

    @Override
    public List<SecurityFindingDraft> evaluate(Workspace workspace, Instant evaluationTime) {
        List<SecurityFindingDraft> findings = new ArrayList<>();
        UUID wsId = workspace.getId();
        Instant since = evaluationTime.minus(Duration.ofHours(24));

        List<SecurityEvent> events = eventRepository.findRecentEvents(wsId, since);
        List<SecurityEvent> adminEvents = events.stream()
                .filter(e -> ADMIN_EVENT_TYPES.contains(e.getEventType()) && e.getActorUserId() != null)
                .toList();

        Map<UUID, List<SecurityEvent>> adminEventsByUser = adminEvents.stream()
                .collect(Collectors.groupingBy(SecurityEvent::getActorUserId));

        for (Map.Entry<UUID, List<SecurityEvent>> entry : adminEventsByUser.entrySet()) {
            UUID actorId = entry.getKey();
            List<SecurityEvent> list = entry.getValue();

            if (list.size() >= 8) {
                Map<String, Object> evidence = Map.of(
                        "actorUserId", actorId.toString(),
                        "adminMutationCount24h", list.size(),
                        "distinctEventTypes", list.stream().map(e -> e.getEventType().name()).distinct().toList()
                );

                findings.add(new SecurityFindingDraft(
                        wsId,
                        null,
                        null,
                        FindingCategory.UNUSUAL_ADMIN_ACTIVITY,
                        list.size() >= 20 ? FindingSeverity.HIGH : FindingSeverity.MEDIUM,
                        FindingConfidence.HIGH,
                        "Unusual Surge in Administrative Policy Mutations (" + list.size() + " in 24h)",
                        "Administrative user executed " + list.size() + " membership, role, or grant mutations within 24 hours.",
                        "Confirm administrative changes were intentional and aligned with scheduled governance or maintenance windows.",
                        "actor:" + actorId,
                        evidence
                ));
            }
        }

        return findings;
    }
}
