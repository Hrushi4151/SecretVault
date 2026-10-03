# Disaster Recovery & Operational Runbooks

## Runbook 1: Clearing Outbox Backlog & Stuck Processing Locks

### Symptoms:
- Events accumulating in `event_outbox` with status `PROCESSING` or `PENDING`.
- Latency between entity mutation and webhook delivery exceeds 30 seconds.

### Resolution Steps:
1. Inspect health of outbox dispatcher workers:
   ```bash
   curl -s -H "Authorization: Bearer $TOKEN" https://vault.internal.net/actuator/health
   ```
2. Check for stale locks older than 5 minutes:
   ```sql
   SELECT id, event_type, locked_at, locked_by FROM event_outbox 
   WHERE status = 'PROCESSING' AND locked_at < NOW() - INTERVAL '5 minutes';
   ```
3. Stale locks are automatically reclaimed by the scheduler. To force release manually:
   ```sql
   UPDATE event_outbox SET status = 'PENDING', locked_at = NULL, locked_by = NULL 
   WHERE status = 'PROCESSING' AND locked_at < NOW() - INTERVAL '5 minutes';
   ```

---

## Runbook 2: Recovering and Replaying Dead-Lettered Events

### Symptoms:
- Events present in `event_outbox` with `status = 'DEAD_LETTER'`.

### Resolution Steps:
1. Identify the root cause from `last_error`:
   ```bash
   secretvault events list --type "*" --limit 50 | grep DEAD_LETTER
   ```
2. Fix downstream consumer availability or webhook receiver endpoint.
3. Trigger targeted replay:
   ```bash
   secretvault events replay --event-id <UUID> --reexecute=true
   ```

---

## Runbook 3: Triaging Webhook Delivery Failures

### Symptoms:
- Webhook endpoint shows consecutive delivery failures; downstream alerts missing.

### Resolution Steps:
1. Check delivery attempts in CLI:
   ```bash
   secretvault webhook deliveries --webhook-id <WEBHOOK_UUID>
   ```
2. If `http_status = 400` or `SSRF_VIOLATION`:
   Verify that destination URL points to a valid public HTTPS hostname and not an internal IP.
3. Once the endpoint is restored, replay the failed delivery:
   ```bash
   secretvault webhook replay <DELIVERY_UUID>
   ```
