package com.secretvault.notification.controller;

import com.secretvault.auth.security.UserPrincipal;
import com.secretvault.common.dto.ApiResponse;
import com.secretvault.notification.entity.Notification;
import com.secretvault.notification.entity.NotificationPreference;
import com.secretvault.notification.entity.NotificationSeverity;
import com.secretvault.notification.entity.NotificationStatus;
import com.secretvault.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}")
@Tag(name = "Notifications", description = "In-app and multi-channel security notifications and preferences")
@SecurityRequirement(name = "BearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public record UpdateNotificationPreferencesRequest(
            boolean channelInApp,
            boolean channelWebhook,
            boolean channelEmail,
            String minSeverity,
            String mutedEventTypesJson,
            boolean quietHoursEnabled,
            String quietHoursStart,
            String quietHoursEnd
    ) {}

    @GetMapping("/notifications")
    @Operation(summary = "List notifications for current user/workspace")
    public ResponseEntity<ApiResponse<Page<Notification>>> listNotifications(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) NotificationStatus status,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        Page<Notification> notifications = notificationService.getNotifications(workspaceId, actorId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(notifications));
    }

    @PostMapping("/notifications/{notificationId}/read")
    @Operation(summary = "Mark notification as read")
    public ResponseEntity<ApiResponse<Notification>> markRead(
            @PathVariable UUID workspaceId,
            @PathVariable UUID notificationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        Notification notif = notificationService.markAsRead(workspaceId, notificationId, actorId);
        return ResponseEntity.ok(ApiResponse.success(notif));
    }

    @PostMapping("/notifications/{notificationId}/acknowledge")
    @Operation(summary = "Acknowledge alert notification")
    public ResponseEntity<ApiResponse<Notification>> acknowledge(
            @PathVariable UUID workspaceId,
            @PathVariable UUID notificationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        Notification notif = notificationService.acknowledge(workspaceId, notificationId, actorId);
        return ResponseEntity.ok(ApiResponse.success(notif, "Notification acknowledged"));
    }

    @PostMapping("/notifications/read-all")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        notificationService.markAllAsRead(workspaceId, actorId);
        return ResponseEntity.ok(ApiResponse.success(null, "All notifications marked as read"));
    }

    @GetMapping("/notification-preferences")
    @Operation(summary = "Get user notification preferences")
    public ResponseEntity<ApiResponse<NotificationPreference>> getPreferences(
            @PathVariable UUID workspaceId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        NotificationPreference prefs = notificationService.getPreferences(workspaceId, actorId);
        return ResponseEntity.ok(ApiResponse.success(prefs));
    }

    @PutMapping("/notification-preferences")
    @Operation(summary = "Update user notification preferences")
    public ResponseEntity<ApiResponse<NotificationPreference>> updatePreferences(
            @PathVariable UUID workspaceId,
            @RequestBody UpdateNotificationPreferencesRequest req,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        NotificationPreference prefs = notificationService.updatePreferences(
                workspaceId,
                actorId,
                req.channelInApp(),
                req.channelWebhook(),
                req.channelEmail(),
                req.minSeverity(),
                req.mutedEventTypesJson(),
                req.quietHoursEnabled(),
                req.quietHoursStart(),
                req.quietHoursEnd()
        );
        return ResponseEntity.ok(ApiResponse.success(prefs, "Notification preferences updated"));
    }
}
