package com.secretvault.auth.repository;

import com.secretvault.auth.entity.RegistrationVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegistrationVerificationRepository extends JpaRepository<RegistrationVerification, UUID> {
    Optional<RegistrationVerification> findByEmail(String email);
    void deleteByEmail(String email);
}
