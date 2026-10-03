package com.secretvault.access.privileged.repository;

import com.secretvault.access.privileged.entity.PrivilegedAccessRequest;
import com.secretvault.access.privileged.model.PrivilegedRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrivilegedAccessRequestRepository extends JpaRepository<PrivilegedAccessRequest, UUID> {

    List<PrivilegedAccessRequest> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<PrivilegedAccessRequest> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<PrivilegedAccessRequest> findByWorkspaceIdAndRequesterIdOrderByCreatedAtDesc(UUID workspaceId, UUID requesterId);

    List<PrivilegedAccessRequest> findByWorkspaceIdAndTargetUserIdOrderByCreatedAtDesc(UUID workspaceId, UUID targetUserId);

    List<PrivilegedAccessRequest> findByWorkspaceIdAndStatusOrderByCreatedAtDesc(UUID workspaceId, PrivilegedRequestStatus status);

    @Query("SELECT r FROM PrivilegedAccessRequest r WHERE r.workspaceId = :workspaceId AND r.status = 'PENDING' AND (r.expiresAt IS NULL OR r.expiresAt > :now) ORDER BY r.createdAt DESC")
    List<PrivilegedAccessRequest> findActivePendingRequests(
            @Param("workspaceId") UUID workspaceId,
            @Param("now") Instant now
    );

    @Query("SELECT r FROM PrivilegedAccessRequest r WHERE r.workspaceId = :workspaceId AND r.status = 'PENDING' AND r.expiresAt IS NOT NULL AND r.expiresAt <= :now")
    List<PrivilegedAccessRequest> findExpiredPendingRequests(
            @Param("workspaceId") UUID workspaceId,
            @Param("now") Instant now
    );
}
