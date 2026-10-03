package com.secretvault.notification.repository;

import com.secretvault.notification.entity.Notification;
import com.secretvault.notification.entity.NotificationSeverity;
import com.secretvault.notification.entity.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByWorkspaceIdAndId(UUID workspaceId, UUID id);

    @Query("SELECT n FROM Notification n WHERE n.workspaceId = :workspaceId " +
            "AND (n.recipientId = :recipientId OR n.recipientId IS NULL) " +
            "ORDER BY n.createdAt DESC")
    Page<Notification> findUserNotifications(
            @Param("workspaceId") UUID workspaceId,
            @Param("recipientId") UUID recipientId,
            Pageable pageable
    );

    @Query("SELECT n FROM Notification n WHERE n.workspaceId = :workspaceId " +
            "AND (n.recipientId = :recipientId OR n.recipientId IS NULL) " +
            "AND n.status = :status " +
            "ORDER BY n.createdAt DESC")
    Page<Notification> findUserNotificationsByStatus(
            @Param("workspaceId") UUID workspaceId,
            @Param("recipientId") UUID recipientId,
            @Param("status") NotificationStatus status,
            Pageable pageable
    );

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.workspaceId = :workspaceId " +
            "AND (n.recipientId = :recipientId OR n.recipientId IS NULL) " +
            "AND n.status = 'UNREAD'")
    long countUnread(
            @Param("workspaceId") UUID workspaceId,
            @Param("recipientId") UUID recipientId
    );

    @Modifying
    @Query("UPDATE Notification n SET n.status = 'READ', n.readAt = :now " +
            "WHERE n.workspaceId = :workspaceId " +
            "AND (n.recipientId = :recipientId OR n.recipientId IS NULL) " +
            "AND n.status = 'UNREAD'")
    int markAllAsRead(
            @Param("workspaceId") UUID workspaceId,
            @Param("recipientId") UUID recipientId,
            @Param("now") Instant now
    );

    List<Notification> findByWorkspaceIdAndTitleAndCreatedAtGreaterThanEqual(
            UUID workspaceId, String title, Instant since
    );
}
