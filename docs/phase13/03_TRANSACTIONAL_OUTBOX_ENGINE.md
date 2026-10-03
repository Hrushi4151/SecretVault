# Transactional Outbox Engine

## 1. Motivation & Problem Statement

Dual-writing to a database and an external message queue or webhook provider within the same HTTP request is susceptible to partial failure:
- Database commit succeeds, but network failure aborts the event publication.
- Message is published, but the database transaction rolls back, producing ghost events.

The **Transactional Outbox Pattern** eliminates dual-write anomalies by writing domain events to an `event_outbox` table in the *same* database transaction as the business entity changes.

## 2. Table Schema (`event_outbox`)

```sql
CREATE TABLE event_outbox (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    schema_version INT NOT NULL DEFAULT 1,
    workspace_id UUID NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    locked_at TIMESTAMP WITH TIME ZONE,
    locked_by VARCHAR(100),
    last_error TEXT,
    processed_at TIMESTAMP WITH TIME ZONE,
    correlation_id VARCHAR(100),
    causation_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_outbox_status_available ON event_outbox(status, available_at);
CREATE INDEX idx_outbox_workspace_created ON event_outbox(workspace_id, created_at);
CREATE INDEX idx_outbox_event_id ON event_outbox(event_id);
```

## 3. Worker Polling & Locking

The outbox poller executes periodically:
1. Queries up to `batchSize` (default 50) rows where `status IN ('PENDING', 'FAILED')` and `available_at <= NOW()`.
2. Acquires row locks using distributed Redis leases or optimistic locking `(status = 'PROCESSING', locked_at = NOW(), locked_by = instanceId)`.
3. Stale locks (`locked_at < NOW() - 5m`) are automatically reclaimed to guarantee resilience across abrupt node terminations.
4. On dispatch success, status transitions to `PROCESSED`. On failure, status transitions to `FAILED` with exponential backoff, or `DEAD_LETTER` once max retry attempts (default 5) are exceeded.
