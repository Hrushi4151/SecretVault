package com.secretvault.repository.service;

import com.secretvault.secret.entity.Secret;
import com.secretvault.secret.entity.SecretVersion;
import com.secretvault.secret.repository.SecretRepository;
import com.secretvault.secret.repository.SecretVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Safely correlates detected secrets with SecretVault secret inventory using
 * cryptographic SHA-256 fingerprints.
 * Plaintext secrets are NEVER decrypted or exposed during correlation.
 */
@Component
public class SecretInventoryCorrelator {

    private static final Logger log = LoggerFactory.getLogger(SecretInventoryCorrelator.class);

    private final SecretVersionRepository secretVersionRepository;
    private final SecretRepository secretRepository;

    public SecretInventoryCorrelator(
            SecretVersionRepository secretVersionRepository,
            SecretRepository secretRepository) {
        this.secretVersionRepository = secretVersionRepository;
        this.secretRepository = secretRepository;
    }

    public record CorrelationResult(
            boolean isMatched,
            UUID secretId,
            String secretName,
            UUID environmentId,
            String message
    ) {
        public static CorrelationResult noMatch() {
            return new CorrelationResult(false, null, null, null, "No matching SecretVault secret found");
        }
    }

    public CorrelationResult correlateByFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return CorrelationResult.noMatch();
        }

        Optional<SecretVersion> matchedVersionOpt = secretVersionRepository.findFirstByFingerprint(fingerprint);
        if (matchedVersionOpt.isPresent()) {
            SecretVersion version = matchedVersionOpt.get();
            Optional<Secret> secretOpt = secretRepository.findById(version.getSecretId());
            if (secretOpt.isPresent()) {
                Secret secret = secretOpt.get();
                log.info("Correlated exposed finding fingerprint {} to SecretVault secret id {}", fingerprint, secret.getId());
                return new CorrelationResult(
                        true,
                        secret.getId(),
                        secret.getName(),
                        secret.getEnvironmentId(),
                        "Match confirmed with SecretVault secret: " + secret.getName()
                );
            }
        }

        return CorrelationResult.noMatch();
    }
}
