package com.secretvault.secret.repository;

import com.secretvault.secret.entity.BranchStatus;
import com.secretvault.secret.entity.SecretBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecretBranchRepository extends JpaRepository<SecretBranch, UUID> {

    List<SecretBranch> findBySecretId(UUID secretId);

    List<SecretBranch> findBySecretIdAndStatus(UUID secretId, BranchStatus status);

    Optional<SecretBranch> findByIdAndSecretId(UUID id, UUID secretId);

    Optional<SecretBranch> findBySecretIdAndName(UUID secretId, String name);

    boolean existsBySecretIdAndName(UUID secretId, String name);
}
