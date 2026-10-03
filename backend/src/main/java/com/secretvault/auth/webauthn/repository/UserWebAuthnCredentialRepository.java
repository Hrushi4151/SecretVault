package com.secretvault.auth.webauthn.repository;

import com.secretvault.auth.webauthn.entity.UserWebAuthnCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link UserWebAuthnCredential}.
 */
@Repository
public interface UserWebAuthnCredentialRepository extends JpaRepository<UserWebAuthnCredential, UUID> {

    Optional<UserWebAuthnCredential> findByCredentialId(String credentialId);

    Optional<UserWebAuthnCredential> findByCredentialIdAndRevokedAtIsNull(String credentialId);

    Optional<UserWebAuthnCredential> findByIdAndUserId(UUID id, UUID userId);

    List<UserWebAuthnCredential> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(UUID userId);

    List<UserWebAuthnCredential> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserIdAndRevokedAtIsNull(UUID userId);

    boolean existsByCredentialId(String credentialId);

    @Query("SELECT c FROM UserWebAuthnCredential c WHERE c.userId = :userId AND c.revokedAt IS NULL")
    List<UserWebAuthnCredential> findActiveByUserId(@Param("userId") UUID userId);

    @Query("SELECT c FROM UserWebAuthnCredential c WHERE c.credentialId IN :credentialIds AND c.revokedAt IS NULL")
    List<UserWebAuthnCredential> findActiveByCredentialIds(@Param("credentialIds") Set<String> credentialIds);
}
