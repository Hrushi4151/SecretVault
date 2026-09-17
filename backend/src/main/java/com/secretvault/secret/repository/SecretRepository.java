package com.secretvault.secret.repository;

import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Secret entities.
 */
@Repository
public interface SecretRepository extends JpaRepository<Secret, UUID> {

    Optional<Secret> findByIdAndEnvironmentId(UUID id, UUID environmentId);

    Optional<Secret> findByEnvironmentIdAndName(UUID environmentId, String name);

    boolean existsByEnvironmentIdAndName(UUID environmentId, String name);

    boolean existsByEnvironmentIdAndNameAndIdNot(UUID environmentId, String name, UUID id);

    List<Secret> findByEnvironmentId(UUID environmentId);

    List<Secret> findByEnvironmentIdAndStatus(UUID environmentId, SecretStatus status);

    List<Secret> findByEnvironmentIdAndStatusNot(UUID environmentId, SecretStatus status);
}
