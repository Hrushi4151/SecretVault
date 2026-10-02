# Phase 9: OIDC Subsystem & Cryptographic Validation

## OpenID Connect (OIDC) Subsystem

SecretVault implements an RFC 7519 / OpenID Connect Core 1.0 compliant validation pipeline that exchanges ephemeral workload ID tokens for scoped SecretVault machine bearer tokens.

---

## OIDC Provider Configuration

An `OidcProvider` entity encapsulates the identity authority:

| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Unique identifier of the provider. |
| `workspaceId` | UUID | Workspace owning this provider configuration. |
| `name` | String | Human-readable name (e.g. `GitHub Actions Enterprise`). |
| `type` | Enum | `GITHUB_ACTIONS`, `GITLAB_CI`, `GENERIC_OIDC`. |
| `issuerUrl` | String | Issuer URL (e.g. `https://token.actions.githubusercontent.com`). |
| `jwksUrl` | String | JWKS endpoint URL (discovered or explicit). |
| `audience` | String | Expected audience claim (`aud`). |
| `discoveryEndpoint`| String | OpenID configuration endpoint (`/.well-known/openid-configuration`). |
| `enabled` | Boolean | Whether token exchange is currently allowed for this provider. |

---

## SSRF Protection (`SsrfSafeHttpClient`)

To prevent Server-Side Request Forgery (SSRF) when SecretVault connects to untrusted external OIDC discovery or JWKS URLs:

1. **Protocol Restriction**: Only `https://` schemes are allowed in production environments.
2. **IP Resolution & Filtering**:
   - Loops through all resolved `InetAddress` records for the hostname.
   - Blocks Loopback addresses (`127.0.0.0/8`, `::1`).
   - Blocks RFC 1918 Private ranges (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`).
   - Blocks Link-Local and Multicast ranges (`169.254.0.0/16`, `224.0.0.0/4`, `fe80::/10`).
   - Blocks Cloud Metadata Services (`169.254.169.254`, `metadata.google.internal`).
3. **Connection Guarding**: Custom `SSLSocketFactory` and `SocketFactory` re-verify IP addresses during the TCP handshake to protect against DNS rebinding attacks.

---

## JWKS Caching & Dynamic Key Rotation (`JwksKeyProvider`)

- Fetches JWKS sets (JSON Web Key Sets) with standard public key formats: `RSA` (`RS256`, `RS384`, `RS512`) and `EC` (`ES256`, `ES384`, `ES512`).
- Maintains an in-memory cache keyed by `(providerId, kid)`.
- Implements a cache TTL (default: 60 minutes) to minimize external network latency.
- If an incoming JWT references a Key ID (`kid`) not present in cache, a throttled cache-miss refresh is triggered to seamlessly accommodate cryptographic key rotations.

---

## JWT Validation Engine (`JwtValidationEngine`)

For each incoming ID Token:
1. **Header Parsing**: Extracts algorithm (`alg`) and key identifier (`kid`).
2. **Algorithm Verification**: Enforces asymmetric cryptographic algorithms (`RS256`, `ES256`, etc.). Rejects unsecured tokens (`none`) and symmetric algorithms (`HS256`).
3. **Public Key Retrieval**: Retrieves verified public key from `JwksKeyProvider`.
4. **Signature Verification**: Validates the cryptographic signature against the token payload.
5. **Claims Validation**:
   - `iss` (Issuer): Must match the configured provider `issuerUrl` exactly.
   - `aud` (Audience): Must match the configured provider `audience` (or contain it in array form).
   - `exp` (Expiration): Enforces token expiry with clock skew tolerance (e.g., 60 seconds).
   - `nbf` (Not Before): Rejects tokens presented before their active window.
6. **Payload Extraction**: Converts all claims into normalized key-value maps for the `ClaimRuleEngine`.

---

## Token Exchange Endpoint (`POST /api/v1/auth/oidc/token`)

### Request Payload
```json
{
  "oidcProviderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "machineIdentityId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "idToken": "eyJhbGciOiJSUzI1NiIs..."
}
```

### Response Payload
```json
{
  "accessToken": "sv_machine_7f8a9b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b2c3d4e5f6a7b8c9d0e1f2a",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "machineIdentityId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "workspaceId": "e1f2a3b4-c5d6-e7f8-a9b2-c3d4e5f6a7b8"
}
```
