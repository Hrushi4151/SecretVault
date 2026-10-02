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
import java.util.stream.Collectors;

@Component
public class AuthenticationAnomalyRule implements SecurityDetectionRule {

    private final SecurityEventRepository eventRepository;

    public AuthenticationAnomalyRule(SecurityEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    public FindingCategory getCategory() {
        return FindingCategory.AUTHENTICATION_ANOMALY;
    }

    @Override
    public String getRuleName() {
        return "Authentication Anomaly Detection Rule";
    }

    @Override
    public List<SecurityFindingDraft> evaluate(Workspace workspace, Instant evaluationTime) {
        List<SecurityFindingDraft> findings = new ArrayList<>();
        Instant since = evaluationTime.minus(Duration.ofHours(24));

        List<SecurityEvent> events = eventRepository.findRecentEvents(workspace.getId(), since);
        List<SecurityEvent> authFailures = events.stream()
                .filter(e -> e.getEventType() == SecurityEventType.AUTH_LOGIN_FAILURE ||
                             e.getEventType() == SecurityEventType.AUTH_ACCOUNT_LOCKED)
                .toList();

        Map<String, List<SecurityEvent>> failuresBySubject = authFailures.stream()
                .collect(Collectors.groupingBy(e -> e.getActorUserId() != null ? e.getActorUserId().toString() :
                        (e.getIpAddress() != null ? e.getIpAddress() : "unknown")));

        for (Map.Entry<String, List<SecurityEvent>> entry : failuresBySubject.entrySet()) {
            String subject = entry.getKey();
            List<SecurityEvent> list = entry.getValue();

            boolean hasLockout = list.stream().anyMatch(e -> e.getEventType() == SecurityEventType.AUTH_ACCOUNT_LOCKED);

            if (list.size() >= 3 || hasLockout) {
                Map<String, Object> evidence = Map.of(
                        "subject", subject,
                        "authFailureCount24h", list.size(),
                        "accountLockoutTriggered", hasLockout,
                        "recentIps", list.stream().map(SecurityEvent::getIpAddress).filter(ip -> ip != null).distinct().toList()
                );

                findings.add(new SecurityFindingDraft(
                        workspace.getId(),
                        null,
                        null,
                        FindingCategory.AUTHENTICATION_ANOMALY,
                        hasLockout || list.size() >= 10 ? FindingSeverity.HIGH : FindingSeverity.MEDIUM,
                        FindingConfidence.HIGH,
                        "Repeated Authentication Failures / Lockout Detected",
                        "Observed " + list.size() + " failed authentication attempts in 24 hours against account / subject [" + subject + "].",
                        "Verify legitimacy with user and check whether IP address should be added to security blocklist or account password reset.",
                        "subject:" + subject,
                        evidence
                ));
            }
        }

        return findings;
    }
}
