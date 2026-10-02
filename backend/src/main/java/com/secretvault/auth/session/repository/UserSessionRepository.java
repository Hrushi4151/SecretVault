package com.secretvault.auth.session.repository;

import com.secretvault.auth.session.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findBySessionIdentifier(String sessionIdentifier);

    Optional<UserSession> findBySessionIdentifierAndUserId(String sessionIdentifier, UUID userId);

    List<UserSession> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<UserSession> findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(UUID userId, Instant now);

    @Query("SELECT s FROM UserSession s WHERE s.userId = :userId AND s.sessionIdentifier <> :currentSessionIdentifier AND s.revokedAt IS NULL")
    List<UserSession> findOtherActiveSessions(
            @Param("userId") UUID userId,
            @Param("currentSessionIdentifier") String currentSessionIdentifier
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserSession s SET s.revokedAt = :revokedAt, s.revocationReason = :reason WHERE s.userId = :userId AND s.revokedAt IS NULL")
    int revokeAllByUserId(
            @Param("userId") UUID userId,
            @Param("revokedAt") Instant revokedAt,
            @Param("reason") String reason
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserSession s SET s.revokedAt = :revokedAt, s.revocationReason = :reason WHERE s.userId = :userId AND s.sessionIdentifier <> :currentSessionIdentifier AND s.revokedAt IS NULL")
    int revokeAllOthersByUserId(
            @Param("userId") UUID userId,
            @Param("currentSessionIdentifier") String currentSessionIdentifier,
            @Param("revokedAt") Instant revokedAt,
            @Param("reason") String reason
    );
}
