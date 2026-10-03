package com.secretvault.rotation.repository;

import com.secretvault.rotation.entity.RotationValidation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RotationValidationRepository extends JpaRepository<RotationValidation, UUID> {
    List<RotationValidation> findByJobIdOrderByCreatedAtDesc(UUID jobId);
}
