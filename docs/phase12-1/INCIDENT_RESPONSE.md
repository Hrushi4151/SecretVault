# SecretVault Phase 12.1 — Security Incident Response Plan

## 1. Severity Classifications & SLAs

| Severity Level | Definition | Target Triage | Target Remediation |
| :--- | :--- | :--- | :--- |
| **SEV-1 (Critical)** | Active credential compromise, unauthorized rotation bypass, data exfiltration | $< 15$ min | $< 1$ hour |
| **SEV-2 (High)** | Provider outage blocking rotation, stale consumer on revoked secret | $< 30$ min | $< 4$ hours |
| **SEV-3 (Medium)** | Overdue scheduled rotation, single-node worker crash | $< 2$ hours | $< 24$ hours |
| **SEV-4 (Low)** | Informational finding, non-critical policy adjustment | $< 1$ business day | Next release |

---

## 2. Incident Playbooks

### 2.1 Playbook: Production Database Credential Leak
1. **Isolate Scope:** Query `SecretImpactService` to identify all applications, clusters, and leases using the compromised secret.
2. **Execute Emergency Rotation:** Call `markCompromised` with `revokeLeasesImmediately: true` and `rotateImmediately: true`.
3. **Invalidate Leases:** Verify all active ephemeral leases are instantly marked `REVOKED`.
4. **Target Revocation:** Verify previous credential is revoked on PostgreSQL / MySQL target engine immediately.
5. **Audit Trail:** Export audit logs filtered by `correlationId` and `action=SECRET_MARKED_COMPROMISED`.

### 2.2 Playbook: Machine Identity Compromise
1. **Disable Machine:** Set machine identity status to `REVOKED` or `DISABLED` via CLI or console.
2. **Automated Cascade:** All future lease renewals for this machine identity fail immediately (`SecretLeaseService` revokes lease upon evaluation).
3. **Forensics:** Inspect access review campaign and access grants assigned to the machine identity.
