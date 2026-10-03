# SecretVault CLI — Complete Command Reference

This document provides the exhaustive specification for all SecretVault CLI commands.

---

## Global Options

The following options apply to all commands:

| Option | Shorthand | Description |
| :--- | :--- | :--- |
| `--profile` | `-p` | Select authentication profile (default: `default`) |
| `--server` | `-s` | SecretVault server API URL |
| `--workspace` | `-w` | Target Workspace slug or UUID |
| `--project` | | Target Project slug or UUID |
| `--environment`, `--env` | `-e` | Target Environment slug or UUID (e.g. `development`, `staging`, `production`) |
| `--json` | | Output response in structured JSON format |
| `--quiet` | `-q` | Suppress non-essential informational messages |
| `--no-color` | | Disable ANSI terminal color codes |
| `--help` | `-h` | Display usage and options |
| `--version` | `-V` | Display CLI version |

---

## 1. Authentication (`secretvault auth`)

### `secretvault auth login`
Authenticates with the backend server and securely stores encrypted credentials.
```bash
secretvault auth login [--email <email>] [--password-stdin] [--server <url>] [--profile <name>]
```

### `secretvault auth logout`
Revokes refresh token and clears local credentials.
```bash
secretvault auth logout [--all]
```

### `secretvault auth status`
Displays active profile, target server, user identity, and token expiration state.
```bash
secretvault auth status [--json]
```

### `secretvault auth whoami`
Displays caller profile, email, permissions status, and workspace.
```bash
secretvault auth whoami [--json]
```

### `secretvault auth profiles`
Lists all configured profile definitions.
```bash
secretvault auth profiles
```

### `secretvault auth switch <profile>`
Sets the active default profile.
```bash
secretvault auth switch work
```

---

## 2. Workspace Navigation (`secretvault workspace`)

### `secretvault workspace list`
Lists all workspaces where caller has membership.
```bash
secretvault workspace list [--json]
```

### `secretvault workspace get <id-or-slug>`
Displays details for a specific workspace.
```bash
secretvault workspace get default
```

---

## 3. Project Management (`secretvault project`)

### `secretvault project list`
Lists all projects within the resolved workspace.
```bash
secretvault project list [--workspace <slug>] [--json]
```

### `secretvault project get <id-or-slug>`
Displays project metadata and list of environments.
```bash
secretvault project get payment-gateway
```

---

## 4. Environment Discovery (`secretvault environment`)

### `secretvault environment list`
Lists all deployment environments in the active project.
```bash
secretvault environment list [--project <slug>]
```

### `secretvault environment get <id-or-slug>`
Displays environment protection tier and details.
```bash
secretvault environment get production
```

---

## 5. Context Scopes (`secretvault context`)

### `secretvault context get`
Displays the active resolved context hierarchy.
```bash
secretvault context get [--json]
```

### `secretvault context set`
Sets default workspace, project, or environment for the active profile or local repository (`--local`).
```bash
secretvault context set --workspace default --project payment-gateway --environment development [--local]
```

### `secretvault context reset`
Clears stored context defaults.
```bash
secretvault context reset
```

---

## 6. Secret Management (`secretvault secret`)

### `secretvault secret list`
Lists secret keys and version numbers in current environment. **Plaintext values are never shown.**
```bash
secretvault secret list [--search <query>] [--status <ACTIVE|ARCHIVED>]
```

### `secretvault secret get <name-or-id>`
Displays secret metadata. **Plaintext is never printed.**
```bash
secretvault secret get STRIPE_API_KEY
```

### `secretvault secret reveal [name-or-id]`
Explicitly decrypts and prints plaintext secret value to stdout with full Phase 5.8.5 Secret Reveal Protection.
```bash
secretvault secret reveal STRIPE_API_KEY [--version <N>] [--reason <text>] [--yes] [--raw] [--json]
```

**Options:**
- `--reason`, `-r`: Mandatory audit reason (10–500 characters) if enforced by environment / workspace reveal policy. If omitted and required, CLI interactively prompts for the justification.
- `--version`, `-v`: Specific version number to decrypt and reveal (defaults to latest active version).
- `--yes`, `-y`: Bypass interactive confirmation prompt.
- `--raw`: Print raw secret value only without key prefix or formatted metadata.
- `--json`: Output as JSON object including metadata, version, and masked/unmasked indicators.

**Security Controls:**
- Evaluates hierarchical `/api/v1/secrets/{id}/reveal-policy` (DENY, STEP_UP_REQUIRED, AUDIT_LOG_ONLY, ALLOWED).
- Triggers Step-Up Authentication challenge (`X-Step-Up-Proof`) when required by policy.
- Obtains a short-lived single-use reveal intent token (`POST /api/v1/secrets/{id}/reveal-intent`) and passes `X-Reveal-Intent-Token`.
- Directs users to the Web Console if hardware WebAuthn is exclusively mandated.

### `secretvault secret create <name>`
Creates a new secret (Version 1).
```bash
# Interactive non-echo prompt
secretvault secret create DB_PASSWORD

# From local file
secretvault secret create TLS_CERT --from-file cert.pem

# From host environment variable
secretvault secret create API_TOKEN --value-from-env HOST_TOKEN

# From stdin
echo "secret_val" | secretvault secret create DB_PASS --stdin
```

### `secretvault secret set <name>`
Creates the secret if new, or appends a new version if existing.
```bash
echo "new_val" | secretvault secret set DB_PASSWORD --stdin
```

### `secretvault secret update <name-or-id>`
Appends a new version to an existing secret.
```bash
secretvault secret update STRIPE_API_KEY --stdin --reason "Quarterly rotation"
```

### `secretvault secret delete <name-or-id>`
Soft-deletes a secret with confirmation prompt.
```bash
secretvault secret delete STRIPE_API_KEY [--yes]
```

### `secretvault secret versions <name-or-id>`
Lists immutable version history for a secret.
```bash
secretvault secret versions STRIPE_API_KEY
```

### `secretvault secret rollback <name-or-id> --version <N>`
Rolls back a secret to historical version N as a brand new version N+1.
```bash
secretvault secret rollback STRIPE_API_KEY --version 1 --reason "Emergency rollback"
```

---

## 7. Process Injection (`secretvault run`)

Injects authorized secrets directly into child process environment:
```bash
secretvault run [--secret <KEY>...] [--override] -- <command> [args...]

# Examples:
secretvault run -- npm run dev
secretvault run -- mvn spring-boot:run
secretvault run --secret DB_PASSWORD -- python3 app.py
```

---

## 8. Environment File Sync (`secretvault env`)

### `secretvault env pull`
Pulls environment secrets into stdout or safely to a local `.env` file.
```bash
# Output to terminal in shell format
secretvault env pull --format shell

# Output safely to file (checks .gitignore, sets chmod 600)
secretvault env pull --output .env --yes
```

### `secretvault env push`
Pushes local `.env` key-value pairs to the environment.
```bash
# Preview diff without mutating remote secrets
secretvault env push --file .env --dry-run

# Execute push
secretvault env push --file .env --yes
```

---

## 9. Diagnostics & System (`secretvault doctor`, `version`, `completion`)

### `secretvault doctor`
Runs health checks against the runtime, config, credential store, and server.
```bash
secretvault doctor [--verbose] [--json]
```

### `secretvault version`
Displays CLI and server build versions.
```bash
secretvault version
```

### `secretvault completion <shell>`
Generates shell autocompletion script for `bash`, `zsh`, `fish`, or `powershell`.
```bash
secretvault completion zsh
```
