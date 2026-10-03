# Webhook Governance Platform & Architecture

## 1. Overview

SecretVault provides an enterprise webhook delivery platform allowing external SIEMs, CI/CD systems, and ticketing tools to receive real-time notifications of secret lifecycle events.

Key features:
- Per-workspace webhook endpoint registration.
- Fine-grained event filtering (e.g. subscribe only to `ROTATION_*` and `SECRET_COMPROMISED`).
- Cryptographic request signing (HMAC-SHA256).
- Strict Server-Side Request Forgery (SSRF) and DNS rebinding defenses.
- Delivery attempt ledger with manual and automated replay capabilities.

## 2. Table Schemas

### 2.1 Webhook Endpoints (`webhook_endpoints`)
```sql
CREATE TABLE webhook_endpoints (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    destination_url VARCHAR(1024) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    subscribed_events_json TEXT,
    signing_secret_encrypted TEXT NOT NULL,
    secret_prefix VARCHAR(10),
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_success_at TIMESTAMP WITH TIME ZONE,
    last_failure_at TIMESTAMP WITH TIME ZONE
);
```

### 2.2 Webhook Deliveries (`webhook_deliveries`)
```sql
CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    webhook_id UUID NOT NULL,
    event_id UUID NOT NULL,
    attempt INT NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    http_status INT,
    duration_ms BIGINT,
    error_message TEXT,
    payload_digest VARCHAR(64),
    next_retry_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```
