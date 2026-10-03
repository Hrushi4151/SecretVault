package com.secretvault.rotation.service;

import com.secretvault.access.model.AccessDecision;
import com.secretvault.access.model.AccessPermission;
import com.secretvault.access.service.EffectiveAccessService;
import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.environment.entity.Environment;
import com.secretvault.environment.repository.EnvironmentRepository;
import com.secretvault.machine.entity.MachineIdentity;
import com.secretvault.machine.model.MachineStatus;
import com.secretvault.machine.repository.MachineIdentityRepository;
import com.secretvault.rotation.dto.RotationDtos.*;
import com.secretvault.rotation.entity.SecretLease;
import com.secretvault.rotation.model.LeaseStatus;
import com.secretvault.rotation.repository.SecretLeaseRepository;
import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.repository.SecretRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service managing Runtime Secret Leases, Dynamic TTLs, Renewal Checks, and Revocation.
 */
@Service
public class SecretLeaseService {

    private static final Logger log = LoggerFactory.getLogger(SecretLeaseService.class);

    private final SecretLeaseRepository leaseRepository;
    private final SecretRepository secretRepository;
    private final EnvironmentRepository environmentRepository;
    private final MachineIdentityRepository machineRepository;
    private final EffectiveAccessService effectiveAccessService;
    private final AuditService auditService;

    public SecretLeaseService(
            SecretLeaseRepository leaseRepository,
            SecretRepository secretRepository,
            EnvironmentRepository environmentRepository,
            MachineIdentityRepository machineRepository,
            EffectiveAccessService effectiveAccessService,
            AuditService auditService) {
        this.leaseRepository = leaseRepository;
        this.secretRepository = secretRepository;
        this.environmentRepository = environmentRepository;
        this.machineRepository = machineRepository;
        this.effectiveAccessService = effectiveAccessService;
        this.auditService = auditService;
    }

    @Transactional
    public SecretLeaseResponse createLease(UUID workspaceId, CreateSecretLeaseRequest req, UUID actorId) {
        Secret secret = secretRepository.findById(req.secretId())
                .orElseThrow(() -> ApiException.notFound("Secret not found"));

        Environment env = environmentRepository.findById(secret.getEnvironmentId())
                .orElseThrow(() -> ApiException.notFound("Environment not found"));

        // Verify caller access via EffectiveAccessService
        verifyAccess(workspaceId, env.getProjectId(), secret.getEnvironmentId(), secret.getId(), actorId, AccessPermission.SECRET_READ);

        // Verify machine identity if specified
        if (req.machineIdentityId() != null) {
            MachineIdentity machine = machineRepository.findById(req.machineIdentityId())
                    .orElseThrow(() -> ApiException.notFound("Machine identity not found"));
            if (machine.getStatus() != MachineStatus.ACTIVE) {
                throw ApiException.forbidden("Machine identity is not active (" + machine.getStatus() + ")");
            }
        }

        long ttlSeconds = req.ttlSeconds() > 0 ? req.ttlSeconds() : 3600L; // Default 1 hour
        long maxLifetimeSeconds = req.maxLifetimeSeconds() > 0 ? req.maxLifetimeSeconds() : 86400L; // Default 24 hours
        if (ttlSeconds > maxLifetimeSeconds) {
            ttlSeconds = maxLifetimeSeconds;
        }

        SecretLease lease = new SecretLease();
        lease.setId(UUID.randomUUID());
        lease.setWorkspaceId(workspaceId);
        lease.setProjectId(env.getProjectId());
        lease.setEnvironmentId(secret.getEnvironmentId());
        lease.setSecretId(secret.getId());
        lease.setMachineIdentityId(req.machineIdentityId());
        lease.setUserId(actorId);
        lease.setConsumerId(req.consumerId());
        lease.setSecretVersionNumber(secret.getCurrentVersionNumber());
        lease.setStatus(LeaseStatus.ACTIVE);
        lease.setTtlSeconds(ttlSeconds);
        lease.setMaxLifetimeSeconds(maxLifetimeSeconds);
        lease.setIssuedAt(Instant.now());
        lease.setExpiresAt(Instant.now().plus(Duration.ofSeconds(ttlSeconds)));
        lease.setIpAddress(req.ipAddress());
        lease.setUserAgent(req.userAgent());

        lease = leaseRepository.save(lease);
        auditService.recordSecretAudit(null, workspaceId, actorId, AuditAction.LEASE_CREATED, secret.getId(), null, null, "SUCCESS");

        log.info("Issued secret lease {} for secret {} (expires at {})", lease.getId(), secret.getId(), lease.getExpiresAt());
        return SecretLeaseResponse.fromEntity(lease);
    }

    @Transactional(readOnly = true)
    public SecretLeaseResponse getLease(UUID workspaceId, UUID leaseId, UUID actorId) {
        SecretLease lease = leaseRepository.findById(leaseId)
                .orElseThrow(() -> ApiException.notFound("Secret lease not found"));

        verifyAccess(workspaceId, lease.getProjectId(), lease.getEnvironmentId(), lease.getSecretId(), actorId, AccessPermission.SECRET_LEASE_READ);
        return SecretLeaseResponse.fromEntity(lease);
    }

    @Transactional(readOnly = true)
    public Page<SecretLeaseResponse> listLeases(UUID workspaceId, UUID secretId, LeaseStatus status, Pageable pageable, UUID actorId) {
        if (secretId != null) {
            Secret secret = secretRepository.findById(secretId).orElseThrow(() -> ApiException.notFound("Secret not found"));
            Environment env = environmentRepository.findById(secret.getEnvironmentId()).orElseThrow(() -> ApiException.notFound("Environment not found"));
            verifyAccess(workspaceId, env.getProjectId(), secret.getEnvironmentId(), secret.getId(), actorId, AccessPermission.SECRET_LEASE_READ);
            return leaseRepository.findBySecretId(secretId, pageable).map(SecretLeaseResponse::fromEntity);
        }

        verifyAccess(workspaceId, null, null, null, actorId, AccessPermission.SECRET_LEASE_READ);
        return leaseRepository.findByWorkspaceId(workspaceId, pageable).map(SecretLeaseResponse::fromEntity);
    }

    @Transactional
    public SecretLeaseResponse renewLease(UUID workspaceId, UUID leaseId, RenewSecretLeaseRequest req, UUID actorId) {
        SecretLease lease = leaseRepository.findById(leaseId)
                .orElseThrow(() -> ApiException.notFound("Secret lease not found"));

        if (!lease.getWorkspaceId().equals(workspaceId)) {
            throw ApiException.forbidden("Lease does not belong to this workspace");
        }

        if (lease.getStatus() != LeaseStatus.ACTIVE) {
            throw ApiException.badRequest("Cannot renew lease with status " + lease.getStatus());
        }

        // Verify machine identity is still valid
        if (lease.getMachineIdentityId() != null) {
            MachineIdentity machine = machineRepository.findById(lease.getMachineIdentityId()).orElse(null);
            if (machine == null || machine.getStatus() != MachineStatus.ACTIVE) {
                lease.setStatus(LeaseStatus.REVOKED);
                lease.setRevokedAt(Instant.now());
                leaseRepository.save(lease);
                throw ApiException.forbidden("Associated machine identity is no longer active; lease revoked");
            }
        }

        // Verify permission grant still valid
        verifyAccess(workspaceId, lease.getProjectId(), lease.getEnvironmentId(), lease.getSecretId(), actorId, AccessPermission.SECRET_READ);

        Instant maxAllowedExpiration = lease.getIssuedAt().plus(Duration.ofSeconds(lease.getMaxLifetimeSeconds()));
        Instant now = Instant.now();
        if (now.isAfter(maxAllowedExpiration)) {
            lease.setStatus(LeaseStatus.EXPIRED);
            leaseRepository.save(lease);
            throw ApiException.badRequest("Lease has reached its absolute maximum lifetime");
        }

        long extendSeconds = (req != null && req.extendSeconds() > 0) ? req.extendSeconds() : lease.getTtlSeconds();
        Instant proposedExpiration = now.plus(Duration.ofSeconds(extendSeconds));
        if (proposedExpiration.isAfter(maxAllowedExpiration)) {
            proposedExpiration = maxAllowedExpiration;
        }

        lease.setExpiresAt(proposedExpiration);
        lease.setLastRenewedAt(now);
        lease = leaseRepository.save(lease);

        auditService.recordSecretAudit(null, workspaceId, actorId, AuditAction.LEASE_RENEWED, lease.getSecretId(), null, null, "SUCCESS");
        log.info("Renewed secret lease {} until {}", lease.getId(), lease.getExpiresAt());
        return SecretLeaseResponse.fromEntity(lease);
    }

    @Transactional
    public void revokeLease(UUID workspaceId, UUID leaseId, UUID actorId) {
        SecretLease lease = leaseRepository.findById(leaseId)
                .orElseThrow(() -> ApiException.notFound("Secret lease not found"));

        verifyAccess(workspaceId, lease.getProjectId(), lease.getEnvironmentId(), lease.getSecretId(), actorId, AccessPermission.SECRET_LEASE_MANAGE);

        lease.setStatus(LeaseStatus.REVOKED);
        lease.setRevokedAt(Instant.now());
        lease.setRevokedBy(actorId);
        leaseRepository.save(lease);

        auditService.recordSecretAudit(null, workspaceId, actorId, AuditAction.LEASE_REVOKED, lease.getSecretId(), null, null, "SUCCESS");
        log.info("Revoked secret lease {}", lease.getId());
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void expireOverdueLeases() {
        Instant now = Instant.now();
        List<SecretLease> expired = leaseRepository.findByStatusAndExpiresAtLessThanEqual(LeaseStatus.ACTIVE, now);
        for (SecretLease lease : expired) {
            lease.setStatus(LeaseStatus.EXPIRED);
            leaseRepository.save(lease);
            log.debug("Marked lease {} as EXPIRED", lease.getId());
        }
    }

    private void verifyAccess(UUID workspaceId, UUID projectId, UUID environmentId, UUID secretId, UUID actorId, AccessPermission permission) {
        if (actorId == null) return;
        AccessDecision decision = effectiveAccessService.evaluateAccess(workspaceId, projectId, environmentId, secretId, permission, actorId);
        if (!decision.allowed()) {
            throw ApiException.forbidden("Access denied: missing " + permission.getCode());
        }
    }
}
