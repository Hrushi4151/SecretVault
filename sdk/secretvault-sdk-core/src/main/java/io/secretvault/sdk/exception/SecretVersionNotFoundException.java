package io.secretvault.sdk.exception;

public class SecretVersionNotFoundException extends SecretVaultException {
    public SecretVersionNotFoundException(String secretName, int versionNumber) {
        super("Secret '" + secretName + "' version " + versionNumber + " not found", ErrorCode.SV_SECRET_VERSION_NOT_FOUND, 404, null);
    }

    public SecretVersionNotFoundException(String secretName, int versionNumber, String requestId) {
        super("Secret '" + secretName + "' version " + versionNumber + " not found", ErrorCode.SV_SECRET_VERSION_NOT_FOUND, 404, requestId);
    }
}
