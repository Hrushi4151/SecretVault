package com.secretvault.secret.repository;

import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.entity.VersionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Optional<SecretVersion> findByIdAndSecretId(UUID id, UUID secretId);

    Optional<SecretVersion> findBySecretIdAndVersionNumber(UUID secretId, Integer versionNumber);

    List<SecretVersion> findBySecretIdOrderByVersionNumberDesc(UUID secretId);

    Page<SecretVersion> findBySecretId(UUID secretId, Pageable pageable);

    Page<SecretVersion> findBySecretIdAndVersionType(UUID secretId, VersionType versionType, Pageable pageable);

    List<SecretVersion> findBySecretIdAndBranchIdOrderByVersionNumberDesc(UUID secretId, UUID branchId);

    Optional<SecretVersion> findTopBySecretIdOrderByVersionNumberDesc(UUID secretId);

    long countBySecretId(UUID secretId);
}
