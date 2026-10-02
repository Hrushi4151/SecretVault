package com.secretvault.provider.repository;

import com.secretvault.provider.entity.ProviderResourceMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderResourceMappingRepository extends JpaRepository<ProviderResourceMapping, UUID> {

    List<ProviderResourceMapping> findByWorkspaceIdAndIntegrationId(UUID workspaceId, UUID integrationId);

    List<ProviderResourceMapping> findByWorkspaceId(UUID workspaceId);

    List<ProviderResourceMapping> findByEnvironmentId(UUID environmentId);

    List<ProviderResourceMapping> findByProjectId(UUID projectId);

    Optional<ProviderResourceMapping> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<ProviderResourceMapping> findByWorkspaceIdAndIntegrationIdAndProjectIdAndEnvironmentId(
            UUID workspaceId,
            UUID integrationId,
            UUID projectId,
            UUID environmentId
    );

    boolean existsByWorkspaceIdAndIntegrationIdAndProjectIdAndEnvironmentId(
            UUID workspaceId,
            UUID integrationId,
            UUID projectId,
            UUID environmentId
    );

    void deleteByIntegrationId(UUID integrationId);
}
