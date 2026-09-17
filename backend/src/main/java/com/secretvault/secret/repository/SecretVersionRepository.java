package com.secretvault.secret.repository;

import com.secretvault.secret.entity.SecretVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for SecretVersion entities.
 */
@Repository
public interface SecretVersionRepository extends JpaRepository<SecretVersion, UUID> {

    Optional<SecretVersion> findBySecretIdAndVersionNumber(UUID secretId, Integer versionNumber);

    List<SecretVersion> findBySecretIdOrderByVersionNumberDesc(UUID secretId);

    long countBySecretId(UUID secretId);
}
