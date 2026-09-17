package com.secretvault.project.access.repository;

import com.secretvault.project.access.entity.ProjectAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectAccessRepository extends JpaRepository<ProjectAccess, UUID> {

    List<ProjectAccess> findByProjectId(UUID projectId);

    Optional<ProjectAccess> findByProjectIdAndUserId(UUID projectId, UUID userId);

    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

    void deleteByProjectIdAndUserId(UUID projectId, UUID userId);
}
