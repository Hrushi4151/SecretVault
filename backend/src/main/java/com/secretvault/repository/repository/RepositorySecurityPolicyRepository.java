package com.secretvault.repository.repository;

import com.secretvault.repository.entity.RepositorySecurityPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepositorySecurityPolicyRepository extends JpaRepository<RepositorySecurityPolicy, UUID> {

    Optional<RepositorySecurityPolicy> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<RepositorySecurityPolicy> findByWorkspaceIdAndRepositoryId(UUID workspaceId, UUID repositoryId);

    List<RepositorySecurityPolicy> findByWorkspaceId(UUID workspaceId);

    List<RepositorySecurityPolicy> findByWorkspaceIdAndRepositoryIdIsNull(UUID workspaceId);

    boolean existsByWorkspaceIdAndRepositoryId(UUID workspaceId, UUID repositoryId);
}
