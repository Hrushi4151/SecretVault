# Event Correlation Engine & Anomaly Detection

## 1. Multi-Signal Correlation

Isolated events often do not indicate an active attack or critical outage on their own. The **Event Correlation Engine** identifies clustered patterns across time and resources:

```
[ROTATION_FAILED] ──┐
[CONSUMER_STALE]  ──┼──► Correlation Window (5 min) ──► Cluster into SEC-INC-402
[LEASE_REVOKED]   ──┘
```

## 2. Correlation Techniques

1. **Temporal Sliding Windows:** Events occurring within a short duration (default 5 minutes) sharing the same `secretId` or `projectId` are grouped.
2. **Causation Tree Tracing:** Events linked through `causationId` chains are correlated automatically under the root incident.
3. **Anomaly Heuristics:**
   - **Reveal Burst:** More than $N$ reveals of high-risk secrets within 60 seconds by a non-service account.
   - **Off-Hours Elevation:** JIT access requests requested outside normal working hours.
   - **Provider Drift Spikes:** Multiple drift notifications across synchronized cloud providers.
