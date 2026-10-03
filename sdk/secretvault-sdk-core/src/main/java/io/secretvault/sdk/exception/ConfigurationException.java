package io.secretvault.sdk.exception;

public class ConfigurationException extends SecretVaultException {
    public ConfigurationException(String message) {
        super(message, ErrorCode.SV_CONFIGURATION_INVALID);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, ErrorCode.SV_CONFIGURATION_INVALID, cause);
    }
}
