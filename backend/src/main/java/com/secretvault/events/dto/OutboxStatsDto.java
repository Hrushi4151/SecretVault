package com.secretvault.events.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutboxStatsDto(
        long pendingCount,
        long processingCount,
        long failedCount,
        long deadLetterCount,
        long processedCount,
        Long oldestPendingAgeSeconds
) {}
