package com.secretvault.rotation.repository;

import com.secretvault.rotation.entity.SecretDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecretDependencyRepository extends JpaRepository<SecretDependency, UUID> {

    List<SecretDependency> findBySecretId(UUID secretId);

    List<SecretDependency> findByConsumerId(UUID consumerId);

    Optional<SecretDependency> findByConsumerIdAndSecretId(UUID consumerId, UUID secretId);

    @Query("SELECT d FROM SecretDependency d WHERE d.secretId = :secretId")
    List<SecretDependency> findAllBySecretId(@Param("secretId") UUID secretId);
}
