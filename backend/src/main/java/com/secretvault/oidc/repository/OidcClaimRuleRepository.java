package com.secretvault.oidc.repository;

import com.secretvault.oidc.entity.OidcClaimRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OidcClaimRuleRepository extends JpaRepository<OidcClaimRule, UUID> {

    List<OidcClaimRule> findByTrustPolicyId(UUID trustPolicyId);

    void deleteByTrustPolicyId(UUID trustPolicyId);
}
