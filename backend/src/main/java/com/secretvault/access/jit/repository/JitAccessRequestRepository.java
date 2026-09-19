package com.secretvault.access.jit.repository;

import com.secretvault.access.jit.entity.JitAccessRequest;
import com.secretvault.access.jit.entity.JitStatus;
import com.secretvault.access.model.AccessPermission;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JitAccessRequestRepository extends JpaRepository<JitAccessRequest, UUID> {

    List<JitAccessRequest> findByWorkspaceId(UUID workspaceId);

    List<JitAccessRequest> findByWorkspaceIdAndStatus(UUID workspaceId, JitStatus status);

    List<JitAccessRequest> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    Optional<JitAccessRequest> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM JitAccessRequest r WHERE r.id = :id AND r.workspaceId = :workspaceId")
    Optional<JitAccessRequest> findByIdAndWorkspaceIdForUpdate(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId);

    @Query("SELECT r FROM JitAccessRequest r WHERE r.workspaceId = :workspaceId AND r.userId = :userId AND r.status = 'APPROVED' AND r.expiresAt > :now")
    List<JitAccessRequest> findActiveGrantsForUser(@Param("workspaceId") UUID workspaceId, @Param("userId") UUID userId, @Param("now") Instant now);

    @Query("SELECT r FROM JitAccessRequest r WHERE r.workspaceId = :workspaceId AND r.userId = :userId AND r.environmentId = :envId AND r.requestedPermission = :perm AND r.status = 'APPROVED' AND r.expiresAt > :now")
    List<JitAccessRequest> findActiveGrantsForEnvAndPerm(
            @Param("workspaceId") UUID workspaceId,
            @Param("userId") UUID userId,
            @Param("envId") UUID envId,
            @Param("perm") AccessPermission perm,
            @Param("now") Instant now
    );
}
