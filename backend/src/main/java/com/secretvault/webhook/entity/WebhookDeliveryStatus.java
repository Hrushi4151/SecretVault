package com.secretvault.webhook.entity;

public enum WebhookDeliveryStatus {
    PENDING,
    SUCCESS,
    FAILED,
    DEAD_LETTER,
    BLOCKED_SSRF
}
