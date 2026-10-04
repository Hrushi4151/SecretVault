# SecretVault Operational Runbook 04: PostgreSQL Outage & Multi-AZ Failover

## Incident Summary
Triggered when primary PostgreSQL RDS instance becomes unreachable, crashes, or suffers hardware degradation in the primary Availability Zone.

---

### Step-by-Step Response Procedure

1. **Detect**: CloudWatch alarm `RDS-DatabaseDown` or `SecretVaultDatabasePoolSaturated`, Hikari connection timeout exceptions.
2. **Automatic Multi-AZ Failover Verification**:
   - AWS RDS automatically initiates failover to the standby replica in the secondary AZ within 60–120 seconds.
   - Monitor RDS status:
     ```bash
     aws rds describe-db-instances --db-instance-identifier secretvault-pg-prod \
       --query 'DBInstances[0].DBInstanceStatus'
     ```
3. **Manual Forced Failover (if primary is unresponsive but not auto-failing)**:
   ```bash
   aws rds reboot-db-instance --db-instance-identifier secretvault-pg-prod --force-failover
   ```
4. **Application Connection Pool Reconnection**:
   - HikariCP pool automatically evicts broken sockets and re-resolves the RDS DNS CNAME to the new primary instance.
5. **Verify System Health**:
   ```bash
   curl https://vault.internal.net/api/v1/system/health-score
   ```
6. **Verify Data Integrity**:
   - Check outbox event queue depth and resume any paused rotation jobs:
     ```bash
     curl -X POST https://vault.internal.net/api/v1/rotation/retry-stale \
       -H "Authorization: Bearer ${SV_ADMIN_TOKEN}"
     ```
7. **Audit**: Log failover duration and root cause in incident tracker.
