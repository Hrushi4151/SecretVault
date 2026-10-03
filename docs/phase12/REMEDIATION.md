# Phase 12: Remediation Workflows & Verification

## 1. Overview

Finding a leaked credential is only half the battle. Remediating it safely without causing application downtime is the critical second half.

SecretVault provides six distinct remediation actions through `FindingRemediationService`:

```
+-------------------------------------------------------------+
|               SecretVault Remediation Engine                |
+-------------------------------------------------------------+
   |
   +---> [1. ROTATE_SECRET]            (Zero-Downtime Cascade)
   |
   +---> [2. REVOKE_CREDENTIAL]        (Immediate Cloud Invalidation)
   |
   +---> [3. GIT_HISTORY_REWRITE_GUIDE](git-filter-repo / BFG)
   |
   +---> [4. MARK_FALSE_POSITIVE]      (Auto-add to Allowlists)
   |
   +---> [5. IGNORE]                   (Accepted Risk)
   |
   +---> [6. MANUAL_ACKNOWLEDGE]       (Audit Log Record)
```

---

## 2. Action Specifications

### 1. `ROTATE_SECRET`
If the finding matches an active secret in SecretVault's inventory (`matchedSecretId != null`), triggering `ROTATE_SECRET` invokes the Phase 12 `RotationService.triggerRotation()` workflow with strategy `EMERGENCY`:
- A new cryptographically secure replacement secret is generated.
- Pre-activation health checks validate the new credential against the provider.
- Dual-credential grace periods allow active microservice consumers to adopt the new key.
- The obsolete leaked credential is automatically scheduled for revocation after consumers acknowledge the rotation.

### 2. `REVOKE_CREDENTIAL`
For unmanaged keys where immediate invalidation is required, SecretVault issues an emergency provider revocation request (or directs the administrator to the provider's token invalidation console).

### 3. `GIT_HISTORY_REWRITE_GUIDE`
Even after credential rotation, the leaked token must be scrubbed from the repository to prevent confusion and clean security scanner reports. SecretVault provides automated command sequences using `git-filter-repo`:
```bash
# 1. Install git-filter-repo
pip install git-filter-repo

# 2. Scrub specific string from entire commit graph
git filter-repo --replace-text <(echo "OLD_SECRET==>REDACTED_SECRET")

# 3. Force push rewritten history to upstream
git push origin --force --all
git push origin --force --tags
```

### 4. `MARK_FALSE_POSITIVE`
Creates a `FindingAllowlist` entry with the token's SHA-256 fingerprint, automatically suppressing future alerts across subsequent scans.
