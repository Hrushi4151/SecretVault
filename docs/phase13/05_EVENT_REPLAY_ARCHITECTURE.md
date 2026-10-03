# Event Replay Architecture & Point-in-Time Auditing

## 1. Overview

SecretVault provides on-demand replay capabilities for disaster recovery, downstream consumer recovery, audit reconstruction, and policy testing.

Replays are requested via:
`POST /api/v1/workspaces/{workspaceId}/event-replays`
or CLI:
`secretvault events replay --type ROTATION_FAILED --reexecute=false`

## 2. Replay Scopes & Filters

Users can replay events matching specific filters:
- Single Target Event (`targetEventId`)
- Event Type Filter (`eventTypeFilter`, e.g. `SECRET_*`, `ROTATION_*`)
- Aggregate Scope (`aggregateType`, `aggregateId`)
- Time Range (`fromTimestamp`, `toTimestamp`)
- Downstream Side-Effect Execution Flag (`reexecuteSideEffects`):
  - `false` (default): Audits and inspects historical event stream without re-triggering webhooks or rotations.
  - `true`: Bypasses consumer deduplication locks and executes side-effects for disaster recovery.

## 3. Multi-Tenant Scoping & Security

Event replay requests enforce multi-tenant isolation:
1. Replays are restricted to events belonging to the requesting actor's workspace.
2. Cross-workspace event IDs passed to the replay endpoint are rejected with `404 Not Found`.
3. Replay requests are audited in `AuditService` under `EVENT_REPLAY_REQUESTED`.
