package com.secretvault.audit.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.entity.AuditLog;
import com.secretvault.audit.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service managing append-only security audit trails.
 * Guaranteed never to log or persist secret plaintext, keys, tokens, or credentials.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Appends an audit log entry.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditLog recordAudit(
            UUID organizationId,
            UUID workspaceId,
            UUID actorId,
            String actorType,
            AuditAction action,
            String resourceType,
            UUID resourceId,
            String requestId,
            String ipAddress,
            String outcome
    ) {
        AuditLog auditLog = new AuditLog(
                organizationId,
                workspaceId,
                actorId,
                actorType != null ? actorType : "USER",
                action,
                resourceType,
                resourceId,
                requestId,
                ipAddress,
                outcome != null ? outcome : "SUCCESS"
        );

        AuditLog saved = auditLogRepository.save(auditLog);
        log.info("Audit logged: action=[{}] resourceType=[{}] resourceId=[{}] actor=[{}] workspace=[{}] outcome=[{}]",
                action, resourceType, resourceId, actorId, workspaceId, outcome);
        return saved;
    }

    /**
     * Convenience method for secret-related audit logging.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditLog recordSecretAudit(
            UUID organizationId,
            UUID workspaceId,
            UUID actorId,
            AuditAction action,
            UUID secretId,
            String requestId,
            String ipAddress,
            String outcome
    ) {
        return recordAudit(
                organizationId,
                workspaceId,
                actorId,
                "USER",
                action,
                "SECRET",
                secretId,
                requestId,
                ipAddress,
                outcome
        );
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public AuditLog logSuccess(
            AuditAction action,
            String resourceType,
            UUID resourceId,
            UUID actorId,
            UUID workspaceId,
            String description
    ) {
        return recordAudit(null, workspaceId, actorId, "USER", action, resourceType, resourceId, null, null, "SUCCESS");
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public AuditLog record(
            UUID workspaceId,
            UUID actorId,
            AuditAction action,
            String resourceType,
            String resourceIdentifier,
            java.util.Map<String, ?> metadata
    ) {
        UUID resourceUuid = null;
        try {
            if (resourceIdentifier != null && !resourceIdentifier.isBlank()) {
                resourceUuid = UUID.fromString(resourceIdentifier);
            }
        } catch (IllegalArgumentException ignored) {}

        if (resourceUuid == null) {
            String seed = (resourceType != null ? resourceType : "RESOURCE") + ":" + (resourceIdentifier != null ? resourceIdentifier : UUID.randomUUID().toString());
            resourceUuid = UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        return recordAudit(null, workspaceId, actorId, "USER", action, resourceType, resourceUuid, null, null, "SUCCESS");
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getWorkspaceAuditLogs(UUID workspaceId) {
        return getWorkspaceAuditLogs(workspaceId, 100);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getWorkspaceAuditLogs(UUID workspaceId, int limit) {
        int boundedLimit = Math.max(1, Math.min(500, limit));
        return auditLogRepository.findByWorkspaceIdOrderByCreatedAtDesc(
                workspaceId,
                org.springframework.data.domain.PageRequest.of(0, boundedLimit)
        ).getContent();
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<AuditLog> searchAuditLogs(
            UUID workspaceId,
            AuditAction action,
            String outcome,
            String resourceType,
            UUID actorId,
            String search,
            java.time.Instant from,
            java.time.Instant to,
            org.springframework.data.domain.Pageable pageable
    ) {
        return auditLogRepository.searchAuditLogs(
                workspaceId, action, outcome, resourceType, actorId, search, from, to, pageable
        );
    }

    @Transactional(readOnly = true)
    public List<AuditLog> exportAuditLogs(
            UUID workspaceId,
            AuditAction action,
            String outcome,
            String resourceType,
            UUID actorId,
            String search,
            java.time.Instant from,
            java.time.Instant to,
            int maxLimit
    ) {
        return auditLogRepository.exportAuditLogs(
                workspaceId, action, outcome, resourceType, actorId, search, from, to, maxLimit
        );
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getSecretAuditLogs(UUID secretId) {
        return auditLogRepository.findByResourceTypeAndResourceIdOrderByCreatedAtDesc("SECRET", secretId);
    }
}
