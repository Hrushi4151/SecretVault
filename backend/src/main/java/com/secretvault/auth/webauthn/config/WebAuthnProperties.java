package com.secretvault.auth.webauthn.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for WebAuthn / FIDO2 Relying Party and Passkey policies.
 */
@Configuration
@ConfigurationProperties(prefix = "secretvault.webauthn")
public class WebAuthnProperties {

    private String rpId = "localhost";
    private String rpName = "SecretVault";
    private List<String> allowedOrigins = new ArrayList<>(List.of(
            "http://localhost:5173",
            "http://localhost:3000",
            "http://localhost:8080",
            "https://app.secretvault.dev"
    ));
    private long challengeTtlSeconds = 300L;
    private long timeoutSeconds = 60L;
    private String userVerification = "PREFERRED";
    private boolean allowOriginPort = true;
    private String cloneDetectionAction = "BLOCK";

    public String getRpId() {
        return rpId;
    }

    public void setRpId(String rpId) {
        this.rpId = rpId;
    }

    public String getRpName() {
        return rpName;
    }

    public void setRpName(String rpName) {
        this.rpName = rpName;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public long getChallengeTtlSeconds() {
        return challengeTtlSeconds;
    }

    public void setChallengeTtlSeconds(long challengeTtlSeconds) {
        this.challengeTtlSeconds = challengeTtlSeconds;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getUserVerification() {
        return userVerification;
    }

    public void setUserVerification(String userVerification) {
        this.userVerification = userVerification;
    }

    public boolean isAllowOriginPort() {
        return allowOriginPort;
    }

    public void setAllowOriginPort(boolean allowOriginPort) {
        this.allowOriginPort = allowOriginPort;
    }

    public String getCloneDetectionAction() {
        return cloneDetectionAction;
    }

    public void setCloneDetectionAction(String cloneDetectionAction) {
        this.cloneDetectionAction = cloneDetectionAction;
    }
}
