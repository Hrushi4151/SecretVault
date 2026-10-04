# SecretVault Operational Runbook 13: Failed Deployment & Rollback

## Incident Summary
Triggered when a newly deployed backend or frontend image fails readiness probes, throws startup exceptions, or causes 5xx error spikes.

---

### Step-by-Step Response Procedure

1. **Detect**: Alert `SecretVaultHigh5xxRate` or ECS deployment circuit breaker triggered.
2. **Immediate Rollback to Previous Stable Task Definition**:
   ```bash
   PREV_TASK_DEF=$(aws ecs list-task-definitions --family-prefix secretvault-backend-prod --sort DESC --query 'taskDefinitionArns[1]' --output text)
   aws ecs update-service \
     --cluster secretvault-cluster-prod \
     --service secretvault-backend-prod \
     --task-definition "${PREV_TASK_DEF}"
   ```
3. **Frontend Rollback**:
   ```bash
   PREV_FRONTEND_DEF=$(aws ecs list-task-definitions --family-prefix secretvault-frontend-prod --sort DESC --query 'taskDefinitionArns[1]' --output text)
   aws ecs update-service \
     --cluster secretvault-cluster-prod \
     --service secretvault-frontend-prod \
     --task-definition "${PREV_FRONTEND_DEF}"
   ```
4. **Database Backward Compatibility Invariant**:
   - SecretVault migrations follow **Expand -> Migrate -> Contract**. The database schema remains compatible with the previous application version.
5. **Verify Recovery**:
   ```bash
   curl https://vault.internal.net/api/v1/system/health-score
   ```
