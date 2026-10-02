package com.secretvault.auth.mfa.repository;

import com.secretvault.auth.mfa.entity.MfaRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository managing hashed backup recovery codes for MFA.
 * Provides atomic single-use consumption primitives to prevent concurrent reuse races.
 */
@Repository
public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, UUID> {

    List<MfaRecoveryCode> findByUserMfaIdOrderByCodeIndexAsc(UUID userMfaId);

    List<MfaRecoveryCode> findByUserMfaIdAndUsedFalseOrderByCodeIndexAsc(UUID userMfaId);

    long countByUserMfaIdAndUsedFalse(UUID userMfaId);

    void deleteByUserMfaId(UUID userMfaId);

    /**
     * Atomically marks a recovery code as used if and only if it is currently unused.
     * Prevents race conditions where two simultaneous requests attempt to use the same code.
     *
     * @param id     Recovery code UUID
     * @param usedAt Timestamp of consumption
     * @return 1 if successfully marked as used, 0 if already used or non-existent
     */
    @Modifying
    @Query("UPDATE MfaRecoveryCode r SET r.used = true, r.usedAt = :usedAt, r.updatedAt = :usedAt WHERE r.id = :id AND r.used = false")
    int markUsedIfUnused(@Param("id") UUID id, @Param("usedAt") Instant usedAt);
}
