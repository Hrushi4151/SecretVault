# SecretVault Operational Runbook 08: Unauthorized Secret Reveal Investigation

## Incident Summary
Triggered when an unauthorized user attempts to reveal a plaintext secret value, fails Step-Up authentication, or consumes a reveal intent token outside established policy boundaries.

---

### Step-by-Step Response Procedure

1. **Detect**: Audit action `SECRET_REVEAL_DENIED` or `SECRET_REVEAL_POLICY_DENIED`.
2. **Examine Intent Details**:
   ```bash
   curl https://vault.internal.net/api/v1/workspaces/{workspaceId}/secrets/{secretId}/reveal-audits \
     -H "Authorization: Bearer ${SV_ADMIN_TOKEN}"
   ```
3. **Check Actor & IP**:
   - Determine whether the request originated from an unrecognized IP, VPN, or Tor exit node.
4. **Enforce Access Suspension**:
   - If the user account appears compromised, revoke active sessions and suspend the user:
     ```bash
     curl -X POST https://vault.internal.net/api/v1/users/{userId}/suspend \
       -H "Authorization: Bearer ${SV_ADMIN_TOKEN}"
     ```
5. **Rotate Accessed Secret (if suspect)**:
   - If the secret might have been exposed during the interaction, trigger rotation:
     ```bash
     curl -X POST https://vault.internal.net/api/v1/secrets/{secretId}/rotate \
       -H "Authorization: Bearer ${SV_ADMIN_TOKEN}"
     ```
6. **Audit & Log Verification**: Confirm all reveal attempts have immutable justification records.
