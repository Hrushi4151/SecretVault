# SecretVault Operational Runbook 07: Mass Secret Rotation Anomaly

## Incident Summary
Triggered when an unusually high number of secret rotations (> 20 within 5 minutes) are initiated simultaneously across workspaces, indicating possible insider threat, automation defect, or security breach.

---

### Step-by-Step Response Procedure

1. **Detect**: Security Intelligence finding `MASS_ROTATION_ANOMALY` or high volume of `ROTATION_STARTED` events.
2. **Contain**: Place platform or affected workspace in Maintenance Mode:
   ```bash
   curl -X POST https://vault.internal.net/api/v1/maintenance/enable \
     -H "Authorization: Bearer ${SV_ADMIN_TOKEN}" \
     -H "Content-Type: application/json" \
     -d '{"reason": "Mass rotation anomaly investigation", "allowReadOnly": true}'
   ```
3. **Identify Actor & Trigger Source**:
   - Check audit logs for the user/machine token responsible:
     ```bash
     curl https://vault.internal.net/api/v1/audit/actions?action=ROTATION_STARTED&limit=50 \
       -H "Authorization: Bearer ${SV_ADMIN_TOKEN}"
     ```
4. **Revoke Actor Session / Token**:
   ```bash
   curl -X POST https://vault.internal.net/api/v1/auth/sessions/{sessionId}/revoke \
     -H "Authorization: Bearer ${SV_ADMIN_TOKEN}"
   ```
5. **Evaluate Active Rotation Jobs**:
   - Check if downstream systems are experiencing outage due to un-synchronized credentials.
6. **Resume / Disable Maintenance Mode**:
   ```bash
   curl -X POST https://vault.internal.net/api/v1/maintenance/disable \
     -H "Authorization: Bearer ${SV_ADMIN_TOKEN}"
   ```
7. **Audit & Incident Closure**: Record post-incident analysis in Security Incident center.
