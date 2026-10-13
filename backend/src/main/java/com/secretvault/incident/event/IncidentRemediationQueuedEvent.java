package com.secretvault.incident.event;

import java.util.UUID;

/**
 * Internal Spring application event published after a remediation execution
 * has been successfully committed with status QUEUED.
 */
public record IncidentRemediationQueuedEvent(
        UUID executionId,
        UUID workspaceId,
        UUID incidentId
) {}
