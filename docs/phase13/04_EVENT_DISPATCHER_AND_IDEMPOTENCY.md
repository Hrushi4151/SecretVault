# Event Dispatcher & Idempotency Engine

## 1. At-Least-Once Delivery & Handlers

The `EventDispatcher` routes claimed outbox events to registered `DomainEventHandler` implementations:
- `AuditEventHandler`: Appends immutable audit log entries.
- `AutomationEventHandler`: Evaluates automation policies and triggers actions.
- `WebhookEventHandler`: Dispatches signed outbound HTTP webhooks.
- `NotificationEventHandler`: Delivers in-app alerts and multi-channel messages.
- `SecurityIncidentEventHandler`: Correlates security anomalies into incidents.

## 2. Idempotency Log (`event_processing_log`)

Because network partitions and retries may deliver an event multiple times, consumers maintain an idempotency ledger:

```sql
CREATE TABLE event_processing_log (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 1,
    error TEXT,
    correlation_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_event_consumer UNIQUE (event_id, consumer_name)
);
```

Before executing side-effects, the consumer attempts an atomic write to `event_processing_log`. If a row already exists, duplicate execution is rejected immediately, guaranteeing exactly-once side-effect semantics.

## 3. Retry Strategy & Dead-Lettering

Failed deliveries back off exponentially:
$$\text{delay} = \min(\text{maxDelay}, \text{initialDelay} \times 2^{\text{attempt}}) \pm \text{jitter}$$

Events that fail 5 consecutive times transition to `DEAD_LETTER` status, creating a `SECURITY_FINDING_CREATED` event and notifying system administrators.
