# Security Incident Lifecycle & Triage Operations

## 1. Overview

SecretVault provides native security incident operations for managing detected anomalies, unauthorized reveal spikes, rotation failures, and credential compromises.

## 2. Incident Status State Machine

```
   ┌────────┐        ┌──────────────┐        ┌───────────┐
   │  OPEN  │ ────►  │ INVESTIGATING│ ────►  │ CONTAINED │
   └────────┘        └──────────────┘        └─────┬─────┘
                                                   │
                                                   ▼
   ┌────────┐        ┌──────────────┐        ┌───────────┐
   │ CLOSED │ ◄────  │   RESOLVED   │ ◄────  │REMEDIATION│
   └────────┘        └──────────────┘        └───────────┘
```

- **`OPEN`:** Incident created (manually by operator or automatically by security policy).
- **`INVESTIGATING`:** An analyst has begun triage and root-cause analysis.
- **`CONTAINED`:** Active containment measures applied (e.g. lease revoked, machine token suspended).
- **`REMEDIATION`:** Permanent fix in progress (e.g. emergency secret rotation, consumer credential rollout).
- **`RESOLVED`:** Verification complete and service confirmed healthy.
- **`CLOSED`:** Post-mortem documented and incident archived.

## 3. Incident Schema (`security_incidents`)

```sql
CREATE TABLE security_incidents (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    incident_number VARCHAR(50) NOT NULL UNIQUE,
    severity VARCHAR(20) NOT NULL,
    category VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    title VARCHAR(255) NOT NULL,
    description TEXT,
    source_event_id UUID,
    assignee_id UUID,
    resolution_summary TEXT,
    resolved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```
