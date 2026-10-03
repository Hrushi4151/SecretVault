package com.secretvault.cli.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Phase13CliDtos {

    // Domain Events & Outbox
    public record OutboxEventDto(
            UUID id,
            UUID eventId,
            String eventType,
            int schemaVersion,
            UUID workspaceId,
            String aggregateType,
            String aggregateId,
            String payload,
            String status,
            int attemptCount,
            String lastError,
            Instant occurredAt,
            Instant processedAt,
            Instant createdAt
    ) {}

    public record EventReplayDto(
            UUID id,
            UUID workspaceId,
            UUID targetEventId,
            String eventTypeFilter,
            String aggregateType,
            String aggregateId,
            Instant fromTimestamp,
            Instant toTimestamp,
            boolean reexecuteSideEffects,
            String status,
            int totalEvents,
            int replayedEvents,
            int failedEvents,
            String errorMessage,
            Instant createdAt,
            Instant completedAt
    ) {}

    public record CreateReplayRequest(
            UUID targetEventId,
            String eventTypeFilter,
            String aggregateType,
            String aggregateId,
            Instant fromTimestamp,
            Instant toTimestamp,
            boolean reexecuteSideEffects
    ) {}

    // Automation Engine
    public record AutomationPolicyDto(
            UUID id,
            UUID workspaceId,
            String name,
            String description,
            boolean enabled,
            int priority,
            String scopeType,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            String triggerEventTypes,
            String conditionsJson,
            String actionsJson,
            boolean dryRun,
            boolean approvalRequired,
            int policyVersion,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CreateAutomationPolicyRequest(
            String name,
            String description,
            boolean enabled,
            int priority,
            String scopeType,
            UUID projectId,
            UUID environmentId,
            UUID secretId,
            String triggerEventTypes,
            String conditionsJson,
            String actionsJson,
            boolean dryRun,
            boolean approvalRequired
    ) {}

    public record AutomationApprovalDto(
            UUID id,
            UUID workspaceId,
            UUID executionId,
            UUID policyId,
            String actionType,
            String actionPayloadJson,
            String status,
            UUID requestedBy,
            UUID decidedBy,
            String rejectionReason,
            Instant expiresAt,
            Instant decidedAt,
            Instant createdAt
    ) {}

    public record DecideApprovalRequest(
            boolean approve,
            String rejectionReason
    ) {}

    public record AutomationExecutionDto(
            UUID id,
            UUID workspaceId,
            UUID policyId,
            int policyVersion,
            UUID eventId,
            String triggerEventType,
            String status,
            boolean dryRun,
            String evaluationResultJson,
            String actionsExecutedJson,
            String errorMessage,
            long durationMs,
            Instant createdAt
    ) {}

    // Webhook Governance
    public record WebhookEndpointDto(
            UUID id,
            UUID workspaceId,
            String name,
            String destinationUrl,
            boolean enabled,
            List<String> subscribedEvents,
            String secretPrefix,
            Instant lastSuccessAt,
            Instant lastFailureAt,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CreateWebhookRequest(
            String name,
            String destinationUrl,
            List<String> subscribedEvents,
            boolean enabled
    ) {}

    public record WebhookDeliveryDto(
            UUID id,
            UUID workspaceId,
            UUID webhookId,
            UUID eventId,
            int attempt,
            String status,
            Integer httpStatus,
            Long durationMs,
            String errorMessage,
            Instant deliveredAt,
            Instant createdAt
    ) {}

    // Incident Operations
    public record SecurityIncidentDto(
            UUID id,
            UUID workspaceId,
            String incidentNumber,
            String severity,
            String category,
            String status,
            String title,
            String description,
            UUID sourceEventId,
            UUID assigneeId,
            String resolutionSummary,
            Instant resolvedAt,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CreateIncidentRequest(
            String title,
            String description,
            String severity,
            String category,
            UUID sourceEventId
    ) {}

    public record UpdateIncidentStatusRequest(
            String status,
            String resolutionSummary
    ) {}

    // Notifications
    public record NotificationDto(
            UUID id,
            UUID workspaceId,
            UUID recipientId,
            String severity,
            String title,
            String message,
            UUID eventId,
            String channel,
            String status,
            Instant createdAt,
            Instant readAt,
            Instant acknowledgedAt
    ) {}
}
