package io.secretvault.sdk.exception;

public class SecretNotFoundException extends SecretVaultException {
    public SecretNotFoundException(String secretName) {
        super("Secret '" + secretName + "' not found in active scope", ErrorCode.SV_SECRET_NOT_FOUND, 404, null);
    }

    public SecretNotFoundException(String secretName, String requestId) {
        super("Secret '" + secretName + "' not found in active scope", ErrorCode.SV_SECRET_NOT_FOUND, 404, requestId);
    }
}
