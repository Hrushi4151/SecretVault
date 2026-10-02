package com.secretvault.provider.repository;

import com.secretvault.provider.entity.ProviderIntegration;
import com.secretvault.provider.model.IntegrationStatus;
import com.secretvault.provider.model.ProviderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderIntegrationRepository extends JpaRepository<ProviderIntegration, UUID> {

    Optional<ProviderIntegration> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<ProviderIntegration> findByWorkspaceId(UUID workspaceId);

    Page<ProviderIntegration> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    List<ProviderIntegration> findByWorkspaceIdAndStatus(UUID workspaceId, IntegrationStatus status);

    List<ProviderIntegration> findByWorkspaceIdAndProviderType(UUID workspaceId, ProviderType providerType);

    boolean existsByWorkspaceIdAndDisplayName(UUID workspaceId, String displayName);

    @Query("""
        SELECT p FROM ProviderIntegration p
        WHERE p.workspaceId = :workspaceId
          AND (:providerType IS NULL OR p.providerType = :providerType)
          AND (:status IS NULL OR p.status = :status)
          AND (:search IS NULL OR LOWER(p.displayName) LIKE :search)
    """)
    Page<ProviderIntegration> searchIntegrations(
            @Param("workspaceId") UUID workspaceId,
            @Param("providerType") ProviderType providerType,
            @Param("status") IntegrationStatus status,
            @Param("search") String search,
            Pageable pageable
    );
}
