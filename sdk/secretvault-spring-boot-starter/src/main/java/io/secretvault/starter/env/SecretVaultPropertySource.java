package io.secretvault.starter.env;

import io.secretvault.sdk.api.SecretVaultClient;
import io.secretvault.sdk.exception.SecretNotFoundException;
import io.secretvault.sdk.model.SecretValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.PropertySource;

/**
 * Spring {@link PropertySource} that resolves {@code ${secretvault:KEY_NAME}} and {@code secretvault://KEY_NAME} placeholders.
 */
public class SecretVaultPropertySource extends PropertySource<SecretVaultClient> {

    public static final String PROPERTY_SOURCE_NAME = "secretVaultPropertySource";
    private static final Logger log = LoggerFactory.getLogger(SecretVaultPropertySource.class);

    private static final String PREFIX_COLON = "secretvault:";
    private static final String PREFIX_SLASHES = "secretvault://";

    public SecretVaultPropertySource(SecretVaultClient source) {
        super(PROPERTY_SOURCE_NAME, source);
    }

    @Override
    public Object getProperty(String name) {
        if (name == null) return null;

        String secretKey = null;
        if (name.startsWith(PREFIX_SLASHES)) {
            secretKey = name.substring(PREFIX_SLASHES.length());
        } else if (name.startsWith(PREFIX_COLON)) {
            secretKey = name.substring(PREFIX_COLON.length());
        }

        if (secretKey == null || secretKey.trim().isEmpty()) {
            return null;
        }

        try {
            SecretValue secretValue = source.secrets().get(secretKey.trim());
            return secretValue != null ? secretValue.value() : null;
        } catch (SecretNotFoundException e) {
            log.debug("Secret '{}' not found in SecretVault", secretKey);
            return null;
        } catch (Exception e) {
            log.warn("Error resolving secret '{}' from SecretVault: {}", secretKey, e.getMessage());
            return null;
        }
    }
}
