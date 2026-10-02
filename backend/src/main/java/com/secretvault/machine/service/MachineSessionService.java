package com.secretvault.machine.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.common.exception.ApiException;
import com.secretvault.machine.dto.MachineDtos;
import com.secretvault.machine.entity.MachineIdentity;
import com.secretvault.machine.entity.MachineSession;
import com.secretvault.machine.model.MachineStatus;
import com.secretvault.machine.repository.MachineIdentityRepository;
import com.secretvault.machine.repository.MachineSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class MachineSessionService {

    private static final Logger log = LoggerFactory.getLogger(MachineSessionService.class);
    private static final String MACHINE_TOKEN_PREFIX = "sv_machine_";

    private final MachineSessionRepository sessionRepository;
    private final MachineIdentityRepository machineRepository;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public record MachineTokenResult(
            String rawToken,
            String tokenPrefix,
            Instant expiresAt,
            long expiresInSeconds,
            MachineIdentity machineIdentity
    ) {}

    public record MachineSessionContext(
            MachineIdentity machineIdentity,
            MachineSession session
    ) {}

    public MachineSessionService(
            MachineSessionRepository sessionRepository,
            MachineIdentityRepository machineRepository,
            AuditService auditService
    ) {
        this.sessionRepository = sessionRepository;
        this.machineRepository = machineRepository;
        this.auditService = auditService;
    }

    /**
     * Issues a high-entropy, short-lived opaque machine token and records the hashed session.
     */
    @Transactional
    public MachineTokenResult createSession(
            UUID workspaceId,
            UUID machineIdentityId,
            UUID oidcProviderId,
            String sourceIp,
            String userAgent,
            Map<String, Object> metadata,
            long ttlSeconds
    ) {
        MachineIdentity machine = machineRepository.findByIdAndWorkspaceIdAndDeletedAtIsNull(machineIdentityId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Machine identity not found"));

        if (!machine.isUsable(Instant.now())) {
            throw ApiException.forbidden("Cannot issue token: machine identity is " + machine.getStatus());
        }

        // Bound TTL between 60 seconds and 3600 seconds (1 hour max)
        long effectiveTtl = Math.max(60, Math.min(ttlSeconds, 3600));
        Instant expiresAt = Instant.now().plusSeconds(effectiveTtl);

        // If machine identity itself has an earlier expiresAt, cap token expiration
        if (machine.getExpiresAt() != null && machine.getExpiresAt().isBefore(expiresAt)) {
            expiresAt = machine.getExpiresAt();
        }

        // Generate 32 bytes cryptographically secure random token
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String tokenSuffix = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String rawToken = MACHINE_TOKEN_PREFIX + tokenSuffix;

        String tokenHash = hashToken(rawToken);
        String tokenPrefix = rawToken.substring(0, Math.min(rawToken.length(), 18)) + "...";

        MachineSession session = new MachineSession(
                workspaceId,
                machineIdentityId,
                oidcProviderId,
                tokenHash,
                tokenPrefix,
                expiresAt,
                sourceIp,
                userAgent,
                metadata
        );

        sessionRepository.save(session);

        machine.setLastAuthenticatedAt(Instant.now());
        machineRepository.save(machine);

        auditService.recordAudit(
                null,
                workspaceId,
                machineIdentityId,
                "MACHINE_IDENTITY",
                AuditAction.MACHINE_TOKEN_ISSUED,
                "MACHINE_SESSION",
                session.getId(),
                null,
                sourceIp,
                "SUCCESS"
        );

        log.info("Issued machine token for [{}] in workspace [{}] (Session ID: {}, TTL: {}s)",
                machine.getName(), workspaceId, session.getId(), effectiveTtl);

        return new MachineTokenResult(rawToken, tokenPrefix, expiresAt, effectiveTtl, machine);
    }

    /**
     * Validates an incoming machine token against database session records and active machine status.
     */
    @Transactional
    public Optional<MachineSessionContext> validateToken(String rawToken) {
        if (rawToken == null || !rawToken.startsWith(MACHINE_TOKEN_PREFIX)) {
            return Optional.empty();
        }

        String tokenHash = hashToken(rawToken);
        Optional<MachineSession> sessionOpt = sessionRepository.findByTokenHash(tokenHash);
        if (sessionOpt.isEmpty()) {
            return Optional.empty();
        }

        MachineSession session = sessionOpt.get();
        Instant now = Instant.now();

        if (!session.isValid(now)) {
            log.debug("Machine session [{}] is expired or revoked", session.getId());
            return Optional.empty();
        }

        Optional<MachineIdentity> machineOpt = machineRepository.findByIdAndDeletedAtIsNull(session.getMachineIdentityId());
        if (machineOpt.isEmpty()) {
            return Optional.empty();
        }

        MachineIdentity machine = machineOpt.get();
        if (!machine.isUsable(now)) {
            log.warn("Machine identity [{}] is not usable (status: {})", machine.getName(), machine.getStatus());
            return Optional.empty();
        }

        // Update lastUsedAt
        session.setLastUsedAt(now);
        machine.setLastUsedAt(now);
        sessionRepository.save(session);
        machineRepository.save(machine);

        return Optional.of(new MachineSessionContext(machine, session));
    }

    @Transactional(readOnly = true)
    public List<MachineDtos.MachineSessionResponse> listSessions(UUID workspaceId, UUID machineIdentityId) {
        if (!machineRepository.existsByIdAndWorkspaceIdAndDeletedAtIsNull(machineIdentityId, workspaceId)) {
            throw ApiException.notFound("Machine identity not found");
        }

        Instant now = Instant.now();
        return sessionRepository.findByWorkspaceIdAndMachineIdentityIdOrderByIssuedAtDesc(workspaceId, machineIdentityId)
                .stream()
                .map(s -> new MachineDtos.MachineSessionResponse(
                        s.getId(),
                        s.getWorkspaceId(),
                        s.getMachineIdentityId(),
                        s.getOidcProviderId(),
                        s.getTokenPrefix(),
                        s.getIssuedAt(),
                        s.getExpiresAt(),
                        s.getRevokedAt(),
                        s.getLastUsedAt(),
                        s.getSourceIp(),
                        s.getUserAgent(),
                        s.isValid(now)
                ))
                .toList();
    }

    @Transactional
    public void revokeSession(UUID workspaceId, UUID machineIdentityId, UUID sessionId, UUID actorId) {
        MachineSession session = sessionRepository.findByIdAndWorkspaceId(sessionId, workspaceId)
                .orElseThrow(() -> ApiException.notFound("Machine session not found"));

        if (!session.getMachineIdentityId().equals(machineIdentityId)) {
            throw ApiException.badRequest("Session does not belong to specified machine identity");
        }

        session.setRevokedAt(Instant.now());
        sessionRepository.save(session);

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.MACHINE_TOKEN_REVOKED,
                "MACHINE_SESSION",
                sessionId,
                null,
                null,
                "SUCCESS"
        );

        log.info("Revoked machine session [{}] for machine ID [{}]", sessionId, machineIdentityId);
    }

    @Transactional
    public int revokeAllSessions(UUID workspaceId, UUID machineIdentityId, UUID actorId) {
        if (!machineRepository.existsByIdAndWorkspaceIdAndDeletedAtIsNull(machineIdentityId, workspaceId)) {
            throw ApiException.notFound("Machine identity not found");
        }

        int count = sessionRepository.revokeAllByWorkspaceAndMachine(workspaceId, machineIdentityId, Instant.now());

        auditService.recordAudit(
                null,
                workspaceId,
                actorId,
                "USER",
                AuditAction.MACHINE_TOKEN_REVOKED,
                "MACHINE_IDENTITY",
                machineIdentityId,
                null,
                null,
                "SUCCESS (revoked " + count + " sessions)"
        );

        log.info("Revoked {} machine sessions for machine ID [{}] in workspace [{}]", count, machineIdentityId, workspaceId);
        return count;
    }

    public static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable", e);
        }
    }
}
