package com.secretvault.secret.repository;

import com.secretvault.secret.entity.SecretVersionTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecretVersionTagRepository extends JpaRepository<SecretVersionTag, UUID> {

    List<SecretVersionTag> findBySecretVersionId(UUID secretVersionId);

    List<SecretVersionTag> findBySecretVersionIdIn(Collection<UUID> secretVersionIds);

    Optional<SecretVersionTag> findBySecretVersionIdAndName(UUID secretVersionId, String name);

    boolean existsBySecretVersionIdAndName(UUID secretVersionId, String name);

    void deleteBySecretVersionIdAndName(UUID secretVersionId, String name);
}
