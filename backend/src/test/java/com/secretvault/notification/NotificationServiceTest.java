package com.secretvault.notification;

import com.secretvault.notification.entity.Notification;
import com.secretvault.notification.entity.NotificationChannel;
import com.secretvault.notification.entity.NotificationPreference;
import com.secretvault.notification.entity.NotificationSeverity;
import com.secretvault.notification.repository.NotificationPreferenceRepository;
import com.secretvault.notification.repository.NotificationRepository;
import com.secretvault.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 13: Notification Engine Tests")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    private NotificationService notificationService;
    private UUID workspaceId;
    private UUID recipientId;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, preferenceRepository);
        workspaceId = UUID.randomUUID();
        recipientId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Send notification creates and persists unread notification")
    void testSendNotification() {
        when(preferenceRepository.findByWorkspaceIdAndUserId(workspaceId, recipientId))
                .thenReturn(Optional.empty());
        when(notificationRepository.findByWorkspaceIdAndTitleAndCreatedAtGreaterThanEqual(eq(workspaceId), eq("New Secret Created"), any(Instant.class)))
                .thenReturn(List.of());
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Notification result = notificationService.sendNotification(
                workspaceId,
                recipientId,
                NotificationSeverity.INFO,
                "New Secret Created",
                "Secret DB_PASSWORD was created in Production",
                UUID.randomUUID(),
                "/secrets/db_password",
                NotificationChannel.IN_APP
        );

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("New Secret Created");
        assertThat(result.getSeverity()).isEqualTo(NotificationSeverity.INFO);
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("Notification is suppressed if severity is below user's minimum severity threshold")
    void testSeveritySuppression() {
        NotificationPreference pref = new NotificationPreference();
        pref.setMinSeverity("HIGH");

        when(preferenceRepository.findByWorkspaceIdAndUserId(workspaceId, recipientId))
                .thenReturn(Optional.of(pref));

        Notification result = notificationService.sendNotification(
                workspaceId,
                recipientId,
                NotificationSeverity.LOW,
                "Minor config change",
                "A minor tag was changed",
                null,
                null,
                NotificationChannel.IN_APP
        );

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Deduplication window collapses repeated alerts into a single digest")
    void testAlertStormDeduplication() {
        Notification existing = new Notification();
        existing.setId(UUID.randomUUID());
        existing.setWorkspaceId(workspaceId);
        existing.setTitle("Consumer Stale Alert");
        existing.setMessage("Consumer worker-1 missed heartbeat");

        when(preferenceRepository.findByWorkspaceIdAndUserId(workspaceId, recipientId))
                .thenReturn(Optional.empty());
        when(notificationRepository.findByWorkspaceIdAndTitleAndCreatedAtGreaterThanEqual(eq(workspaceId), eq("Consumer Stale Alert"), any(Instant.class)))
                .thenReturn(List.of(existing));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Notification result = notificationService.sendNotification(
                workspaceId,
                recipientId,
                NotificationSeverity.MEDIUM,
                "Consumer Stale Alert",
                "Consumer worker-2 missed heartbeat",
                null,
                null,
                NotificationChannel.IN_APP
        );

        assertThat(result).isNotNull();
        assertThat(result.getMessage()).contains("Repeated event count: 2");
    }
}
