package com.secretvault.oidc.repository;

import com.secretvault.oidc.entity.OidcTrustPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OidcTrustPolicyRepository extends JpaRepository<OidcTrustPolicy, UUID> {

    List<OidcTrustPolicy> findByWorkspaceId(UUID workspaceId);

    List<OidcTrustPolicy> findByMachineIdentityId(UUID machineIdentityId);

    List<OidcTrustPolicy> findByOidcProviderId(UUID oidcProviderId);

    Optional<OidcTrustPolicy> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Query("SELECT p FROM OidcTrustPolicy p WHERE p.oidcProviderId = :providerId AND p.enabled = true ORDER BY p.priority DESC")
    List<OidcTrustPolicy> findActivePoliciesByProviderId(@Param("providerId") UUID providerId);

    @Query("SELECT p FROM OidcTrustPolicy p WHERE p.workspaceId = :workspaceId AND p.oidcProviderId = :providerId AND p.enabled = true ORDER BY p.priority DESC")
    List<OidcTrustPolicy> findActivePoliciesByWorkspaceAndProvider(@Param("workspaceId") UUID workspaceId, @Param("providerId") UUID providerId);

    void deleteByMachineIdentityId(UUID machineIdentityId);

    long countByWorkspaceId(UUID workspaceId);
}
