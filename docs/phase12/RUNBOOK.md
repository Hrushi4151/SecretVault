# Phase 12: Incident Response Runbook

## 1. Trigger: Critical Secret Leak Detected in Repository

### Step 1: Verification & Assessment (0 – 5 Minutes)
1. Navigate to **SecretVault Security > Repo Security & Leaks**.
2. Select the flagged finding to open the **"Why Was This Flagged?"** analysis drawer.
3. Review:
   - Secret Type and Masked Evidence.
   - Provider Validation Status (`ACTIVE` confirms the key is currently usable by attackers).
   - Matched Secret in Inventory (identifies affected production microservices).

### Step 2: Emergency Rotation (5 – 10 Minutes)
1. In the finding action menu, click **Remediate > Rotate Secret**.
2. Select Strategy: **EMERGENCY (Dual-Credential Grace Period)**.
3. SecretVault immediately creates a replacement key, propagates it to consuming services, and schedules the leaked key for revocation.

### Step 3: Git History Redaction (Post-Incident)
1. Coordinate with repo owner to run `git-filter-repo` to remove the string from historical Git objects.
2. Force push updated branches and tags.
3. Mark finding as **Resolved**.
