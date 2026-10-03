# SecretVault — Production-Grade Secret Reveal Security Architecture & Threat Specification

## 1. Executive Summary & Philosophy

In modern DevSecOps, secrets in a central management vault are read constantly by machines (via scoped API keys and machine identities) but rarely inspected by humans. When a human engineer requires access to plaintext credentials (e.g. during an active production outage, key rotation, or database incident), the reveal operation is the **highest-risk vector** for credential leakage, session hijacking, insider exfiltration, and compliance failure.

SecretVault enforces a **Zero-Trust, Zero-Plaintext Storage, Two-Phase Single-Use Reveal Protocol** backed by authoritative hierarchical policy governance.

```
+---------------------------------------------------------------------------------------------------+
|                                  SECRET REVEAL SECURITY PIPELINE                                  |
+---------------------------------------------------------------------------------------------------+

 USER REQUEST
      │
      ▼
 [1. AUTHENTICATED SESSION] ───► Validate User is ACTIVE & Session is Active/Non-Expired
      │
      ▼
 [2. HIERARCHY & AUTHORIZATION] ───► Authoritative EffectiveAccessService (SECRET_REVEAL permission)
      │
      ▼
 [3. POLICY EVALUATION] ───► SecretRevealPolicyService (Hierarchical: Secret -> Env -> Proj -> WS)
      │
      ├─► Requires Step-Up? ───► Verify cryptographic X-Step-Up-Proof (WebAuthn / TOTP / Password)
      │
      └─► Requires Justification? ───► Validate reason (≥10 chars, anti-spam, ticket reference)
      │
      ▼
 [4. REVEAL INTENT ISSUE] ───► Generate single-use token (sec_rev_...) & store in Redis with TTL
      │
      ▼
 [5. REVEAL EXECUTION] ───► Atomic Consume (GETDEL Lua script) prevents replay attacks
      │
      ├─► Context Validation ───► Strict binding (userId, session, workspace, project, env, secret, ver)
      ├─► TOCTOU Re-Check ───► Re-evaluate effective access and verify secret is not deleted
      │
      ▼
 [6. CRYPTOGRAPHIC DECRYPTION] ───► AES-256-GCM in-memory with strict AAD binding
      │
      ▼
 [7. ZEROIZATION & RESPONSE] ───► Decrypted byte buffer wiped; Cache-Control: no-store headers set
      │
      ▼
 [8. AUDIT & CLIENT CONTROLS] ───► Immutable audit log emitted; Frontend auto-mask & clipboard timer
```

---

## 2. Threat Model & Adversarial Matrix (SR-01 to SR-60)

The Secret Reveal architecture is validated against 60 adversarial test scenarios implemented in [`SecretRevealThreatMatrixTest.java`](file:///d:/CodePlayground/JAVA%20SpringBoot/SecureVault/backend/src/test/java/com/secretvault/secret/reveal/SecretRevealThreatMatrixTest.java).

| Threat ID | Threat Category | Attack Vector / Scenario | Defense Mechanism | Tested & Verified |
|---|---|---|---|---|
| **SR-01** | Missing Authentication | Unauthenticated request to `/reveal-intent` | Filter chain enforces 401 Unauthorized | PASS |
| **SR-02** | Suspended User | Inactive/suspended user attempts reveal | `validateActiveUser` rejects with 401 | PASS |
| **SR-03** | Invalid Session | Forged or revoked session identifier | `validateActiveSession` fails closed with 401 | PASS |
| **SR-04** | Hierarchy Mismatch | Secret does not belong to specified environment | Scoped repository lookup throws 404 | PASS |
| **SR-05** | Workspace Isolation | Requesting secret across workspace boundary | Hierarchy check enforces workspace boundary (404) | PASS |
| **SR-06** | Project Isolation | Requesting secret from another project in workspace | Project access verification enforces 404 | PASS |
| **SR-07** | Soft-Deleted Secret | Attempting to reveal a soft-deleted secret | State check blocks reveal on `DELETED` status (400) | PASS |
| **SR-08** | Unauthorized Role | Viewer role attempting reveal | `EffectiveAccessService` blocks without `SECRET_REVEAL` (403) | PASS |
| **SR-09** | Missing Privilege | Environment requires privileged access elevation | Elevation validator enforces active grant (403) | PASS |
| **SR-10** | Expired Elevation | Elevated privileged access grant expired | `EffectiveAccessService` time-window evaluation fails (403) | PASS |
| **SR-11** | JIT Revocation | JIT access revoked before reveal | Real-time permissions re-evaluation throws 403 | PASS |
| **SR-12** | Hierarchical Override | Workspace policy overrides default policy | Deepest specific scope takes precedence | PASS |
| **SR-13** | Secret Scope Override | Secret-level policy overrides workspace policy | Explicit secret-level policy takes highest precedence | PASS |
| **SR-14** | Missing Justification | Policy requires justification reason, but null supplied | Throws `JUSTIFICATION_REQUIRED` (400) | PASS |
| **SR-15** | Short Justification | Reason shorter than minimum length (<10 chars) | Throws `INVALID_JUSTIFICATION_REASON` (400) | PASS |
| **SR-16** | Repetitive Spam Reason | Low-entropy spam (e.g. `aaaaaaaaaaaa`) | Entropy and repetition validator rejects reason (400) | PASS |
| **SR-17** | Mandatory Step-Up | Policy requires step-up but proof missing | Throws `STEP_UP_REQUIRED` (403) | PASS |
| **SR-18** | Forged Step-Up Proof | Manipulated or forged step-up proof token | Proof validator rejects forged signature (403) | PASS |
| **SR-19** | Expired Step-Up Proof | Step-up proof used after TTL expiration | Security state store fails lookup (403) | PASS |
| **SR-20** | Replayed Step-Up Proof | Reusing previously consumed step-up proof | Lua atomic GETDEL ensures single use (403) | PASS |
| **SR-21** | WebAuthn Enforcement | Policy specifies WebAuthn-only step-up, user uses TOTP | Policy factor validation rejects disallowed factor (403) | PASS |
| **SR-22** | Single-Use Intent Token | Attacker replays intercepted reveal intent token | `securityStateStore.consumeAtomic` fails on 2nd attempt (403) | PASS |
| **SR-23** | Expired Intent Token | Consuming intent token after 60s TTL | Redis TTL eviction returns empty (403) | PASS |
| **SR-24** | Cross-Secret Intent Hijack | Intent token for Secret A used to reveal Secret B | `intent.matches(...)` validates `secretId` equality (403) | PASS |
| **SR-25** | Cross-User Intent Hijack | User B attempts to consume User A's reveal intent | `intent.matches(...)` validates `userId` equality (403) | PASS |
| **SR-26** | Cross-Session Intent Hijack| User consumes intent from a different session | `intent.matches(...)` validates `sessionIdentifier` (403) | PASS |
| **SR-27** | Cross-Version Intent Hijack| Intent issued for v1 used to reveal v2 | `intent.matches(...)` validates `versionNumber` (403) | PASS |
| **SR-28** | Intent Cross-Tenant Hijack | Intent token used against different workspace | `intent.matches(...)` validates `workspaceId` (403) | PASS |
| **SR-29** | TOCTOU Permission Drop | Role downgraded between intent issue and reveal | `executeReveal` re-checks `EffectiveAccessService` (403) | PASS |
| **SR-30** | TOCTOU Secret Deletion | Secret deleted between intent issue and reveal | `executeReveal` re-verifies active status (400) | PASS |
| **SR-31** | Non-Existent Version | Revealing version that does not exist in DB | `SecretVersionRepository` returns empty (404) | PASS |
| **SR-32** | Historical Reveal Auth | Accessing historical version without reveal access | Standard reveal pipeline enforces authorization (403) | PASS |
| **SR-33** | Historical Version Intent | Historical version reveal token bound to version | Token validates exact version requested | PASS |
| **SR-34** | AAD Tampering | Encrypted payload modified in transit/storage | AES-256-GCM authentication tag check fails (500) | PASS |
| **SR-35** | Key Reference Mismatch | Version points to missing or corrupted KEK | KmsKeyProvider fails to unwrap DEK | PASS |
| **SR-36** | In-Memory Buffer Wiping | Decrypted byte array in memory | Decrypted byte buffers explicitly zeroized after response | PASS |
| **SR-37** | Plaintext Log Leakage | Logging during reveal lifecycle | Zero plaintext logged across all service layers | PASS |
| **SR-38** | URL Plaintext Leakage | Plaintext passed in query parameters | Secrets only transported via request/response body | PASS |
| **SR-39** | HTTP Cache Headers | Response cached by intermediate proxies | `Cache-Control: no-store, no-cache, must-revalidate` set | PASS |
| **SR-40** | Copy Restriction Policy | Policy disables clipboard copy | Metadata `copyAllowed: false` transmitted to client | PASS |
| **SR-41** | Display Timeout Policy | Policy configures short reveal duration (e.g. 15s) | `maxDisplayDurationSeconds` enforced by UI countdown | PASS |
| **SR-42** | Clipboard Timeout Policy | Clipboard retention configured (e.g. 15s) | `clipboardTimeoutSeconds` auto-clears clipboard | PASS |
| **SR-43** | Intent Rate Limiting | Flooding intent creation endpoint | Rate limiter restricts excessive requests per window (429) | PASS |
| **SR-44** | Reveal Rate Limiting | Flooding reveal execution endpoint | Rate limiter restricts excessive execution attempts (429) | PASS |
| **SR-45** | Concurrent Reveal Invocations | Simultaneous consumption of the same intent | Lua script executes atomically; exactly 1 wins | PASS |
| **SR-46** | Redis Outage Fail-Closed | Redis disconnects or throws exception | Intent verification fails closed (500) | PASS |
| **SR-47** | Audit Log Intent Creation | Recording `SECRET_REVEAL_INTENT_CREATED` | Immutable audit record created with actor and IP | PASS |
| **SR-48** | Audit Log Execution | Recording `SECRET_REVEALED` on success | Immutable audit record created with metadata | PASS |
| **SR-49** | Audit Log Denied | Recording `SECRET_REVEAL_DENIED` on failure | Security audit event logged for SOC monitoring | PASS |
| **SR-50** | Audit Log Replay Blocked | Recording `SECRET_REVEAL_REPLAY_BLOCKED` | High-priority security incident logged | PASS |
| **SR-51** | Audit Log Zero Plaintext | Verifying audit table contents | Audit log stores zero secret plaintext or ciphertext | PASS |
| **SR-52** | Policy CRUD Permissions | Non-admin attempting to alter reveal policies | Only `ACCESS_MANAGE` / `ADMIN` permitted (403) | PASS |
| **SR-53** | Policy Scope Validation | Setting invalid scope without parent ID | Service validates hierarchical entity presence (400) | PASS |
| **SR-54** | Policy Hierarchy Fallback | Project without policy falls back to Workspace | Hierarchy traversal resolves closest parent policy | PASS |
| **SR-55** | Policy Hierarchy Precedence | Secret policy overrides Environment policy | Most granular configuration strictly overrides | PASS |
| **SR-56** | Audit Trail Permissions | Non-security viewer accessing reveal audit | Only `SECURITY_VIEW` permitted (403) | PASS |
| **SR-57** | Bulk Reveal Restriction | Attempting bulk reveal when disallowed by policy | Policy evaluation enforces single-secret reveals | PASS |
| **SR-58** | Intent Duration Bound | Requesting intent TTL beyond max allowed limit | Service bounds TTL to policy `maxIntentDurationSeconds` | PASS |
| **SR-59** | Direct API Compatibility | Direct API clients consuming reveal with headers | Full backward compatibility for `X-Step-Up-Proof` | PASS |
| **SR-60** | Complete Lifecycle E2E | Create secret -> Policy setup -> Step-Up -> Intent -> Reveal | Full end-to-end verification passing | PASS |

---

## 3. Two-Phase Reveal Protocol

### Phase 1: Intent Creation (`POST .../reveal-intent`)
```http
POST /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{sId}/reveal-intent
Authorization: Bearer <JWT>
X-Step-Up-Proof: <step_up_proof_token> (if required)
Content-Type: application/json

{
  "versionNumber": 1,
  "reason": "Emergency incident response for ticket INC-9042"
}
```

**Response (`200 OK`):**
```json
{
  "success": true,
  "data": {
    "intentToken": "sec_rev_8f3a9e1d2c4b...",
    "expiresAt": "2026-10-03T18:00:00Z",
    "maxDisplayDurationSeconds": 45,
    "copyAllowed": true,
    "clipboardTimeoutSeconds": 15,
    "policyLevel": "HIGHLY_SENSITIVE",
    "requireReason": true,
    "requireStepUp": true,
    "allowedStepUpFactors": ["WEBAUTHN"]
  }
}
```

### Phase 2: Intent Consumption & Reveal (`POST .../reveal`)
```http
POST /api/v1/workspaces/{wId}/projects/{pId}/environments/{eId}/secrets/{sId}/reveal
Authorization: Bearer <JWT>
Content-Type: application/json

{
  "intentToken": "sec_rev_8f3a9e1d2c4b...",
  "versionNumber": 1
}
```

**Response (`200 OK`):**
```json
{
  "success": true,
  "data": {
    "id": "767cd1c9-2b0c-4aba-b2ac-0e49f7de7654",
    "environmentId": "bf5c7c2b-6493-4e09-ad22-7504d5180a22",
    "name": "PAYMENT_GATEWAY_KEY",
    "versionNumber": 1,
    "value": "sk_live_verysecret123456789",
    "revealedAt": "2026-10-03T18:00:05Z",
    "maxDisplayDurationSeconds": 45,
    "copyAllowed": true,
    "clipboardTimeoutSeconds": 15,
    "policyLevel": "HIGHLY_SENSITIVE"
  }
}
```

---

## 4. Cryptographic Envelope Architecture

1. **Envelope Encryption**: Each secret version possesses an independently generated 256-bit Data Encryption Key (DEK) wrapped under the active Key Encryption Key (KEK).
2. **Authenticated Additional Data (AAD)**: DEK encryption and ciphertext encryption bind the strict resource hierarchy:
   $$\text{AAD} = \text{secretId} \mathbin{\Vert} \text{environmentId} \mathbin{\Vert} \text{versionNumber}$$
   Any attempt to transpose ciphertext between environments or secrets immediately causes AES-256-GCM tag verification failure.
3. **In-Memory Decryption & Zeroization**: Decryption occurs solely inside `DefaultSecretRevealService.java`. The raw byte buffer is converted to string for the ephemeral response, and byte arrays are overwritten with zeroes (`Arrays.fill(bytes, (byte) 0)`).

---

## 5. Summary of Policy Hierarchy & Scoping

Reveal policies are evaluated through hierarchical inheritance:
$$\text{SECRET} \succ \text{ENVIRONMENT} \succ \text{PROJECT} \succ \text{WORKSPACE} \succ \text{DEFAULT}$$

- **`DEFAULT`**: Standard developer environment. Step-up optional, justification optional, 60s display.
- **`SENSITIVE`**: Staging environments. Step-up recommended, 45s display, 15s clipboard.
- **`HIGHLY_SENSITIVE`**: Production environments. Mandatory step-up, mandatory justification (≥ 10 chars), 30s display.
- **`PRODUCTION_CRITICAL`**: Tier-0 credentials (e.g. Master DB passwords, root cloud keys). Mandatory WebAuthn-only step-up, mandatory ticket justification, clipboard copy completely disabled (`copyAllowed = false`), 15s auto-mask.
