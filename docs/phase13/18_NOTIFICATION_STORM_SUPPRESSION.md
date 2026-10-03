# Notification Storm Suppression & Deduplication Windows

## 1. Problem: Alert Flooding During Outages

When a network outage or database connectivity disruption occurs, hundreds of consumers may miss heartbeats or report rotation errors simultaneously. Dispatching hundreds of individual notifications overwhelms on-call responders and saturates notification channels.

## 2. Sliding Window Deduplication & Digest Aggregation

`NotificationService` buffers alerts within a sliding deduplication window (default 5 minutes):

1. **Fingerprint Calculation:**
   $$\text{fingerprint} = \text{SHA256}(\text{workspaceId} + ":" + \text{eventType} + ":" + \text{severity})$$
2. **Window Check:**
   If a notification with the same fingerprint was created within the last 5 minutes:
   - Increments an alert counter instead of emitting a new notification.
   - Updates the notification title to include the aggregate count:
     *Original:* "Workload consumer 'api-gateway-1' is stale"
     *Aggregated:* "14 workload consumers became stale in workspace 'Production'"
3. **Digest Delivery:**
   When the storm subsides, a single digest summary is sent to external channels.
