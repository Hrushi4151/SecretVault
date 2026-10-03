package com.secretvault.access.privileged.repository;

import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.privileged.entity.PrivilegedAccessElevation;
import com.secretvault.access.privileged.model.PrivilegedAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrivilegedAccessElevationRepository extends JpaRepository<PrivilegedAccessElevation, UUID> {

    List<PrivilegedAccessElevation> findByWorkspaceIdAndUserIdOrderByCreatedAtDesc(UUID workspaceId, UUID userId);

    List<PrivilegedAccessElevation> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<PrivilegedAccessElevation> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<PrivilegedAccessElevation> findByRequestId(UUID requestId);

    @Query("SELECT e FROM PrivilegedAccessElevation e WHERE e.workspaceId = :workspaceId AND e.userId = :userId AND e.status = 'ACTIVE' AND e.revokedAt IS NULL AND e.startsAt <= :now AND e.expiresAt > :now")
    List<PrivilegedAccessElevation> findActiveElevationsForUser(
            @Param("workspaceId") UUID workspaceId,
            @Param("userId") UUID userId,
            @Param("now") Instant now
    );

    @Query("SELECT e FROM PrivilegedAccessElevation e WHERE e.workspaceId = :workspaceId AND e.userId = :userId AND e.status = 'ACTIVE' AND e.revokedAt IS NULL AND e.startsAt <= :now AND e.expiresAt > :now AND (e.grantedPermission = :permission OR e.grantedPermission IS NULL)")
    List<PrivilegedAccessElevation> findActiveElevationsForUserAndPermission(
            @Param("workspaceId") UUID workspaceId,
            @Param("userId") UUID userId,
            @Param("permission") AccessPermission permission,
            @Param("now") Instant now
    );
}
