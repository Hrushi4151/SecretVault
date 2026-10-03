package com.secretvault.notification.repository;

import com.secretvault.notification.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    Optional<NotificationPreference> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    Optional<NotificationPreference> findByWorkspaceIdAndUserIdIsNull(UUID workspaceId);
}
