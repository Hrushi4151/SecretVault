# SecretVault CLI (`@secretvault/cli`)

The **SecretVault CLI** is the official developer and CI/CD consumption layer for the multi-tenant SecretVault enterprise secrets platform. It enables runtime in-memory secret injection, encrypted local credential storage, OIDC workload identity authentication, and DevSecOps control plane workflows.

---

## 🚀 Installation

### Global Installation via npm (Official Distribution)

```bash
npm install -g @secretvault/cli
```

Verify installation:

```bash
secretvault --version
secretvault doctor
```

Supported Platforms:
- macOS (Apple Silicon `darwin-arm64` & Intel `darwin-x64`)
- Linux (`linux-x64`)
- Windows (`win32-x64`)
- Requires Java 21+ runtime

---

## 👩‍💻 Human Developer Workflow

```bash
# 1. Authenticate with SecretVault server
secretvault auth login --server http://localhost:8080 --email developer@rally.io

# 2. Inspect available workspaces and set context
secretvault workspace list
secretvault context set --workspace default --project rally --env development
secretvault context get

# 3. Inspect authorized secrets (values are masked in lists)
secretvault secret list

# 4. Run application with in-memory injected secrets (Zero .env file on disk)
secretvault run --env development -- npm run dev

# Or with specific override options
secretvault run --project rally --env development --no-override -- node server.js
```

---

## 🤖 CI/CD Machine Identity & OIDC Workflow

SecretVault supports zero-permanent-credential workload authentication via GitHub Actions, GitLab CI, and generic RFC 7519 OIDC providers.

```bash
# 1. Obtain OIDC token from CI runtime and pipe via stdin (never exposed in process arguments)
echo "$ACTIONS_ID_TOKEN_REQUEST_TOKEN" | secretvault auth oidc \
  --provider-id "$SECRET_VAULT_PROVIDER_ID" \
  --machine-id "$SECRET_VAULT_MACHINE_ID" \
  --token-stdin

# 2. Execute CI/CD application with short-lived session
secretvault run --env staging -- npm test
```

---

## 🔒 Security Architecture

- **Unified Authorization**: All human and machine requests evaluate through the same authoritative `EffectiveAccessService` backend pipeline.
- **In-Memory Injection**: Secrets are injected directly into child process memory via `ProcessBuilder.environment()`. No `.env` files are created or persisted.
- **Encrypted Credential Storage**: Local sessions are secured using AES-256-GCM encryption with PBKDF2 key derivation.
- **No Token Leaks**: OIDC tokens and passwords accept stdin input (`--token-stdin`, `--password-stdin`) to prevent process table leakage. Centralized redaction masks tokens and sensitive headers across all logs.
- **Strict Short TTL**: Machine identity sessions default to 600 seconds, strictly enforced server-side.

---

## 📚 Complete Documentation

- [Phase 9 — Machine Identities & OIDC Workload Authentication](../docs/phase9/README.md)
- [Phase 10 — Developer CLI & Runtime Injection](../docs/phase10/README.md)
- [Command Reference](../docs/phase10/COMMANDS.md)
- [Security & Threat Model](../docs/phase10/SECURITY.md)
- [Troubleshooting & Diagnostics](../docs/phase10/TROUBLESHOOTING.md)

