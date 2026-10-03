package com.secretvault.rotation.repository;

import com.secretvault.rotation.entity.RotationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RotationAttemptRepository extends JpaRepository<RotationAttempt, UUID> {
    List<RotationAttempt> findByJobIdOrderByAttemptNumberAsc(UUID jobId);
}
