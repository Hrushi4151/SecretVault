# SecretVault Operational Runbook 12: AWS Region Outage & Disaster Recovery Cutover

## Incident Summary
Triggered upon total AWS primary region failure (e.g. major multi-AZ outage in `us-east-1`).

---

### Disaster Recovery Parameters
- **Recovery Point Objective (RPO):** <= 15 minutes
- **Recovery Time Objective (RTO):** <= 60 minutes
- **Target DR Region:** `us-west-2`

---

### Step-by-Step Response Procedure

1. **Declare Regional Disaster**: SRE Lead authorizes DR cutover procedure.
2. **Promote Cross-Region Read Replica**:
   ```bash
   aws rds promote-read-replica-db-cluster \
     --db-cluster-identifier secretvault-pg-dr-cluster \
     --region us-west-2
   ```
3. **Provision / Scale ECS Fargate Tasks in DR Region**:
   ```bash
   aws ecs update-service \
     --cluster secretvault-cluster-dr \
     --service secretvault-backend-dr \
     --desired-count 4 \
     --region us-west-2
   ```
4. **Update Multi-Region AWS KMS Key**:
   - Ensure DR region tasks use replicated multi-region CMK alias (`alias/secretvault-kek-dr`).
5. **Route53 DNS Failover**:
   - Update `vault.secretvault.dev` DNS A/ALIAS record to point to DR region Application Load Balancer.
6. **Verify DR System Health**:
   ```bash
   curl https://vault-dr.secretvault.dev/api/v1/system/health-score
   ```
7. **Post-Failover Audit**: Notify customer tenants and verify zero data loss.
