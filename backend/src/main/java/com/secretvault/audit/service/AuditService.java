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
 * Service managing non-repudiable append-only security audit trails.
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

    @Transactional(readOnly = true)
    public List<AuditLog> getWorkspaceAuditLogs(UUID workspaceId) {
        return auditLogRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getSecretAuditLogs(UUID secretId) {
        return auditLogRepository.findByResourceTypeAndResourceIdOrderByCreatedAtDesc("SECRET", secretId);
    }
}
