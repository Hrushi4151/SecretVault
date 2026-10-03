package com.secretvault.rotation.repository;

import com.secretvault.rotation.entity.SecretConsumer;
import com.secretvault.rotation.model.ConsumerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecretConsumerRepository extends JpaRepository<SecretConsumer, UUID> {

    List<SecretConsumer> findByWorkspaceId(UUID workspaceId);

    Page<SecretConsumer> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    Optional<SecretConsumer> findByWorkspaceIdAndName(UUID workspaceId, String name);

    List<SecretConsumer> findByEnvironmentId(UUID environmentId);

    Optional<SecretConsumer> findByEnvironmentIdAndNameAndInstanceId(UUID environmentId, String name, String instanceId);

    @Query("SELECT c FROM SecretConsumer c WHERE c.status = 'ACTIVE' AND c.lastHeartbeatAt < :threshold")
    List<SecretConsumer> findStaleConsumers(@Param("threshold") Instant threshold);

    @Query("SELECT COUNT(c) FROM SecretConsumer c WHERE c.workspaceId = :workspaceId AND c.status = :status")
    long countByWorkspaceIdAndStatus(@Param("workspaceId") UUID workspaceId, @Param("status") ConsumerStatus status);
}
