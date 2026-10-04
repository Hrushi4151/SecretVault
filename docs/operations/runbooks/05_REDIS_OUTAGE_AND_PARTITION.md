# SecretVault Operational Runbook 05: Redis Outage & Cluster Partition

## Incident Summary
Triggered when ElastiCache Redis primary node becomes unreachable, network partitioned, or runs out of memory (`OOM command not allowed`).

---

### Step-by-Step Response Procedure

1. **Detect**: Alert `SecretVaultRedisDegraded` or `RedisHealthIndicator` reporting `DOWN`.
2. **Security Invariant Evaluation**:
   - **MFA Challenges / Step-Up Proofs / JIT State**: Enforces strict **FAIL-CLOSED** security policy (rejections with `SECURITY_STATE_ERROR` rather than unauthorized bypass).
   - **Rate Limiting**: Configured in production to fail closed against brute force.
3. **ElastiCache Automatic Failover**:
   - Multi-AZ ElastiCache automatically promotes replica node to primary within 30 seconds.
4. **Memory Pressure Mitigation**:
   - If Redis is OOM, verify eviction policy (`volatile-ttl`) and scale node size:
     ```bash
     aws elasticache modify-replication-group \
       --replication-group-id secretvault-redis-prod \
       --cache-node-type cache.r6g.large \
       --apply-immediately
     ```
5. **Recovery Verification**:
   ```bash
   curl https://vault.internal.net/api/v1/system/health-score
   ```
6. **Audit**: Verify no stale session tokens or security challenges persisted past their intended TTLs.
