package com.secretvault.machine.repository;

import com.secretvault.machine.entity.MachineSession;
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
public interface MachineSessionRepository extends JpaRepository<MachineSession, UUID> {

    Optional<MachineSession> findByTokenHash(String tokenHash);

    List<MachineSession> findByWorkspaceIdAndMachineIdentityIdOrderByIssuedAtDesc(UUID workspaceId, UUID machineIdentityId);

    List<MachineSession> findByMachineIdentityIdOrderByIssuedAtDesc(UUID machineIdentityId);

    Optional<MachineSession> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Modifying
    @Query("UPDATE MachineSession s SET s.revokedAt = :now WHERE s.machineIdentityId = :machineIdentityId AND s.revokedAt IS NULL")
    int revokeAllByMachineIdentityId(@Param("machineIdentityId") UUID machineIdentityId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE MachineSession s SET s.revokedAt = :now WHERE s.workspaceId = :workspaceId AND s.machineIdentityId = :machineIdentityId AND s.revokedAt IS NULL")
    int revokeAllByWorkspaceAndMachine(@Param("workspaceId") UUID workspaceId, @Param("machineIdentityId") UUID machineIdentityId, @Param("now") Instant now);

    @Query("SELECT COUNT(s) FROM MachineSession s WHERE s.workspaceId = :workspaceId AND s.revokedAt IS NULL AND s.expiresAt > :now")
    long countActiveSessions(@Param("workspaceId") UUID workspaceId, @Param("now") Instant now);
}
