package com.secretvault.environment.repository;

import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.entity.EnvironmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnvironmentRepository extends JpaRepository<Environment, UUID> {

    List<Environment> findByProjectId(UUID projectId);

    List<Environment> findByProjectIdAndStatus(UUID projectId, EnvironmentStatus status);

    Optional<Environment> findByIdAndProjectId(UUID id, UUID projectId);

    Optional<Environment> findByProjectIdAndSlug(UUID projectId, String slug);

    boolean existsByProjectIdAndSlug(UUID projectId, String slug);

    boolean existsByProjectIdAndSlugAndIdNot(UUID projectId, String slug, UUID id);

    long countByProjectId(UUID projectId);
}
