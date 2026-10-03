package com.secretvault.rotation.provider;

import com.secretvault.rotation.entity.RotationPolicy;
import com.secretvault.rotation.model.SecretType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Registry holding all available SecretRotator implementations and selecting
 * the most specialized rotator for a given secret type and policy.
 */
@Component
public class SecretRotatorRegistry {

    private static final Logger log = LoggerFactory.getLogger(SecretRotatorRegistry.class);
    private final List<SecretRotator> rotators;

    public SecretRotatorRegistry(List<SecretRotator> rotators) {
        this.rotators = rotators;
    }

    /**
     * Resolves the appropriate rotator for the given secret type and rotation policy.
     */
    public SecretRotator getRotator(SecretType type, RotationPolicy policy) {
        for (SecretRotator rotator : rotators) {
            if (rotator.supports(type, policy)) {
                log.debug("Selected rotator {} for type {} and policy {}", rotator.getClass().getSimpleName(), type, policy != null ? policy.getId() : "null");
                return rotator;
            }
        }
        throw new IllegalStateException("No registered SecretRotator supports secret type: " + type);
    }
}
