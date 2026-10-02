package com.secretvault.auth.mfa.service;

import com.secretvault.audit.entity.AuditAction;
import com.secretvault.audit.service.AuditService;
import com.secretvault.auth.entity.User;
import com.secretvault.auth.mfa.entity.MfaRecoveryCode;
import com.secretvault.auth.mfa.entity.MfaStatus;
import com.secretvault.auth.mfa.entity.UserMfa;
import com.secretvault.auth.mfa.model.AuthenticationState;
import com.secretvault.auth.mfa.model.MfaActivationResult;
import com.secretvault.auth.mfa.model.MfaChallengeInfo;
import com.secretvault.auth.mfa.model.MfaChallengePayload;
import com.secretvault.auth.mfa.model.MfaEnrollmentResponse;
import com.secretvault.auth.mfa.model.MfaStatusInfo;
import com.secretvault.auth.mfa.model.MfaVerificationResult;
import com.secretvault.auth.mfa.recovery.RecoveryCodeService;
import com.secretvault.auth.mfa.repository.MfaRecoveryCodeRepository;
import com.secretvault.auth.mfa.repository.UserMfaRepository;
import com.secretvault.auth.mfa.totp.Base32;
import com.secretvault.auth.mfa.totp.TotpService;
import com.secretvault.auth.repository.UserRepository;
import com.secretvault.common.exception.ApiException;
import com.secretvault.common.security.state.SecurityStateStore;
import com.secretvault.encryption.model.EncryptedPayload;
import com.secretvault.encryption.service.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of MfaService.
 * Coordinates cryptographic TOTP engine, PostgreSQL persistence, Redis SecurityStateStore,
 * envelope encryption, and audit event streams.
 */
@Service
public class DefaultMfaService implements MfaService {

    private static final Logger log = LoggerFactory.getLogger(DefaultMfaService.class);

    private static final String CATEGORY_MFA_CHALLENGE = "mfa_challenge";
    private static final String PURPOSE_LOGIN_MFA = "LOGIN_MFA";
    private static final long CHALLENGE_TTL_SECONDS = 300L;
    private static final int MAX_ATTEMPTS = 5;
    private static final int RECOVERY_CODE_COUNT = 10;

    private final UserMfaRepository userMfaRepository;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;
    private final UserRepository userRepository;
    private final TotpService totpService;
    private final RecoveryCodeService recoveryCodeService;
    private final EncryptionService encryptionService;
    private final SecurityStateStore securityStateStore;
    private final AuditService auditService;

    public DefaultMfaService(
            UserMfaRepository userMfaRepository,
            MfaRecoveryCodeRepository recoveryCodeRepository,
            UserRepository userRepository,
            TotpService totpService,
            RecoveryCodeService recoveryCodeService,
            EncryptionService encryptionService,
            SecurityStateStore securityStateStore,
            AuditService auditService
    ) {
        this.userMfaRepository = Objects.requireNonNull(userMfaRepository, "UserMfaRepository must not be null");
        this.recoveryCodeRepository = Objects.requireNonNull(recoveryCodeRepository, "MfaRecoveryCodeRepository must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "UserRepository must not be null");
        this.totpService = Objects.requireNonNull(totpService, "TotpService must not be null");
        this.recoveryCodeService = Objects.requireNonNull(recoveryCodeService, "RecoveryCodeService must not be null");
        this.encryptionService = Objects.requireNonNull(encryptionService, "EncryptionService must not be null");
        this.securityStateStore = Objects.requireNonNull(securityStateStore, "SecurityStateStore must not be null");
        this.auditService = Objects.requireNonNull(auditService, "AuditService must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isMfaEnabled(UUID userId) {
        if (userId == null) return false;
        return userMfaRepository.findByUserId(userId)
                .map(UserMfa::isEnabled)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public MfaStatusInfo getStatus(UUID userId) {
        if (userId == null) return MfaStatusInfo.disabled();
        return userMfaRepository.findByUserId(userId)
                .map(mfa -> {
                    long remainingCodes = mfa.isEnabled()
                            ? recoveryCodeRepository.countByUserMfaIdAndUsedFalse(mfa.getId())
                            : 0;
                    return new MfaStatusInfo(
                            mfa.isEnabled(),
                            mfa.getStatus(),
                            mfa.getEnrolledAt(),
                            mfa.getVerifiedAt(),
                            mfa.getLastUsedAt(),
                            remainingCodes
                    );
                })
                .orElseGet(MfaStatusInfo::disabled);
    }

    @Override
    @Transactional
    public MfaEnrollmentResponse beginEnrollment(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        Optional<UserMfa> existingMfa = userMfaRepository.findByUserId(userId);
        if (existingMfa.isPresent() && existingMfa.get().isEnabled()) {
            throw ApiException.conflict("MFA is already enabled for this account");
        }

        // 1. Generate high-entropy TOTP Base32 secret
        String base32Secret = totpService.generateSecret();
        byte[] rawSecretBytes = Base32.decode(base32Secret);

        // 2. Encrypt TOTP secret via AES-256-GCM envelope encryption bound to user MFA context
        String aad = "user-mfa:" + userId;
        EncryptedPayload encryptedPayload = encryptionService.encrypt(rawSecretBytes, aad);

        // 3. Persist or update PENDING_VERIFICATION state in PostgreSQL
        UserMfa userMfa;
        if (existingMfa.isPresent()) {
            userMfa = existingMfa.get();
            userMfa.updateEncryptedPayload(encryptedPayload);
            userMfa.setStatus(MfaStatus.PENDING_VERIFICATION);
            userMfa.setEnrolledAt(Instant.now());
            userMfa.setVerifiedAt(null);
            userMfa.resetFailedAttempts();
            recoveryCodeRepository.deleteByUserMfaId(userMfa.getId());
        } else {
            userMfa = new UserMfa(userId, encryptedPayload);
        }
        userMfa = userMfaRepository.save(userMfa);

        // 4. Construct RFC 6238 Key URI for QR provisioning
        String provisioningUri = totpService.buildProvisioningUri(base32Secret, user.getEmail());

        auditService.recordAudit(
                null,
                null,
                userId,
                "USER",
                AuditAction.MFA_ENROLLMENT_STARTED,
                "USER_MFA",
                userMfa.getId(),
                null,
                null,
                "SUCCESS"
        );

        return new MfaEnrollmentResponse(
                base32Secret,
                provisioningUri,
                totpService.getProperties().getIssuer(),
                user.getEmail()
        );
    }

    @Override
    @Transactional
    public MfaActivationResult activateMfa(UUID userId, String verificationCode) {
        Objects.requireNonNull(userId, "userId must not be null");
        if (verificationCode == null || verificationCode.isBlank()) {
            throw ApiException.badRequest("Verification code is required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        UserMfa userMfa = userMfaRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.badRequest("No pending MFA enrollment found for user"));

        if (userMfa.getStatus() != MfaStatus.PENDING_VERIFICATION) {
            if (userMfa.getStatus() == MfaStatus.ENABLED) {
                throw ApiException.conflict("MFA is already enabled");
            }
            throw ApiException.badRequest("MFA enrollment is not pending verification");
        }

        // 1. Decrypt TOTP secret in memory
        String aad = "user-mfa:" + userId;
        byte[] rawSecretBytes;
        try {
            rawSecretBytes = encryptionService.decrypt(userMfa.toEncryptedPayload(), aad);
        } catch (Exception ex) {
            log.error("Failed to decrypt MFA envelope during activation for user [{}]", userId);
            throw ApiException.internal("MFA_DECRYPTION_ERROR", "Failed to decrypt MFA secret", ex);
        }

        String base32Secret = Base32.encode(rawSecretBytes, false);

        // 2. Verify candidate code against decrypted secret
        boolean valid = totpService.verifyCode(base32Secret, verificationCode);
        if (!valid) {
            userMfa.incrementFailedAttempts();
            userMfaRepository.save(userMfa);
            auditService.recordAudit(null, null, userId, "USER", AuditAction.MFA_VERIFICATION_FAILED, "USER_MFA", userMfa.getId(), null, null, "INVALID_CODE");
            throw ApiException.badRequest("Invalid verification code");
        }

        // 3. Mark UserMfa and User as actively ENABLED
        userMfa.enable();
        userMfaRepository.save(userMfa);

        user.setMfaEnabled(true);
        userRepository.save(user);

        // 4. Generate and persist one-way hashed recovery codes batch
        recoveryCodeRepository.deleteByUserMfaId(userMfa.getId());
        List<String> plaintextCodes = recoveryCodeService.generateCodes(RECOVERY_CODE_COUNT);
        for (int i = 0; i < plaintextCodes.size(); i++) {
            String code = plaintextCodes.get(i);
            String hash = recoveryCodeService.hash(code);
            MfaRecoveryCode entity = new MfaRecoveryCode(userMfa.getId(), hash, i);
            recoveryCodeRepository.save(entity);
        }

        auditService.recordAudit(null, null, userId, "USER", AuditAction.MFA_ACTIVATED, "USER_MFA", userMfa.getId(), null, null, "SUCCESS");

        return new MfaActivationResult(MfaStatus.ENABLED, plaintextCodes);
    }

    @Override
    public MfaChallengeInfo createLoginChallenge(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        if (!isMfaEnabled(userId)) {
            throw ApiException.badRequest("MFA is not enabled for this user");
        }

        String challengeId = UUID.randomUUID().toString();
        MfaChallengePayload payload = MfaChallengePayload.of(
                challengeId,
                userId,
                PURPOSE_LOGIN_MFA,
                CHALLENGE_TTL_SECONDS,
                MAX_ATTEMPTS
        );

        try {
            securityStateStore.put(CATEGORY_MFA_CHALLENGE, challengeId, payload, Duration.ofSeconds(CHALLENGE_TTL_SECONDS));
        } catch (Exception ex) {
            log.error("Redis failure storing MFA login challenge for user [{}]", userId);
            throw ApiException.internal("MFA_SERVICE_UNAVAILABLE", "Failed to create MFA login challenge", ex);
        }

        auditService.recordAudit(null, null, userId, "USER", AuditAction.MFA_CHALLENGE_ISSUED, "MFA_CHALLENGE", userId, challengeId, null, "SUCCESS");

        return new MfaChallengeInfo(challengeId, userId, payload.expiresAt(), AuthenticationState.MFA_REQUIRED);
    }

    @Override
    @Transactional
    public MfaVerificationResult verifyLoginTotp(String challengeId, UUID userId, String code) {
        if (challengeId == null || challengeId.isBlank() || userId == null || code == null || code.isBlank()) {
            return MfaVerificationResult.failed(AuthenticationState.AUTHENTICATION_FAILED, "Missing challenge parameters or verification code");
        }

        Optional<MfaChallengePayload> optChallenge;
        try {
            optChallenge = securityStateStore.get(CATEGORY_MFA_CHALLENGE, challengeId, MfaChallengePayload.class);
        } catch (Exception ex) {
            log.error("Redis failure during MFA challenge lookup for challenge [{}]", challengeId);
            throw ApiException.internal("MFA_SERVICE_UNAVAILABLE", "MFA service temporarily unavailable", ex);
        }

        if (optChallenge.isEmpty()) {
            return MfaVerificationResult.failed(AuthenticationState.MFA_CHALLENGE_EXPIRED, "MFA challenge expired or not found");
        }

        MfaChallengePayload challenge = optChallenge.get();

        // 1. Validate user and purpose binding
        if (!challenge.userId().equals(userId)) {
            handleFailedAttempt(challengeId, userId, challenge.maxAttempts());
            auditService.recordAudit(null, null, userId, "USER", AuditAction.MFA_VERIFICATION_FAILED, "MFA_CHALLENGE", userId, challengeId, null, "USER_MISMATCH");
            return MfaVerificationResult.failed(AuthenticationState.AUTHENTICATION_FAILED, "MFA challenge does not match authenticated user");
        }

        if (!PURPOSE_LOGIN_MFA.equals(challenge.purpose())) {
            return MfaVerificationResult.failed(AuthenticationState.AUTHENTICATION_FAILED, "Invalid MFA challenge purpose");
        }

        // 2. Load active MFA configuration
        UserMfa userMfa = userMfaRepository.findByUserId(userId).orElse(null);
        if (userMfa == null || !userMfa.isEnabled()) {
            return MfaVerificationResult.failed(AuthenticationState.AUTHENTICATION_FAILED, "MFA is not enabled for this user");
        }

        // 3. Decrypt TOTP secret in memory
        String aad = "user-mfa:" + userId;
        byte[] rawSecretBytes;
        try {
            rawSecretBytes = encryptionService.decrypt(userMfa.toEncryptedPayload(), aad);
        } catch (Exception ex) {
            log.error("Failed to decrypt TOTP secret during verification for user [{}]", userId);
            return MfaVerificationResult.failed(AuthenticationState.AUTHENTICATION_FAILED, "Failed to decrypt MFA secret");
        }

        String base32Secret = Base32.encode(rawSecretBytes, false);

        // 4. Verify TOTP code
        boolean valid = totpService.verifyCode(base32Secret, code);
        if (!valid) {
            userMfa.incrementFailedAttempts();
            userMfaRepository.save(userMfa);
            auditService.recordAudit(null, null, userId, "USER", AuditAction.MFA_VERIFICATION_FAILED, "USER_MFA", userMfa.getId(), challengeId, null, "INVALID_CODE");
            boolean locked = handleFailedAttempt(challengeId, userId, challenge.maxAttempts());
            if (locked) {
                return MfaVerificationResult.failed(AuthenticationState.MFA_CHALLENGE_LOCKED, "Maximum MFA attempts exceeded. Challenge locked.");
            }
            return MfaVerificationResult.failed(AuthenticationState.MFA_REQUIRED, "Invalid authentication code");
        }

        // 5. ATOMIC CONSUMPTION: single-use enforcement in Redis
        Optional<MfaChallengePayload> consumed = securityStateStore.consumeAtomic(CATEGORY_MFA_CHALLENGE, challengeId, MfaChallengePayload.class);
        if (consumed.isEmpty()) {
            return MfaVerificationResult.failed(AuthenticationState.MFA_CHALLENGE_EXPIRED, "MFA challenge already consumed or expired");
        }

        userMfa.recordSuccessfulUse(Instant.now());
        userMfaRepository.save(userMfa);

        auditService.recordAudit(null, null, userId, "USER", AuditAction.MFA_VERIFICATION_SUCCESS, "USER_MFA", userMfa.getId(), challengeId, null, "SUCCESS");

        return MfaVerificationResult.success(userId);
    }

    @Override
    @Transactional
    public MfaVerificationResult verifyLoginRecoveryCode(String challengeId, UUID userId, String recoveryCode) {
        if (challengeId == null || challengeId.isBlank() || userId == null || recoveryCode == null || recoveryCode.isBlank()) {
            return MfaVerificationResult.failed(AuthenticationState.AUTHENTICATION_FAILED, "Missing challenge parameters or recovery code");
        }

        Optional<MfaChallengePayload> optChallenge;
        try {
            optChallenge = securityStateStore.get(CATEGORY_MFA_CHALLENGE, challengeId, MfaChallengePayload.class);
        } catch (Exception ex) {
            log.error("Redis failure during MFA challenge lookup for challenge [{}]", challengeId);
            throw ApiException.internal("MFA_SERVICE_UNAVAILABLE", "MFA service temporarily unavailable", ex);
        }

        if (optChallenge.isEmpty()) {
            return MfaVerificationResult.failed(AuthenticationState.MFA_CHALLENGE_EXPIRED, "MFA challenge expired or not found");
        }

        MfaChallengePayload challenge = optChallenge.get();

        if (!challenge.userId().equals(userId)) {
            handleFailedAttempt(challengeId, userId, challenge.maxAttempts());
            auditService.recordAudit(null, null, userId, "USER", AuditAction.MFA_VERIFICATION_FAILED, "MFA_CHALLENGE", userId, challengeId, null, "USER_MISMATCH");
            return MfaVerificationResult.failed(AuthenticationState.AUTHENTICATION_FAILED, "MFA challenge does not match authenticated user");
        }

        if (!PURPOSE_LOGIN_MFA.equals(challenge.purpose())) {
            return MfaVerificationResult.failed(AuthenticationState.AUTHENTICATION_FAILED, "Invalid MFA challenge purpose");
        }

        UserMfa userMfa = userMfaRepository.findByUserId(userId).orElse(null);
        if (userMfa == null || !userMfa.isEnabled()) {
            return MfaVerificationResult.failed(AuthenticationState.AUTHENTICATION_FAILED, "MFA is not enabled for this user");
        }

        // 1. Search candidate unused recovery codes
        List<MfaRecoveryCode> unusedCodes = recoveryCodeRepository.findByUserMfaIdAndUsedFalseOrderByCodeIndexAsc(userMfa.getId());
        MfaRecoveryCode matchedCode = null;
        for (MfaRecoveryCode candidate : unusedCodes) {
            if (recoveryCodeService.matches(recoveryCode, candidate.getCodeHash())) {
                matchedCode = candidate;
                break;
            }
        }

        if (matchedCode == null) {
            userMfa.incrementFailedAttempts();
            userMfaRepository.save(userMfa);
            auditService.recordAudit(null, null, userId, "USER", AuditAction.MFA_VERIFICATION_FAILED, "USER_MFA", userMfa.getId(), challengeId, null, "INVALID_RECOVERY_CODE");
            boolean locked = handleFailedAttempt(challengeId, userId, challenge.maxAttempts());
            if (locked) {
                return MfaVerificationResult.failed(AuthenticationState.MFA_CHALLENGE_LOCKED, "Maximum MFA attempts exceeded. Challenge locked.");
            }
            return MfaVerificationResult.failed(AuthenticationState.MFA_REQUIRED, "Invalid recovery code");
        }

        // 2. Atomically consume recovery code in PostgreSQL
        int rowsUpdated = recoveryCodeRepository.markUsedIfUnused(matchedCode.getId(), Instant.now());
        if (rowsUpdated != 1) {
            return MfaVerificationResult.failed(AuthenticationState.MFA_REQUIRED, "Recovery code has already been consumed");
        }

        // 3. Atomically consume MFA challenge in Redis
        Optional<MfaChallengePayload> consumed = securityStateStore.consumeAtomic(CATEGORY_MFA_CHALLENGE, challengeId, MfaChallengePayload.class);
        if (consumed.isEmpty()) {
            return MfaVerificationResult.failed(AuthenticationState.MFA_CHALLENGE_EXPIRED, "MFA challenge already consumed or expired");
        }

        userMfa.recordSuccessfulUse(Instant.now());
        userMfaRepository.save(userMfa);

        auditService.recordAudit(null, null, userId, "USER", AuditAction.MFA_RECOVERY_CODE_USED, "MFA_RECOVERY_CODE", matchedCode.getId(), challengeId, null, "SUCCESS");
        auditService.recordAudit(null, null, userId, "USER", AuditAction.MFA_VERIFICATION_SUCCESS, "USER_MFA", userMfa.getId(), challengeId, null, "SUCCESS");

        return MfaVerificationResult.success(userId);
    }

    @Override
    @Transactional
    public MfaVerificationResult verifyLoginTotp(String challengeId, String code) {
        if (challengeId == null || challengeId.isBlank()) {
            return MfaVerificationResult.failed(AuthenticationState.AUTHENTICATION_FAILED, "Missing challenge ID");
        }
        Optional<MfaChallengePayload> optChallenge;
        try {
            optChallenge = securityStateStore.get(CATEGORY_MFA_CHALLENGE, challengeId, MfaChallengePayload.class);
        } catch (Exception ex) {
            log.error("Redis failure during challenge resolution for challenge [{}]", challengeId);
            throw ApiException.internal("MFA_SERVICE_UNAVAILABLE", "MFA service temporarily unavailable", ex);
        }
        if (optChallenge.isEmpty()) {
            return MfaVerificationResult.failed(AuthenticationState.MFA_CHALLENGE_EXPIRED, "MFA challenge expired or not found");
        }
        return verifyLoginTotp(challengeId, optChallenge.get().userId(), code);
    }

    @Override
    @Transactional
    public MfaVerificationResult verifyLoginRecoveryCode(String challengeId, String recoveryCode) {
        if (challengeId == null || challengeId.isBlank()) {
            return MfaVerificationResult.failed(AuthenticationState.AUTHENTICATION_FAILED, "Missing challenge ID");
        }
        Optional<MfaChallengePayload> optChallenge;
        try {
            optChallenge = securityStateStore.get(CATEGORY_MFA_CHALLENGE, challengeId, MfaChallengePayload.class);
        } catch (Exception ex) {
            log.error("Redis failure during challenge resolution for challenge [{}]", challengeId);
            throw ApiException.internal("MFA_SERVICE_UNAVAILABLE", "MFA service temporarily unavailable", ex);
        }
        if (optChallenge.isEmpty()) {
            return MfaVerificationResult.failed(AuthenticationState.MFA_CHALLENGE_EXPIRED, "MFA challenge expired or not found");
        }
        return verifyLoginRecoveryCode(challengeId, optChallenge.get().userId(), recoveryCode);
    }

    private boolean handleFailedAttempt(String challengeId, UUID userId, int maxAttempts) {
        long attemptCount = securityStateStore.incrementAttempts(CATEGORY_MFA_CHALLENGE, challengeId, Duration.ofSeconds(CHALLENGE_TTL_SECONDS));
        if (attemptCount >= maxAttempts) {
            securityStateStore.delete(CATEGORY_MFA_CHALLENGE, challengeId);
            auditService.recordAudit(null, null, userId, "USER", AuditAction.MFA_CHALLENGE_LOCKED, "MFA_CHALLENGE", userId, challengeId, null, "MAX_ATTEMPTS_EXCEEDED");
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public void disableMfa(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        UserMfa userMfa = userMfaRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.badRequest("MFA is not configured for user"));

        if (!userMfa.isEnabled()) {
            throw ApiException.badRequest("MFA is not currently enabled");
        }

        recoveryCodeRepository.deleteByUserMfaId(userMfa.getId());
        userMfa.disable();
        userMfaRepository.save(userMfa);

        user.setMfaEnabled(false);
        userRepository.save(user);

        auditService.recordAudit(null, null, userId, "USER", AuditAction.MFA_DISABLED, "USER_MFA", userMfa.getId(), null, null, "SUCCESS");
    }
}
