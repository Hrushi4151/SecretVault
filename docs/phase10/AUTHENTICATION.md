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
    ├─► Success: JWT Access Token + Refresh Token
    ▼
Encrypted Credential Store (AES-256-GCM + PBKDF2)
    │
    └─► Tokens saved encrypted in ~/.credentials.enc
```

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
