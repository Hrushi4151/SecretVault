package com.secretvault.cli.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashMap;
import java.util.Map;

/**
 * Non-sensitive CLI configuration model.
 * NEVER contains plaintext credentials, passwords, or tokens.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CliConfig {

    private String defaultProfile = "default";
    private Map<String, ProfileConfig> profiles = new HashMap<>();
    private int timeoutSeconds = 30;

    public CliConfig() {
        profiles.put("default", new ProfileConfig("http://localhost:8080"));
    }

    public String getDefaultProfile() {
        return defaultProfile;
    }

    public void setDefaultProfile(String defaultProfile) {
        this.defaultProfile = defaultProfile;
    }

    public Map<String, ProfileConfig> getProfiles() {
        if (profiles == null) {
            profiles = new HashMap<>();
        }
        return profiles;
    }

    public void setProfiles(Map<String, ProfileConfig> profiles) {
        this.profiles = profiles;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public ProfileConfig getProfile(String name) {
        if (name == null || name.isBlank()) {
            name = defaultProfile;
        }
        return getProfiles().computeIfAbsent(name, ProfileConfig::new);
    }
}
