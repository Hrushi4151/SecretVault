package com.secretvault.notification.service;

import com.secretvault.common.exception.ApiException;
import com.secretvault.notification.entity.*;
import com.secretvault.notification.repository.NotificationPreferenceRepository;
import com.secretvault.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Duration DEDUPLICATION_WINDOW = Duration.ofMinutes(5);

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationPreferenceRepository preferenceRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
    }

    @Transactional
    public Notification createNotification(
            UUID workspaceId,
            UUID recipientId,
            NotificationSeverity severity,
            String title,
            String message,
            UUID eventId,
            String actionUrl
    ) {
        return sendNotification(workspaceId, recipientId, severity, title, message, eventId, actionUrl, NotificationChannel.IN_APP);
    }

    @Transactional
    public Notification sendNotification(
            UUID workspaceId,
            UUID recipientId,
            NotificationSeverity severity,
            String title,
            String message,
            UUID eventId,
            String actionUrl,
            NotificationChannel channel
    ) {
        if (workspaceId == null) throw ApiException.badRequest("workspaceId is required");
        if (title == null || title.isBlank()) throw ApiException.badRequest("title is required");
        if (message == null || message.isBlank()) throw ApiException.badRequest("message is required");

        // Check user/workspace preferences
        if (recipientId != null) {
            Optional<NotificationPreference> prefOpt = preferenceRepository.findByWorkspaceIdAndUserId(workspaceId, recipientId);
            if (prefOpt.isPresent()) {
                NotificationPreference pref = prefOpt.get();
                if (isSeveritySuppressed(severity, pref.getMinSeverity())) {
                    log.debug("Notification [{}] suppressed for user [{}] due to severity filter", title, recipientId);
                    return null;
                }
            }
        }

        // Deduplication storm check (collapse identical titles within window)
        Instant since = Instant.now().minus(DEDUPLICATION_WINDOW);
        List<Notification> duplicates = notificationRepository.findByWorkspaceIdAndTitleAndCreatedAtGreaterThanEqual(
                workspaceId, title, since
        );

        if (!duplicates.isEmpty()) {
            Notification existing = duplicates.get(0);
            log.info("Deduplicating notification [{}] - updating existing [{}] count", title, existing.getId());
            existing.setMessage(message + " (Repeated event count: " + (duplicates.size() + 1) + ")");
            existing.setStatus(NotificationStatus.UNREAD);
            return notificationRepository.save(existing);
        }

        Notification notification = new Notification();
        notification.setWorkspaceId(workspaceId);
        notification.setRecipientId(recipientId);
        notification.setSeverity(severity != null ? severity : NotificationSeverity.INFO);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setEventId(eventId);
        notification.setActionUrl(actionUrl);
        notification.setChannel(channel != null ? channel : NotificationChannel.IN_APP);
        notification.setStatus(NotificationStatus.UNREAD);

        Notification saved = notificationRepository.save(notification);
        log.info("Notification [{}] dispatched to recipient [{}] with severity [{}]",
                saved.getId(), recipientId != null ? recipientId : "ALL", saved.getSeverity());
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Notification> getNotifications(UUID workspaceId, UUID userId, NotificationStatus status, Pageable pageable) {
        if (status != null) {
            return notificationRepository.findUserNotificationsByStatus(workspaceId, userId, status, pageable);
        }
        return notificationRepository.findUserNotifications(workspaceId, userId, pageable);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID workspaceId, UUID userId) {
        return notificationRepository.countUnread(workspaceId, userId);
    }

    @Transactional
    public Notification markAsRead(UUID workspaceId, UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findByWorkspaceIdAndId(workspaceId, notificationId)
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        notification.setStatus(NotificationStatus.READ);
        notification.setReadAt(Instant.now());
        return notificationRepository.save(notification);
    }

    @Transactional
    public Notification acknowledge(UUID workspaceId, UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findByWorkspaceIdAndId(workspaceId, notificationId)
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        notification.setStatus(NotificationStatus.ACKNOWLEDGED);
        notification.setAcknowledgedAt(Instant.now());
        return notificationRepository.save(notification);
    }

    @Transactional
    public int markAllAsRead(UUID workspaceId, UUID userId) {
        return notificationRepository.markAllAsRead(workspaceId, userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public NotificationPreference getPreferences(UUID workspaceId, UUID userId) {
        return preferenceRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseGet(() -> {
                    NotificationPreference def = new NotificationPreference();
                    def.setWorkspaceId(workspaceId);
                    def.setUserId(userId);
                    return def;
                });
    }

    @Transactional
    public NotificationPreference updatePreferences(
            UUID workspaceId,
            UUID userId,
            boolean inApp,
            boolean webhook,
            boolean email,
            String minSeverity,
            String mutedEventsJson,
            boolean quietHours,
            String quietStart,
            String quietEnd
    ) {
        NotificationPreference pref = preferenceRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseGet(() -> {
                    NotificationPreference p = new NotificationPreference();
                    p.setWorkspaceId(workspaceId);
                    p.setUserId(userId);
                    return p;
                });

        pref.setChannelInApp(inApp);
        pref.setChannelWebhook(webhook);
        pref.setChannelEmail(email);
        if (minSeverity != null) pref.setMinSeverity(minSeverity);
        if (mutedEventsJson != null) pref.setMutedEventTypesJson(mutedEventsJson);
        pref.setQuietHoursEnabled(quietHours);
        if (quietStart != null) pref.setQuietHoursStart(quietStart);
        if (quietEnd != null) pref.setQuietHoursEnd(quietEnd);

        return preferenceRepository.save(pref);
    }

    private boolean isSeveritySuppressed(NotificationSeverity actual, String minSeverityStr) {
        if (minSeverityStr == null || minSeverityStr.isBlank()) return false;
        try {
            NotificationSeverity min = NotificationSeverity.valueOf(minSeverityStr.toUpperCase());
            return actual.ordinal() < min.ordinal();
        } catch (Exception e) {
            return false;
        }
    }
}
