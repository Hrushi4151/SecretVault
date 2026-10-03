package com.secretvault.events.handler;

import com.secretvault.events.dispatcher.DomainEventHandler;
import com.secretvault.events.model.DomainEvent;
import com.secretvault.events.model.EventSeverity;
import com.secretvault.notification.entity.NotificationChannel;
import com.secretvault.notification.entity.NotificationSeverity;
import com.secretvault.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class NotificationDomainEventHandler implements DomainEventHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationDomainEventHandler.class);
    private static final String CONSUMER_NAME = "NotificationDomainEventHandler";

    private final NotificationService notificationService;

    public NotificationDomainEventHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public String getConsumerName() {
        return CONSUMER_NAME;
    }

    @Override
    public void handle(DomainEvent event) {
        // Only generate notifications for MEDIUM, HIGH, and CRITICAL events, or direct user-facing events
        if (event.getSeverity() == EventSeverity.INFO || event.getSeverity() == EventSeverity.LOW) {
            return;
        }

        try {
            NotificationSeverity severity = mapSeverity(event.getSeverity());
            String title = formatTitle(event);
            String message = formatMessage(event);

            UUID recipient = null;
            if (event.getActorId() != null) {
                try {
                    recipient = UUID.fromString(event.getActorId());
                } catch (IllegalArgumentException ignored) {}
            }

            notificationService.sendNotification(
                    event.getWorkspaceId(),
                    recipient,
                    severity,
                    title,
                    message,
                    event.getEventId(),
                    formatActionUrl(event),
                    NotificationChannel.IN_APP
            );
        } catch (Exception ex) {
            log.warn("Non-fatal: Failed to generate notification for event [{}]: {}", event.getEventId(), ex.getMessage());
        }
    }

    private NotificationSeverity mapSeverity(EventSeverity sev) {
        if (sev == null) return NotificationSeverity.INFO;
        switch (sev) {
            case CRITICAL: return NotificationSeverity.CRITICAL;
            case HIGH: return NotificationSeverity.HIGH;
            case MEDIUM: return NotificationSeverity.MEDIUM;
            case LOW: return NotificationSeverity.LOW;
            default: return NotificationSeverity.INFO;
        }
    }

    private String formatTitle(DomainEvent event) {
        return switch (event.getEventType()) {
            case SECRET_COMPROMISED -> "CRITICAL: Secret Marked as Compromised";
            case ROTATION_FAILED -> "Secret Rotation Execution Failed";
            case ROTATION_COMPLETED -> "Secret Rotation Completed Successfully";
            case LEASE_EXPIRED -> "Ephemeral Secret Lease Expired";
            case MACHINE_SUSPENDED -> "Machine Identity Suspended";
            case SECURITY_FINDING_CREATED -> "New Security Finding Detected";
            case SECURITY_INCIDENT_CREATED -> "Security Incident Opened";
            case JIT_REQUESTED -> "JIT Privileged Access Elevation Requested";
            default -> "Security Alert: " + event.getEventType().name();
        };
    }

    private String formatMessage(DomainEvent event) {
        return String.format("Event [%s] occurred on aggregate [%s:%s] in workspace [%s]. Source: %s.",
                event.getEventType(), event.getAggregateType(), event.getAggregateId(),
                event.getWorkspaceId(), event.getSource());
    }

    private String formatActionUrl(DomainEvent event) {
        if (event.getSecretId() != null) {
            return "/secrets?id=" + event.getSecretId();
        }
        return "/events?id=" + event.getEventId();
    }
}
