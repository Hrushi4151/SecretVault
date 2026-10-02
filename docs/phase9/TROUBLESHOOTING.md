# Phase 9: Troubleshooting & Diagnostic Guide

## Common Error Codes & Resolutions

---

### 1. `OIDC_TOKEN_INVALID` / `JWT Signature Verification Failed`
**Symptom**: `POST /api/v1/auth/oidc/token` returns `401 Unauthorized`.
**Causes & Fixes**:
- **Mismatched Key ID (`kid`)**: Ensure the provider's JWKS endpoint is reachable. If the provider rotated keys, wait a moment for the cache refresh or verify network access.
- **Algorithm Mismatch**: SecretVault supports RSA (`RS256`, `RS384`, `RS512`) and ECDSA (`ES256`, `ES384`, `ES512`). HMAC symmetric signing is not accepted for OIDC tokens.
- **Clock Skew**: Check the system clock of the runner issuing the token. Tokens with timestamps far in the future or past exceed the allowable tolerance window.

---

### 2. `OIDC_ISSUER_MISMATCH`
**Symptom**: `401 Unauthorized` with message "Issuer did not match expected provider issuer".
**Causes & Fixes**:
- Ensure the `issuerUrl` in the `OidcProvider` configuration precisely matches the `iss` claim in the JWT.
- Example: GitHub Actions issuer is `https://token.actions.githubusercontent.com` (no trailing slash).

---

### 3. `OIDC_AUDIENCE_MISMATCH`
**Symptom**: `401 Unauthorized` with message "Audience does not match".
**Causes & Fixes**:
- Verify the `aud` parameter requested by your CI pipeline matches the `audience` field configured on the `OidcProvider`.
- Default recommended audience: `secretvault`.

---

### 4. `TRUST_POLICY_MISMATCH` / `No Matching Trust Policy Found`
**Symptom**: `403 Forbidden` during token exchange.
**Causes & Fixes**:
- Check the claims present in the token against the machine's `OidcTrustPolicy` rules.
- Common misconfigurations:
  - Repository case mismatch (e.g. `hrushi4151/rally` vs `Hrushi4151/Rally`).
  - Branch prefix: For GitHub Actions, `ref` is `refs/heads/main`, not simply `main`. Use `EQUALS refs/heads/main` or `SUFFIX :main` on `sub`.

---

### 5. `MACHINE_DISABLED` / `MACHINE_EXPIRED`
**Symptom**: `401 Unauthorized` on API requests with `sv_machine_...`.
**Causes & Fixes**:
- Check machine status in the UI or CLI: `secretvault machine status <machine-id>`.
- If disabled, run `POST /api/v1/workspaces/{workspaceId}/machines/{machineId}/enable`.
- If expired, update `expiresAt` or extend lifecycle.

---

### 6. `SSRF_VIOLATION` / `Target IP address blocked by security policy`
**Symptom**: Failed to fetch OIDC discovery document or JWKS.
**Causes & Fixes**:
- The provider URL resolves to a loopback (`127.0.0.1`), private RFC 1918 range (`10.x`, `192.168.x`), or cloud metadata IP (`169.254.169.254`).
- In production, all OIDC providers must resolve to valid public IPs over HTTPS.
