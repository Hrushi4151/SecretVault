package com.secretvault.machine.repository;

import com.secretvault.access.model.AccessScope;
import com.secretvault.machine.entity.MachineAccessGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MachineAccessGrantRepository extends JpaRepository<MachineAccessGrant, UUID> {

    List<MachineAccessGrant> findByWorkspaceIdAndMachineIdentityId(UUID workspaceId, UUID machineIdentityId);

    List<MachineAccessGrant> findByMachineIdentityId(UUID machineIdentityId);

    Optional<MachineAccessGrant> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    void deleteByMachineIdentityId(UUID machineIdentityId);

    @Query("SELECT g FROM MachineAccessGrant g WHERE g.workspaceId = :workspaceId AND g.machineIdentityId = :machineIdentityId AND " +
           "(g.scopeType = 'WORKSPACE' OR " +
           "(g.scopeType = 'PROJECT' AND g.projectId = :projectId) OR " +
           "(g.scopeType = 'ENVIRONMENT' AND g.environmentId = :environmentId) OR " +
           "(g.scopeType = 'SECRET' AND (g.secretId = :secretId OR g.secretPattern = :secretKeyName)))")
    List<MachineAccessGrant> findApplicableGrants(
            @Param("workspaceId") UUID workspaceId,
            @Param("machineIdentityId") UUID machineIdentityId,
            @Param("projectId") UUID projectId,
            @Param("environmentId") UUID environmentId,
            @Param("secretId") UUID secretId,
            @Param("secretKeyName") String secretKeyName
    );
}
