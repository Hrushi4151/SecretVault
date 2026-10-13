package com.secretvault.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative canonical resource reference for AI Copilot.
 * Ensures model text cannot inject arbitrary or malicious external URLs.
 * Frontend router uses navigationTarget and resourceType to navigate safely.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiResourceReference(
        String resourceType,
        String resourceId,
        UUID workspaceId,
        UUID projectId,
        UUID environmentId,
        String displayName,
        String title,
        Map<String, Object> safeMetadata,
        String navigationTarget,
        String route,
        String severity,
        String status
) {
    public AiResourceReference(
            String resourceType,
            String resourceId,
            UUID workspaceId,
            String displayName,
            Map<String, Object> safeMetadata,
            String navigationTarget,
            String severity,
            String status
    ) {
        this(
                resourceType,
                resourceId,
                workspaceId,
                null,
                null,
                displayName,
                displayName,
                safeMetadata != null ? safeMetadata : Map.of(),
                navigationTarget,
                navigationTarget,
                severity,
                status
        );
    }

    public static AiResourceReference invitation(
            UUID invitationId,
            UUID workspaceId,
            String workspaceName,
            String role,
            String inviterName,
            String status,
            String expiresAt
    ) {
        return new AiResourceReference(
                "INVITATION",
                invitationId != null ? invitationId.toString() : "",
                workspaceId,
                null,
                null,
                "Invitation: " + workspaceName,
                "Invitation: " + workspaceName,
                Map.of(
                        "workspaceName", workspaceName != null ? workspaceName : "",
                        "role", role != null ? role : "MEMBER",
                        "inviter", inviterName != null ? inviterName : "Administrator",
                        "status", status != null ? status : "PENDING",
                        "expiresAt", expiresAt != null ? expiresAt : ""
                ),
                "/workspaces/invitations",
                "/workspaces/invitations",
                "INFO",
                status != null ? status : "PENDING"
        );
    }

    public static AiResourceReference project(
            UUID projectId,
            UUID workspaceId,
            String projectName,
            String slug,
            int environmentCount,
            int secretCount
    ) {
        return new AiResourceReference(
                "PROJECT",
                projectId != null ? projectId.toString() : "",
                workspaceId,
                projectId,
                null,
                projectName,
                projectName,
                Map.of(
                        "slug", slug != null ? slug : "",
                        "environmentCount", environmentCount,
                        "secretCount", secretCount
                ),
                "/projects/" + (projectId != null ? projectId.toString() : ""),
                "/projects/" + (projectId != null ? projectId.toString() : ""),
                null,
                "ACTIVE"
        );
    }

    public static AiResourceReference secret(
            UUID secretId,
            UUID workspaceId,
            UUID projectId,
            UUID environmentId,
            String secretKey,
            int currentVersion,
            String rotationStatus
    ) {
        return new AiResourceReference(
                "SECRET",
                secretId != null ? secretId.toString() : "",
                workspaceId,
                projectId,
                environmentId,
                secretKey,
                secretKey,
                Map.of(
                        "version", currentVersion,
                        "rotationStatus", rotationStatus != null ? rotationStatus : "COMPLIANT"
                ),
                "/secrets?project=" + (projectId != null ? projectId.toString() : "") + "&secret=" + (secretId != null ? secretId.toString() : ""),
                "/secrets?project=" + (projectId != null ? projectId.toString() : "") + "&secret=" + (secretId != null ? secretId.toString() : ""),
                "COMPLIANT".equalsIgnoreCase(rotationStatus) ? "LOW" : "HIGH",
                rotationStatus != null ? rotationStatus : "ACTIVE"
        );
    }

    public static AiResourceReference finding(
            UUID findingId,
            UUID workspaceId,
            String title,
            String severity,
            String category,
            String status
    ) {
        return new AiResourceReference(
                "FINDING",
                findingId != null ? findingId.toString() : "",
                workspaceId,
                null,
                null,
                title,
                title,
                Map.of(
                        "category", category != null ? category : "SECURITY",
                        "status", status != null ? status : "OPEN"
                ),
                "/repo-security?findingId=" + (findingId != null ? findingId.toString() : ""),
                "/repo-security?findingId=" + (findingId != null ? findingId.toString() : ""),
                severity != null ? severity : "HIGH",
                status != null ? status : "OPEN"
        );
    }

    public static AiResourceReference rotation(
            UUID rotationId,
            UUID workspaceId,
            String secretKey,
            String status,
            String nextRotationDue
    ) {
        return new AiResourceReference(
                "ROTATION",
                rotationId != null ? rotationId.toString() : "",
                workspaceId,
                null,
                null,
                "Rotation: " + (secretKey != null ? secretKey : "Credential"),
                "Rotation: " + (secretKey != null ? secretKey : "Credential"),
                Map.of(
                        "secretKey", secretKey != null ? secretKey : "",
                        "status", status != null ? status : "SCHEDULED",
                        "nextDue", nextRotationDue != null ? nextRotationDue : ""
                ),
                "/rotation",
                "/rotation",
                "OVERDUE".equalsIgnoreCase(status) ? "HIGH" : "INFO",
                status != null ? status : "SCHEDULED"
        );
    }

    public static AiResourceReference syncJob(
            UUID jobId,
            UUID workspaceId,
            String providerType,
            String status,
            String lastSyncAt
    ) {
        return new AiResourceReference(
                "SYNC",
                jobId != null ? jobId.toString() : "",
                workspaceId,
                null,
                null,
                "Sync: " + (providerType != null ? providerType : "Cloud Provider"),
                "Sync: " + (providerType != null ? providerType : "Cloud Provider"),
                Map.of(
                        "provider", providerType != null ? providerType : "EXTERNAL",
                        "status", status != null ? status : "IN_SYNC",
                        "lastSyncAt", lastSyncAt != null ? lastSyncAt : ""
                ),
                "/sync-center",
                "/sync-center",
                "FAILED".equalsIgnoreCase(status) ? "HIGH" : "LOW",
                status != null ? status : "IN_SYNC"
        );
    }
}
