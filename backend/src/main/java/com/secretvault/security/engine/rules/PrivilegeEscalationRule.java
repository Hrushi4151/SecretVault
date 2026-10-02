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
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PrivilegeEscalationRule implements SecurityDetectionRule {

    private final SecurityEventRepository eventRepository;

    public PrivilegeEscalationRule(SecurityEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    public FindingCategory getCategory() {
        return FindingCategory.PRIVILEGE_ESCALATION_PATTERN;
    }

    @Override
    public String getRuleName() {
        return "Privilege Escalation Pattern Detection Rule";
    }

    @Override
    public List<SecurityFindingDraft> evaluate(Workspace workspace, Instant evaluationTime) {
        List<SecurityFindingDraft> findings = new ArrayList<>();
        UUID wsId = workspace.getId();
        Instant since = evaluationTime.minus(Duration.ofHours(48));

        List<SecurityEvent> events = eventRepository.findRecentEvents(wsId, since);
        Map<UUID, List<SecurityEvent>> eventsByUser = events.stream()
                .filter(e -> e.getActorUserId() != null)
                .collect(Collectors.groupingBy(SecurityEvent::getActorUserId));

        for (Map.Entry<UUID, List<SecurityEvent>> entry : eventsByUser.entrySet()) {
            UUID userId = entry.getKey();
            List<SecurityEvent> userEvents = entry.getValue();

            boolean hasElevation = userEvents.stream().anyMatch(e ->
                    e.getEventType() == SecurityEventType.ACCESS_GRANT_CREATED ||
                    e.getEventType() == SecurityEventType.JIT_APPROVED ||
                    e.getEventType() == SecurityEventType.MEMBER_ROLE_CHANGED
            );

            long revealCount = userEvents.stream().filter(e ->
                    e.getEventType() == SecurityEventType.SECRET_REVEALED ||
                    e.getEventType() == SecurityEventType.SECRET_HISTORICAL_REVEALED
            ).count();

            if (hasElevation && revealCount >= 3) {
                Map<String, Object> evidence = Map.of(
                        "userId", userId.toString(),
                        "elevationObserved", true,
                        "revealCount48h", revealCount,
                        "timeWindowHours", 48
                );

                findings.add(new SecurityFindingDraft(
                        wsId,
                        null,
                        null,
                        FindingCategory.PRIVILEGE_ESCALATION_PATTERN,
                        revealCount >= 8 ? FindingSeverity.CRITICAL : FindingSeverity.HIGH,
                        FindingConfidence.HIGH,
                        "Rapid Privilege Elevation and High-Volume Secret Reveals",
                        "User received access elevation and subsequently executed " + revealCount + " secret reveal operations within a 48-hour window.",
                        "Inspect reveal activity with user to verify business need and revoke temporary grants if access is no longer required.",
                        "user:" + userId,
                        evidence
                ));
            }
        }

        return findings;
    }
}
