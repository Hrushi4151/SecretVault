# SecretVault — Developer CLI (`secretvault`) Specification

## 1. Overview & Vision

The **SecretVault CLI** (`secretvault`) is the enterprise developer interface designed to inject secrets directly into child process memory at runtime, completely eliminating the need to write plaintext `.env` files to disk.

> **Status:** Phase 10 — Implemented & Production Ready.

For complete Phase 10 documentation, see:
- [Phase 10 Overview](./phase10/README.md)
- [Installation Guide](./phase10/INSTALLATION.md)
- [Authentication & Profiles](./phase10/AUTHENTICATION.md)
- [Command Reference](./phase10/COMMANDS.md)
- [Local Development Guide](./phase10/LOCAL_DEVELOPMENT.md)
- [Environment Files (.env)](./phase10/ENV_FILES.md)
- [Security & Threat Model](./phase10/SECURITY.md)
- [CI/CD & Headless Execution](./phase10/CI_USAGE.md)
- [Architecture & Design](./phase10/ARCHITECTURE.md)
- [Troubleshooting](./phase10/TROUBLESHOOTING.md)

---

## 2. Comprehensive Command Catalog

### 2.1 Authentication & Configuration
- `secretvault auth login` — Interactive non-echo login with MFA challenge (TOTP & Recovery Code) or stdin prompt.
- `secretvault auth oidc` — Exchange OIDC workload identity token for a short-lived machine session.
- `secretvault auth logout` — Securely revokes refresh tokens and clears local credentials.
- `secretvault auth status` — Displays active organization, profile, target server, and token expiration state.
- `secretvault auth whoami` — Displays authenticated caller identity, permissions status, and workspace.
- `secretvault auth profiles` — Lists all configured profile definitions.
- `secretvault auth switch <profile>` — Switches active profile.

### 2.2 Project & Environment Navigation
- `secretvault workspace list` — Lists authorized workspaces.
- `secretvault project list` — Lists accessible projects within workspace.
- `secretvault environment list` — Lists project deployment environments and protection tiers.
- `secretvault context get` / `secretvault context set` — Manages hierarchical context bindings.
- `secretvault dev init` — Bootstraps `.secretvault/project.json` in local directory.

### 2.3 Secret Management, Protection & Diffing
- `secretvault secret list` — Lists secret keys, versions, and statuses (values strictly masked).
- `secretvault secret get <KEY>` — Fetches secret metadata.
- `secretvault secret reveal <KEY>` — Protected plaintext secret reveal enforcing `/reveal-policy`, mandatory audit reason (`-r`), Step-Up challenge (`X-Step-Up-Proof`), and single-use intent token (`X-Reveal-Intent-Token`).
- `secretvault secret create <KEY>` — Creates a new secret version 1 (supports `--stdin`, `--from-file`, `--value-from-env`).
- `secretvault secret set <KEY>` — Creates or updates secret version.
- `secretvault secret update <KEY>` — Appends new version with audit reason.
- `secretvault secret delete <KEY>` — Soft-deletes secret with confirmation prompt (Step-Up protected).
- `secretvault secret versions <KEY>` — Immutable version history.
- `secretvault secret rollback <KEY> --version <V>` — Rolls back secret to historical version as vN+1.
- `secretvault secret rotate <KEY>` — Triggers standard or emergency secret rotation.
- `secretvault secret compromise <KEY>` — Marks secret compromised and triggers immediate revocation and rotation.

### 2.4 Leases & Dynamic Consumers
- `secretvault lease list` / `get` / `renew` / `revoke` — Manages dynamic consumer leases.
- `secretvault consumer list` / `get` / `disable` — Inspects active secret consumer registrations.

### 2.5 Runtime In-Memory Injection (`secretvault run`)
- **Syntax:** `secretvault run [--secret <KEY>...] -- <command> [args...]`
- **Example:** `secretvault run -- npm run dev`
- **Behavior:**
  1. Authenticates against SecretVault API.
  2. Fetches and decrypts authorized secrets directly in RAM.
  3. Spawns child process (`npm run dev`) with injected environment block (no shell wrapper).
  4. Plaintext secrets are never written to disk or recorded in history.
  5. Forwards process signals (`SIGINT`, `SIGTERM`) cleanly and propagates exact child exit codes.
  6. On child process termination, memory buffers are wiped.

### 2.6 Safe `.env` Synchronization
- `secretvault env pull` — Safely pulls secrets into stdout or file (with `.gitignore` check and `chmod 600`).
- `secretvault env push` — Parses `.env` data safely (no shell evaluation) with `--dry-run` diff preview.

### 2.7 Diagnostics & Shell Autocompletion
- `secretvault doctor` — Runs end-to-end environment, network, latency, and credential health checks.
- `secretvault version` — Displays CLI and server versions.
- `secretvault completion <shell>` — Generates completion scripts for `bash`, `zsh`, `fish`, and `powershell`.
