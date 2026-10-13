package com.secretvault.auth.repository;

import com.secretvault.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByResetToken(String resetToken);
    Optional<PasswordResetToken> findByEmail(String email);
    void deleteByUserId(UUID userId);
    void deleteByEmail(String email);
}
