# Ephemeral Secret Leases & Runtime Lifecycles

## 1. Concept & Threat Model

Long-lived, static credentials stored indefinitely on client machines present severe security risks:
- Secret sprawl across developer laptops and CI runners.
- Credential leakages that remain undetected for months.
- Lack of runtime visibility into who is currently using a credential.

**Secret Leases** transform static credentials into short-lived, ephemeral runtime tokens with explicit expiration boundaries.

---

## 2. Lease Properties

| Property | Type | Description |
| :--- | :--- | :--- |
| `id` | `UUID` | Unique lease identifier |
| `secretId` | `UUID` | Target secret bound to this lease |
| `secretVersionNumber` | `Integer` | Exact version issued with this lease |
| `machineIdentityId` | `UUID` | (Optional) Bound machine identity |
| `ttlSeconds` | `long` | Incremental validity duration (e.g. 900s = 15m) |
| `maxLifetimeSeconds`| `long` | Absolute ceiling for renewals (e.g. 14,400s = 4h) |
| `issuedAt` | `Instant` | Time when initial lease was issued |
| `expiresAt` | `Instant` | Current expiration timestamp (`now + ttlSeconds`) |
| `status` | `LeaseStatus` | `ACTIVE`, `RENEWED`, `EXPIRED`, `REVOKED` |

---

## 3. Lease Lifecycle Operations

### 1. Issuance (`createLease`)
```bash
POST /api/v1/workspaces/{workspaceId}/leases
```
The caller requests a lease for a secret. If granted by RBAC/ABAC policy, a new `SecretLease` is issued with an initial TTL.

### 2. Dynamic Renewal (`renewLease`)
```bash
POST /api/v1/workspaces/{workspaceId}/leases/{leaseId}/renew
```
Workloads can extend active leases periodically. However, the lease **cannot** be extended past `issuedAt + maxLifetimeSeconds`. Once the absolute maximum lifetime is reached, the client is forced to request a fresh lease and re-authenticate.

### 3. Immediate Revocation (`revokeLease`)
```bash
DELETE /api/v1/workspaces/{workspaceId}/leases/{leaseId}
```
Operators can immediately revoke an active lease if suspicious behavior is detected.

### 4. Background Expiration Worker
The `@Scheduled` worker in `SecretLeaseService` runs every 60 seconds:
- Queries active leases where `expires_at <= now()`.
- Transitions their status to `EXPIRED`.
- Records audit records for governance and compliance reporting.
