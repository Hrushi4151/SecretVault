package com.secretvault.rotation.provider;

import com.secretvault.rotation.engine.SecretGenerationEngine;
import com.secretvault.rotation.entity.RotationJob;
import com.secretvault.rotation.entity.RotationPolicy;
import com.secretvault.rotation.model.SecretType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Standard rotator for cryptographic credentials (passwords, tokens, API keys, random strings, SSH keys).
 */
@Component
@Order(100) // Lower precedence than specific custom rotators
public class DefaultCryptoRotator implements SecretRotator {

    private static final Logger log = LoggerFactory.getLogger(DefaultCryptoRotator.class);
    private final SecretGenerationEngine generationEngine;

    public DefaultCryptoRotator(SecretGenerationEngine generationEngine) {
        this.generationEngine = generationEngine;
    }

    @Override
    public boolean supports(SecretType type, RotationPolicy policy) {
        return type == SecretType.PASSWORD
                || type == SecretType.RANDOM_STRING
                || type == SecretType.RANDOM_BYTES
                || type == SecretType.TOKEN
                || type == SecretType.SSH_KEY
                || type == SecretType.API_KEY
                || type == SecretType.CERTIFICATE
                || type == SecretType.CUSTOM;
    }

    @Override
    public String generate(RotationPolicy policy, RotationJob job) {
        SecretType type = policy != null && policy.getSecretType() != null ? policy.getSecretType() : SecretType.PASSWORD;
        String configJson = policy != null ? policy.getSecretGeneratorConfig() : null;
        log.info("Generating new secret using DefaultCryptoRotator for policy {} (type: {})", policy != null ? policy.getId() : "N/A", type);
        return generationEngine.generateSecret(type, configJson);
    }

    @Override
    public boolean validate(String newPlaintext, RotationPolicy policy, RotationJob job) {
        if (newPlaintext == null || newPlaintext.trim().isEmpty()) {
            log.warn("Generated secret validation failed: empty or null");
            return false;
        }
        return true;
    }
}
