package com.secretvault.cli.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Local directory/project context metadata stored in .secretvault/project.json.
 * NEVER contains sensitive credentials or secret values.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectLocalConfig {

    private String workspaceId;
    private String workspaceSlug;
    private String projectId;
    private String projectSlug;
    private String environmentId;
    private String environmentSlug;
    private String server;

    public ProjectLocalConfig() {}

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

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }
}
