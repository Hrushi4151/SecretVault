package com.secretvault.webhook.entity;

public enum WebhookDeliveryStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    DEAD_LETTER,
    BLOCKED_SSRF
}
