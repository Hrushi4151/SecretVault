# SecretVault — Developer CLI (`secretvault`) Specification

## 1. Overview & Vision

The **SecretVault CLI** (`secretvault`) is a primary developer interface designed to inject secrets directly into child process memory at runtime, completely eliminating the need to write plaintext `.env` files to disk.

> **Status:** Phase 8/9 Roadmap (PLANNED).

---

## 2. Comprehensive Command Catalog [PLANNED]

### 2.1 Authentication & Configuration
- `secretvault login` — Interactive OAuth/browser login or API token prompt.
- `secretvault logout` — Clears stored local session tokens.
- `secretvault init` — Links local git repository to a SecretVault project.
- `secretvault status` — Displays active organization, project, environment, and CLI version.

### 2.2 Project & Environment Navigation
- `secretvault project select <project-slug>` — Switches active project context.
- `secretvault env switch <development|staging|production>` — Switches active environment.

### 2.3 Secret Management & Diffing
- `secretvault secrets list` — Lists secret keys, descriptions, and sync status (values masked).
- `secretvault secrets get <KEY_NAME>` — Fetches decrypted secret value to terminal (requires `secret.reveal` permission).
- `secretvault secrets set <KEY>=<VALUE>` — Creates or updates a secret version.
- `secretvault secrets delete <KEY>` — Deletes a secret with confirmation prompt.
- `secretvault diff --env staging --env production` — Diffs secret keys and versions across environments (no plaintext revealed).
- `secretvault history <KEY>` — Displays version history and change reasons.
- `secretvault rollback <KEY> --version <V>` — Rolls back secret to a specified prior version.

### 2.4 Runtime In-Memory Injection (`secretvault run`)
- **Syntax:** `secretvault run --env <environment> -- <command>`
- **Example:** `secretvault run --env development -- npm run dev`
- **Behavior:**
  1. Authenticates against SecretVault API.
  2. Fetches and decrypts environment secrets directly in RAM.
  3. Spawns child process (`npm run dev`) and passes secrets via process environment block.
  4. Plaintext secrets are never written to disk or recorded in `.bash_history`.
  5. On child process termination, memory buffers are wiped.

### 2.5 Diagnostics, Security & CI Headless Mode
- `secretvault doctor` — Verifies workstation health, network latency, and token validity.
- `secretvault scan` — Scans local repository for hardcoded secrets and API keys before commit.
- `secretvault audit` — Displays recent local developer actions.
- `secretvault run --ci` — Headless execution utilizing machine identity tokens (`SECRETVault_SA_KEY` or OIDC).
