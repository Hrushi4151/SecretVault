package com.secretvault.security.engine.rules;

import com.secretvault.security.engine.SecurityDetectionRule;
import com.secretvault.security.event.entity.SecurityEvent;
import com.secretvault.security.event.model.SecurityEventOutcome;
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
public class RepeatedAuthorizationFailureRule implements SecurityDetectionRule {

    private final SecurityEventRepository eventRepository;

    public RepeatedAuthorizationFailureRule(SecurityEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    public FindingCategory getCategory() {
        return FindingCategory.REPEATED_AUTHORIZATION_FAILURES;
    }

    @Override
    public String getRuleName() {
        return "Repeated Authorization Failures Detection Rule";
    }

    @Override
    public List<SecurityFindingDraft> evaluate(Workspace workspace, Instant evaluationTime) {
        List<SecurityFindingDraft> findings = new ArrayList<>();
        UUID wsId = workspace.getId();
        Instant since = evaluationTime.minus(Duration.ofHours(24));

        List<SecurityEvent> events = eventRepository.findRecentEvents(wsId, since);
        List<SecurityEvent> denials = events.stream()
                .filter(e -> e.getOutcome() == SecurityEventOutcome.DENIED || e.getEventType() == SecurityEventType.AUTHORIZATION_DENIED)
                .toList();

        Map<String, List<SecurityEvent>> denialsBySubject = denials.stream()
                .collect(Collectors.groupingBy(e -> e.getActorUserId() != null ? e.getActorUserId().toString() : (e.getIpAddress() != null ? e.getIpAddress() : "unknown")));

        for (Map.Entry<String, List<SecurityEvent>> entry : denialsBySubject.entrySet()) {
            String subject = entry.getKey();
            List<SecurityEvent> list = entry.getValue();

            if (list.size() >= 5) {
                Map<String, Object> evidence = Map.of(
                        "subject", subject,
                        "denialCount24h", list.size(),
                        "recentRequestIds", list.stream().map(SecurityEvent::getRequestId).filter(id -> id != null).limit(10).toList(),
                        "recentSources", list.stream().map(SecurityEvent::getSource).distinct().toList()
                );

                findings.add(new SecurityFindingDraft(
                        wsId,
                        null,
                        null,
                        FindingCategory.REPEATED_AUTHORIZATION_FAILURES,
                        list.size() >= 15 ? FindingSeverity.HIGH : FindingSeverity.MEDIUM,
                        FindingConfidence.HIGH,
                        "Elevated Rate of Authorization Denials (" + list.size() + " in 24h)",
                        "Actor encountered " + list.size() + " authorization denials in the last 24 hours, suggesting credential probing or permission misalignment.",
                        "Investigate denial access logs and verify if caller identity or automated script requires explicit scoping or token revocation.",
                        "subject:" + subject,
                        evidence
                ));
            }
        }

        return findings;
    }
}
