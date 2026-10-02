package com.secretvault.oidc.repository;

import com.secretvault.oidc.entity.OidcProvider;
import com.secretvault.oidc.model.OidcProviderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OidcProviderRepository extends JpaRepository<OidcProvider, UUID> {

    List<OidcProvider> findByWorkspaceId(UUID workspaceId);

    Optional<OidcProvider> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Query("SELECT p FROM OidcProvider p WHERE p.workspaceId = :workspaceId AND LOWER(p.name) = LOWER(:name)")
    Optional<OidcProvider> findByWorkspaceIdAndNameIgnoreCase(@Param("workspaceId") UUID workspaceId, @Param("name") String name);

    @Query("SELECT p FROM OidcProvider p WHERE p.workspaceId = :workspaceId AND LOWER(p.issuer) = LOWER(:issuer)")
    Optional<OidcProvider> findByWorkspaceIdAndIssuerIgnoreCase(@Param("workspaceId") UUID workspaceId, @Param("issuer") String issuer);

    @Query("SELECT p FROM OidcProvider p WHERE LOWER(p.issuer) = LOWER(:issuer) AND p.status = 'ACTIVE'")
    List<OidcProvider> findActiveByIssuerIgnoreCase(@Param("issuer") String issuer);

    boolean existsByWorkspaceIdAndNameIgnoreCase(UUID workspaceId, String name);

    boolean existsByWorkspaceIdAndIssuerIgnoreCase(UUID workspaceId, String issuer);

    long countByWorkspaceId(UUID workspaceId);

    long countByWorkspaceIdAndStatus(UUID workspaceId, OidcProviderStatus status);
}
