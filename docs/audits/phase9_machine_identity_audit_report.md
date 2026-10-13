# Phase 9: Machine Identities & CI/CD Workload Authentication
## Functional Audit, Cryptographic Security Validation & Operational Report

**Platform**: SecretVault DevSecOps Platform  
**Subsystems Audited**: `com.secretvault.machine`, `com.secretvault.oidc`, `EffectiveAccessService`  
**Security Standard Alignment**: NIST SP 800-204A/B/C (Workload Identity & Microservice Access Control), OpenID Connect Core 1.0, RFC 7519 (JWT), RFC 7517 (JWK)  
**Execution Environment**: Local Workspace (`Hrushi4151/SecretVault`)  
**Remote Git Status**: `REMOTE PUSH PERFORMED: NO`  

---

## 1. Executive Summary

Phase 9 establishes cryptographic, non-human workload identities within SecretVault, eliminating the need for long-lived, high-risk static tokens stored across external CI/CD pipelines (e.g., GitHub Secrets, GitLab CI Variables, Jenkins credentials). Non-human workloads authenticate via standard OpenID Connect (OIDC) identity federation, exchanging short-lived, cryptographically signed OIDC ID tokens for ephemeral, tightly scoped SecretVault machine sessions.

### Key Functional & Security Outcomes
1. **Zero Standing Secrets**: CI/CD pipelines obtain short-lived machine session tokens that expire automatically (default 1 hour, configurable down to minutes), mitigating the threat of credential leakage from build logs and artifact caches.
2. **Cryptographic Federation**: Tokens are validated using public keys fetched via Server-Side Request Forgery (SSRF)-protected HTTP clients and cached with thread-safe LRU semantics.
3. **Deterministic Claim Policy Engine**: Trust policies validate arbitrary OIDC claims (`sub`, `repository`, `ref`, `job_workflow_ref`, `actor`) using strict pattern matching rules (`EQUALS`, `PREFIX`, `SUFFIX`, `CONTAINS`, `REGEX`, `IN`) to ensure only authorized branches, workflows, or repositories can assume a machine identity.
4. **Least-Privilege Authorization**: Machine sessions are constrained by `MachineGrant` entities that limit read/write access to specific projects, environments, and glob patterns (e.g., `prod/database/*`), enforced via `EffectiveAccessService` with immediate revocation capabilities.
5. **Defect Remediation**: Successfully diagnosed and repaired UI data-unwrapping discrepancies in `MachineIdentitiesView.jsx`, `OidcProvidersView.jsx`, and `MachineDetailsModal.jsx`, resolved OIDC provider 409 conflict handling, prevented UI error state wiping, and reinforced HikariCP connection health check configurations for Neon PostgreSQL.

---

## 2. Architectural Blueprint

```
+---------------------------------------------------------------------------------------------------+
|                                  CI/CD WORKLOAD / RUNNER                                         |
|  (GitHub Actions Runner / GitLab CI Runner / Kubernetes Pod / Spinnaker / ArgoCD / Terraform)     |
+---------------------------------------------------------------------------------------------------+
                                                  |
                    1. Request signed OIDC JWT   |  actions/core.getIDToken()
                                                  v
+---------------------------------------------------------------------------------------------------+
|                                      OIDC IDENTITY PROVIDER                                       |
|               GitHub Actions (token.actions.githubusercontent.com) / GitLab CI / Okta             |
+---------------------------------------------------------------------------------------------------+
                                                  |
                    2. Issues OIDC ID Token       |  RSA-256 / EC-256 signed JWT
                                                  v
+---------------------------------------------------------------------------------------------------+
|                                     SECRETVault PLATFORM                                          |
|                                                                                                   |
|   POST /api/v1/workspaces/{id}/oidc/exchange                                                      |
|   { providerId, idToken, requestedDurationSeconds }                                               |
|                                                                                                   |
|   +-------------------------------------------------------------------------------------------+   |
|   | 1. SsrfSafeHttpClient: Validates Provider JWKS endpoint (Blocks RFC1918, 169.254.169.254)|   |
|   | 2. JwtValidationEngine: Validates signature, expiry, nbf, aud, iss, token size (<16KB)    |   |
|   | 3. OidcWorkloadService: Matches OidcTrustPolicy for MachineIdentity                       |   |
|   | 4. ClaimRuleEngine: Evaluates claim rules (repo, ref, workflow, actor)                     |   |
|   | 5. MachineIdentity: Enforces ACTIVE status, non-expired lifecycle, duration cap           |   |
|   | 6. MachineSessionService: Issues ephemeral MachineSession (JTI tracking)                  |   |
|   +-------------------------------------------------------------------------------------------+   |
|                                                  |                                                |
|   7. Returns Session Token                       |                                                |
|   (Bearer sv_ms_...)                             v                                                |
|                                      +-----------------------+                                    |
|                                      | MachineSession Record |                                    |
|                                      | Status: ACTIVE        |                                    |
|                                      | Expires: 3600s        |                                    |
|                                      +-----------------------+                                    |
+---------------------------------------------------------------------------------------------------+
                                                  |
                    3. Authenticated API Call    |  Authorization: Bearer sv_ms_...
                                                  v
+---------------------------------------------------------------------------------------------------+
|                                  EFFECTIVE ACCESS SERVICE                                         |
|                                                                                                   |
|   - Workspace Boundary Isolation: Machine must belong to target workspace                         |
|   - Machine Grants: Validates project, environment, and glob pattern against target secret        |
|   - Permission Checks: READ, WRITE, ADMIN                                                         |
|   - Session Revocation Check: Instantly blocks revoked sessions                                   |
+---------------------------------------------------------------------------------------------------+
```

---

## 3. End-to-End Cryptographic Validation & OIDC Exchange

### 3.1 Token Exchange Flow
When a CI/CD job requires secrets, it executes an OIDC token exchange rather than authenticating with a pre-shared secret:
1. **Token Generation**: The CI runner requests an ID token from its local environment (e.g., `ACTIONS_ID_TOKEN_REQUEST_URL` in GitHub Actions).
2. **Payload Submission**: The runner sends a `POST` request to `/api/v1/workspaces/{workspaceId}/oidc/exchange` containing:
   - `providerId`: Target OIDC provider UUID.
   - `idToken`: The raw JWT signed by the provider.
   - `machineIdentityId`: Optional explicit identity target (if omitted, the system resolves unique matching policies).
   - `sessionDurationSeconds`: Requested duration capped by machine limits.
3. **Cryptographic Verification**:
   - `JwtValidationEngine` checks token structure, size (rejecting tokens > 16KB), and header.
   - Algorithm pinning: Strictly permits `RS256`, `RS384`, `RS512`, `ES256`, `ES384`, `ES512`. Rejects `none` algorithm and prevents HMAC algorithm confusion.
   - JWKS Retrieval: Public keys are retrieved from the provider's `jwksUri` using `SsrfSafeHttpClient`. Public keys are cached locally with TTL and cache busting on unrecognized `kid`.
   - Claims Verification: Evaluates expiration (`exp`), not before (`nbf`), allowed clock skew (60 seconds), expected audience (`aud`), and issuer (`iss`).

### 3.2 Claim Rule Engine Evaluation
Once the cryptographic signature is verified, `ClaimRuleEngine` evaluates the claims in the token against the machine identity's `OidcTrustPolicy`:
- **Policy Modes**:
  - `ALL`: Every configured rule must evaluate to `true` (logical AND).
  - `ANY`: At least one configured rule must evaluate to `true` (logical OR).
- **Supported Comparison Operators**:
  - `EQUALS`: Exact string equality (case-sensitive or insensitive).
  - `PREFIX`: Matches prefixes (e.g., `ref` starts with `refs/heads/main`).
  - `SUFFIX`: Matches suffixes.
  - `CONTAINS`: Substring search.
  - `REGEX`: Java regular expression evaluation with safety guards.
  - `IN`: Comma-delimited membership check.

### 3.3 Ephemeral Session Creation
Upon successful verification:
- An ephemeral `MachineSession` entity is persisted in the database.
- A cryptographically random session token with prefix `sv_ms_` is generated.
- The token's hash is stored to ensure tokens cannot be recovered from database dumps.
- Session expiration is set to `min(requestedDuration, machine.maxSessionDurationSeconds)`.

---

## 4. Least-Privilege Authorization & Scoped Grants

Authorization for machine identities is managed via `EffectiveAccessService` and `MachineGrant`:
1. **Deny-by-Default Posture**: A newly created machine identity has **zero** access to any secret in the workspace until an explicit `MachineGrant` is created.
2. **Multi-Dimensional Scope**:
   - **Workspace**: Access is strictly limited to secrets within the machine's home workspace.
   - **Project**: Grants can be scoped to specific projects or wildcarded (`*`).
   - **Environment**: Grants can target environments (e.g., `production`, `staging`, `development`).
   - **Secret Pattern**: Supports glob matching (e.g., `db/*`, `aws/credentials`, `stripe_*`).
3. **Permission Levels**:
   - `READ`: View decrypted secret values and metadata.
   - `WRITE`: Update secrets, create new versions.
   - `ADMIN`: Manage secret configuration, rotation policies, and grants.
4. **Instant Revocation**:
   - Machine status can be toggled to `DISABLED` or `REVOKED`, instantly blocking all active and future sessions.
   - Individual sessions can be revoked on-demand via `DELETE /api/v1/workspaces/{wsId}/machine-identities/{mId}/sessions/{sessionId}`.

---

## 5. Security Safeguards & Threat Modeling

| Threat Vector | Mitigation Strategy | Implementation |
| :--- | :--- | :--- |
| **Server-Side Request Forgery (SSRF)** | Provider discovery and JWKS URLs are validated to prevent targeting cloud metadata or internal network endpoints. | `SsrfSafeHttpClient` enforces HTTPS and blocks RFC 1918 private IPs (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), loopbacks (`127.0.0.1`), and cloud metadata (`169.254.169.254`). |
| **Algorithm Confusion & `none` Alg** | Attackers craft unsigned tokens or switch RSA public keys to HMAC keys. | `JwtValidationEngine` rejects `alg=none` and checks that the signing algorithm family matches the resolved public key type. |
| **Token Tampering & Replay** | Tokens replayed after pipeline execution or modified in flight. | Strict `exp` checks with a maximum 60s clock skew; JTI uniqueness logging; short session timeouts. |
| **Over-Privileged CI Workloads** | Pull request workflows accessing production secrets. | `ClaimRuleEngine` checks `ref == refs/heads/main` and `repository == org/repo`, blocking pull request refs (`refs/pull/*`). |
| **Database Compromise** | Exposure of active machine session tokens. | Machine session tokens are hashed using SHA-256 before persistence; raw tokens are only returned once at exchange time. |

---

## 6. Root-Cause Analysis of Discovered Defects & Remediations

### Defect 1: Machine Identities and Providers Created but Not Showing in UI
- **Root Cause**: `apiClient.request()` in `frontend/src/api/client.js` automatically unwraps Spring Boot `{ success: true, data: [...] }` envelopes, returning raw arrays directly. In `MachineIdentitiesView.jsx`, `OidcProvidersView.jsx`, and `MachineDetailsModal.jsx`, data was extracted using `res.data?.data || res.data || []`. When `res` was already an Array, `res.data` was `undefined`, causing the state to evaluate to `[]`.
- **Remediation**: Implemented a universal `unwrapData(res, fallback)` helper across all components that safely handles raw arrays, enveloped objects, and nested payloads.

### Defect 2: 409 Conflict When Registering OIDC Providers
- **Root Cause**: Workspaces enforce unique constraints on provider names and normalized issuer URLs. Because the list view was empty (due to Defect 1), users attempted to re-add "GitHub Actions", resulting in an HTTP 409 Conflict.
- **Remediation**: Fixed data unwrapping so existing providers display immediately. Added automatic list refresh and error message presentation upon 409 Conflict.

### Defect 3: UI Error Messages Overwritten on Refresh
- **Root Cause**: `handleCreateProvider` set `errorMessage` in its catch block and immediately invoked `loadProviders()`. Inside `loadProviders()`, `setErrorMessage(null)` unconditionally cleared the error, preventing users from seeing conflict or validation errors.
- **Remediation**: Updated `loadProviders(clearError = true)` and `loadMachines(clearError = true)` to accept a parameter that preserves active error messages when reloading after a failure.

### Defect 4: Database Connection Dropouts on Serverless PostgreSQL
- **Root Cause**: Neon serverless PostgreSQL drops idle TCP connections after inactivity, causing HikariCP pool errors during subsequent requests.
- **Remediation**: Configured robust HikariCP parameters in `backend/src/main/resources/application.yml`:
  - `connection-test-query: SELECT 1`
  - `max-lifetime: 300000` (5 minutes)
  - `idle-timeout: 60000` (1 minute)
  - `keepalive-time: 30000` (30 seconds)

---

## 7. Verification & Test Evidence

### 7.1 Backend Test Suite
Executed test command:
```bash
mvn test -Dtest="*Machine*,*Oidc*,ClaimRuleEngineTest,JwtValidationEngineTest,SsrfProtectionTest"
```
**Results**:
- **Total Tests Run**: 58
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 0
- **Build Status**: `BUILD SUCCESS`

Detailed Breakdown:
- `MachineAuthorizationIntegrationTest`: 7 passed (grant evaluation, pattern matching, deny overrides).
- `ClaimRuleEngineTest`: 7 passed (all comparison operators, edge conditions).
- `JwtValidationEngineTest`: 7 passed (expired tokens, clock skew, issuer validation, algorithm verification).
- `OidcTokenExchangeIntegrationTest`: 4 passed (complete exchange flow, ambiguous policy protection, unmatched claims).
- `SsrfProtectionTest`: 4 passed (RFC 1918 blocking, AWS/GCP metadata IP blocking, loopback blocking).
- `RotationStateMachineCertificationTest`: 29 passed.

### 7.2 Frontend Test Suite
Created dedicated test suite `frontend/src/__tests__/MachineIdentitiesAndOidc.test.jsx`:
- **MachineIdentitiesView**: Validates array response unwrapping and machine listing.
- **Machine Creation Flow**: Verifies payload formation, identity creation, and list updates.
- **OidcProvidersView**: Validates provider listing and card rendering.
- **Conflict Handling**: Verifies 409 Conflict error banner presentation and list re-synchronization.

Executed full test command:
```bash
npm test -- --run
```
**Results**:
- **Test Files**: 17 passed (100%)
- **Total Tests**: 91 passed (100%)
- **Duration**: 7.88s

### 7.3 Production Build Validation
Executed Vite build:
```bash
npm run build
```
**Result**: Built cleanly in 5.50s with zero errors.

---

## 8. Real-World CI/CD Implementation Example

Below is a production-ready GitHub Actions workflow demonstrating how a CI/CD pipeline authenticates with SecretVault using OIDC:

```yaml
name: Deploy Production Workload

on:
  push:
    branches:
      - main

permissions:
  id-token: write # Mandatory for requesting OIDC tokens
  contents: read

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Retrieve GitHub OIDC Token
        id: oidc
        run: |
          # Request OIDC ID token from GitHub's internal token service
          TOKEN=$(curl -H "Authorization: bearer $ACTIONS_ID_TOKEN_REQUEST_TOKEN" \
            "$ACTIONS_ID_TOKEN_REQUEST_URL&audience=https://secretvault.internal" | jq -r '.value')
          echo "::add-mask::$TOKEN"
          echo "id_token=$TOKEN" >> $GITHUB_OUTPUT

      - name: Exchange OIDC Token for SecretVault Session
        id: secretvault_auth
        run: |
          RESPONSE=$(curl -s -X POST "https://vault.company.com/api/v1/workspaces/d401c3aa-f325-4633-ad5a-f718ae4c7076/oidc/exchange" \
            -H "Content-Type: application/json" \
            -d '{
              "providerId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
              "idToken": "${{ steps.oidc.outputs.id_token }}",
              "sessionDurationSeconds": 1800
            }')
          
          SESSION_TOKEN=$(echo "$RESPONSE" | jq -r '.data.sessionToken')
          echo "::add-mask::$SESSION_TOKEN"
          echo "session_token=$SESSION_TOKEN" >> $GITHUB_OUTPUT

      - name: Fetch Database Credentials
        run: |
          SECRET_PAYLOAD=$(curl -s "https://vault.company.com/api/v1/workspaces/d401c3aa-f325-4633-ad5a-f718ae4c7076/secrets/prod-db-password" \
            -H "Authorization: Bearer ${{ steps.secretvault_auth.outputs.session_token }}")
          
          DB_PASS=$(echo "$SECRET_PAYLOAD" | jq -r '.data.currentVersion.secretValue')
          echo "::add-mask::$DB_PASS"
          echo "Database secret retrieved securely via ephemeral workload session."
```

---

## 9. Conclusion & Operational Readiness

Phase 9 (Machine Identities & CI/CD Workload Authentication) is functionally verified, hardened against cryptographic and SSRF attacks, and fully integrated with SecretVault's authorization and audit systems. All reported issues regarding list rendering, 409 conflict loops, and error handling have been resolved.
