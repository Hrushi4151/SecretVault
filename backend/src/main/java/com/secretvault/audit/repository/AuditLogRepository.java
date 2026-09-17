package com.secretvault.audit.repository;

import com.secretvault.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Append-only repository for querying security and lifecycle audit trails.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    List<AuditLog> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(String resourceType, UUID resourceId);

    List<AuditLog> findByActorIdOrderByCreatedAtDesc(UUID actorId);
}
