# SecretVault CLI — Authentication & Profiles

The SecretVault CLI implements enterprise-grade authentication with automatic token renewal and encrypted local credential storage.

---

## 1. Authentication Flow

```
Developer
    │
    ▼
secretvault auth login ──[ Interactive Non-Echo Password ]
    │
    ▼
REST API: POST /api/v1/auth/login
    │
    ├─► If MFA Required (HTTP 200 with mfaRequired: true):
    │     ├── Prompts for 6-digit TOTP code (or recovery code)
    │     └── Calls POST /api/v1/auth/mfa/verify-totp (or verify-recovery)
    │
    ├─► Success: JWT Access Token + Refresh Token
    ▼
Encrypted Credential Store (AES-256-GCM + PBKDF2)
    │
    └─► Tokens saved encrypted in ~/.credentials.enc
```

---

## 1.1 Multi-Factor Authentication (MFA)

When a user has MFA enabled, `secretvault auth login` seamlessly initiates an interactive challenge flow:

1. **TOTP Verification (Default):**
   - The CLI prompts the user: `Enter 6-digit MFA Code (or type 'recovery'):`
   - The user inputs their 6-digit authenticator code (masked/securely handled).
   - CLI issues `POST /api/v1/auth/mfa/verify-totp` with `{ mfaToken, totpCode }`.

2. **Recovery Code Verification (Backup):**
   - If the user provides `--recovery` or enters `recovery`, the CLI prompts: `Enter MFA Backup Recovery Code:`
   - CLI issues `POST /api/v1/auth/mfa/verify-recovery` with `{ mfaToken, recoveryCode }`.

All MFA inputs and temporary tokens are zeroized in memory immediately following the verification attempt.

---

## 1.2 Generalized Step-Up Authentication

For privileged or sensitive actions (such as `secret reveal`, `secret delete`, or `secret rollback`), the SecretVault backend enforces contextual Step-Up Authentication:

1. The CLI initiates a challenge request `POST /api/v1/auth/step-up/challenge` with the target `action` and `resourceId`.
2. The server responds with allowed factors (e.g. `TOTP`, `PASSWORD`, `RECOVERY_CODE`, `WEBAUTHN`).
3. The CLI prompts for the highest available CLI-compatible factor (TOTP -> Password -> Recovery Code) and verifies via `POST /api/v1/auth/step-up/verify`.
4. The server returns a short-lived, single-use `stepUpProof` token, which the CLI passes via the `X-Step-Up-Proof` header to authorize the protected operation.
5. If the policy mandates WebAuthn hardware keys only (`requireWebAuthnOnly`), the CLI safely denies the operation and instructs the user to perform the ceremony via the SecretVault Web Console.

---

## 2. Interactive & Non-Interactive Login

### Interactive Terminal Login (Default)

Passwords are typed securely without echoing to the terminal:

```bash
secretvault auth login --email developer@secretvault.io
# Prompts: Password: [hidden]
```

### Script / Headless Login (via Stdin)

To authenticate in CI or automated deployment scripts without saving passwords in shell history:

```bash
echo "$VAULT_PASSWORD" | secretvault auth login --email devops@company.com --password-stdin
```

> [!WARNING]
> Never pass plaintext passwords via command line arguments (`--password`). The CLI intentionally disallows `--password <value>` to prevent passwords from leaking into process tables and shell histories (`~/.bash_history`).

---

## 3. Encrypted Credential Storage

The CLI does **NOT** store plaintext tokens in `config.json`. 

Instead, credentials are encrypted using **AES-256-GCM** with a master key derived using **PBKDF2WithHmacSHA256** (65,536 iterations).

### File Storage Locations:
- **macOS:** `~/Library/Application Support/SecretVault/.credentials.enc`
- **Linux:** `~/.config/secretvault/.credentials.enc`
- **Windows:** `%APPDATA%\SecretVault\.credentials.enc`

POSIX permissions are restricted to owner read/write (`chmod 600`) upon creation.

---

## 4. Multi-Profile Management

SecretVault CLI supports multiple profiles and multiple server endpoints:

```bash
# Authenticate against work cluster
secretvault auth login --profile work --server https://vault.company.internal --email lead@company.com

# Authenticate against local dev backend
secretvault auth login --profile local --server http://localhost:8080 --email dev@local.io

# List all configured profiles
secretvault auth profiles

# Switch active profile
secretvault auth switch work
```

---

## 5. Token Auto-Refresh & Session Expiry

- The CLI automatically tracks access token expiration timestamps.
- When an API request returns HTTP 401, the CLI automatically calls `POST /api/v1/auth/refresh` using the secure refresh token, updates the encrypted store, and seamlessly retries the operation once.
- If refresh fails (e.g. revoked session or disabled account), the CLI clears the stored credentials and displays an actionable login prompt.

---

## 6. Logout & Session Invalidation

```bash
# Logout current profile and revoke server refresh token
secretvault auth logout

# Logout and wipe credentials across all profiles
secretvault auth logout --all
```
