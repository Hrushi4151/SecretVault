package com.secretvault.environment.access.repository;

import com.secretvault.environment.access.entity.EnvironmentAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnvironmentAccessRepository extends JpaRepository<EnvironmentAccess, UUID> {

    List<EnvironmentAccess> findByEnvironmentId(UUID environmentId);

    Optional<EnvironmentAccess> findByEnvironmentIdAndUserId(UUID environmentId, UUID userId);

    boolean existsByEnvironmentIdAndUserId(UUID environmentId, UUID userId);

    void deleteByEnvironmentIdAndUserId(UUID environmentId, UUID userId);
}
