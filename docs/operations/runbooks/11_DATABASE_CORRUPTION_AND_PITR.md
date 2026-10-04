# SecretVault Operational Runbook 11: Database Corruption & Point-In-Time Recovery (PITR)

## Incident Summary
Triggered in the event of accidental data deletion, failed catastrophic migration, or storage-level database corruption.

---

### Step-by-Step Response Procedure

1. **Detect**: Data integrity error, corrupted table index, or accidental operator deletion.
2. **Immediate Freeze**: Activate Maintenance Mode to prevent further writes:
   ```bash
   curl -X POST https://vault.internal.net/api/v1/maintenance/enable \
     -H "Authorization: Bearer ${SV_ADMIN_TOKEN}" \
     -H "Content-Type: application/json" \
     -d '{"reason": "Database integrity recovery in progress", "allowReadOnly": false}'
   ```
3. **Identify Recovery Timestamp**: Determine the exact timestamp (UTC) immediately preceding the corruptive event (e.g. `2026-10-04T18:30:00Z`).
4. **Initiate AWS RDS Point-in-Time Restore**:
   ```bash
   aws rds restore-db-instance-to-point-in-time \
     --source-db-instance-identifier secretvault-pg-prod \
     --target-db-instance-identifier secretvault-pg-restored \
     --restore-time "2026-10-04T18:30:00Z" \
     --db-subnet-group-name secretvault-rds-subnet-group-prod \
     --vpc-security-group-ids sg-xxxxxx
   ```
5. **Validate Restored Instance**:
   - Run verification script `infrastructure/scripts/test_db_restore.sh`.
6. **Promote / Switch Connection Endpoint**:
   - Update ECS service environment variable `DB_HOST` to point to the restored RDS instance or swap DNS CNAME.
7. **Disable Maintenance Mode**:
   ```bash
   curl -X POST https://vault.internal.net/api/v1/maintenance/disable \
     -H "Authorization: Bearer ${SV_ADMIN_TOKEN}"
   ```
8. **Audit**: Record full incident timeline and data recovery metrics.
