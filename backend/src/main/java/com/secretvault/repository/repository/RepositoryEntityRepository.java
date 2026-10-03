package com.secretvault.repository.repository;

import com.secretvault.repository.entity.RepositoryEntity;
import com.secretvault.repository.model.RepositoryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositoryEntityRepository extends JpaRepository<RepositoryEntity, UUID> {

    Optional<RepositoryEntity> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Page<RepositoryEntity> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    Page<RepositoryEntity> findByWorkspaceIdAndStatus(UUID workspaceId, RepositoryStatus status, Pageable pageable);

    boolean existsByWorkspaceIdAndOwnerAndName(UUID workspaceId, String owner, String name);

    List<RepositoryEntity> findByWorkspaceIdAndStatus(UUID workspaceId, RepositoryStatus status);

    long countByWorkspaceId(UUID workspaceId);

    long countByWorkspaceIdAndStatus(UUID workspaceId, RepositoryStatus status);
}
