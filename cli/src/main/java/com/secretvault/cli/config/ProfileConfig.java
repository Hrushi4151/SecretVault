package com.secretvault.cli.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Profile-specific non-sensitive configuration settings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProfileConfig {

    private String server = "http://localhost:8080";
    private String workspaceId;
    private String workspaceSlug;
    private String projectId;
    private String projectSlug;
    private String environmentId;
    private String environmentSlug;
    private String outputFormat = "human";

    public ProfileConfig() {
    }

    public ProfileConfig(String server) {
        this.server = server;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getWorkspaceSlug() {
        return workspaceSlug;
    }

    public void setWorkspaceSlug(String workspaceSlug) {
        this.workspaceSlug = workspaceSlug;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getProjectSlug() {
        return projectSlug;
    }

    public void setProjectSlug(String projectSlug) {
        this.projectSlug = projectSlug;
    }

    public String getEnvironmentId() {
        return environmentId;
    }

    public void setEnvironmentId(String environmentId) {
        this.environmentId = environmentId;
    }

    public String getEnvironmentSlug() {
        return environmentSlug;
    }

    public void setEnvironmentSlug(String environmentSlug) {
        this.environmentSlug = environmentSlug;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }
}
