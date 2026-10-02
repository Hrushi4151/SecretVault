package com.secretvault.auth.mfa.repository;

import com.secretvault.auth.mfa.entity.MfaStatus;
import com.secretvault.auth.mfa.entity.UserMfa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository managing persistent UserMfa records.
 */
@Repository
public interface UserMfaRepository extends JpaRepository<UserMfa, UUID> {

    Optional<UserMfa> findByUserId(UUID userId);

    Optional<UserMfa> findByUserIdAndStatus(UUID userId, MfaStatus status);

    boolean existsByUserId(UUID userId);

    boolean existsByUserIdAndStatus(UUID userId, MfaStatus status);

    void deleteByUserId(UUID userId);
}
