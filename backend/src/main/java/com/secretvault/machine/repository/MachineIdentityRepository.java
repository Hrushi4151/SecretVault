package com.secretvault.machine.repository;

import com.secretvault.machine.entity.MachineIdentity;
import com.secretvault.machine.model.MachineStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MachineIdentityRepository extends JpaRepository<MachineIdentity, UUID> {

    List<MachineIdentity> findByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId);

    Optional<MachineIdentity> findByIdAndWorkspaceIdAndDeletedAtIsNull(UUID id, UUID workspaceId);

    Optional<MachineIdentity> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT m FROM MachineIdentity m WHERE m.workspaceId = :workspaceId AND LOWER(m.name) = LOWER(:name) AND m.deletedAt IS NULL")
    Optional<MachineIdentity> findByWorkspaceIdAndNameIgnoreCase(@Param("workspaceId") UUID workspaceId, @Param("name") String name);

    boolean existsByWorkspaceIdAndNameIgnoreCaseAndDeletedAtIsNull(UUID workspaceId, String name);

    boolean existsByIdAndWorkspaceIdAndDeletedAtIsNull(UUID id, UUID workspaceId);

    @Query("SELECT m FROM MachineIdentity m WHERE m.status = 'ACTIVE' AND m.expiresAt IS NOT NULL AND m.expiresAt <= :now AND m.deletedAt IS NULL")
    List<MachineIdentity> findActiveExpiredIdentities(@Param("now") Instant now);

    long countByWorkspaceIdAndDeletedAtIsNull(UUID workspaceId);

    long countByWorkspaceIdAndStatusAndDeletedAtIsNull(UUID workspaceId, MachineStatus status);
}
