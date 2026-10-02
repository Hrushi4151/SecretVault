# SecretVault Developer CLI (`secretvault`) — Phase 10

The **SecretVault CLI** is a secure, developer-first command-line tool built with Java 21, Picocli, and standard REST APIs. It brings the full power of SecretVault's multi-tenant DevSecOps platform directly to the developer workstation and CI/CD pipelines.

---

## Key Highlights

1. **Direct In-Memory Process Secret Injection (`secretvault run`):**
   - Injects authorized secrets directly into child process memory (`npm run dev`, `mvn spring-boot:run`, `python app.py`).
   - Plaintext secrets are **NEVER written to disk** or committed to git.
   - Forwards process signals (`SIGINT`, `SIGTERM`) cleanly and propagates exact exit codes.
2. **Encrypted Local Credential Storage:**
   - Session tokens are stored in an AES-256-GCM encrypted vault using PBKDF2 key derivation.
   - Plaintext passwords and JWT tokens are **NEVER stored in configuration files** or dumped into logs.
3. **Multi-Profile & Multi-Server Support:**
   - Switch seamlessly between development, staging, and enterprise environments (`secretvault auth switch work`).
4. **Context Hierarchy Auto-Discovery:**
   - Resolves active Workspace → Project → Environment context automatically using directory-level `.secretvault/project.json` bindings.
5. **Full Secret Lifecycle & Version Control:**
   - Metadata listing (masked values), explicit reveals (`secretvault secret reveal`), immutable version history, and rollbacks.
6. **Safe `.env` Workflows (`env pull` / `env push`):**
   - Pure data-only parser preventing code evaluation or subshell execution.
   - Diff previews before mutation and automatic `.gitignore` checks with `chmod 600` permissions.
7. **Comprehensive System Diagnostics (`secretvault doctor`):**
   - Real-time connectivity, latency benchmarks, runtime validation, and credential health.

---

## Documentation Index

| Guide | Description |
| :--- | :--- |
| [Installation Guide](./INSTALLATION.md) | How to build, package, install, and add `secretvault` to your PATH. |
| [Authentication & Profiles](./AUTHENTICATION.md) | Interactive login, token auto-refresh, secure AES-256-GCM storage, and profiles. |
| [Complete Command Reference](./COMMANDS.md) | Syntax, parameters, examples, exit codes, and JSON schemas for all commands. |
| [Local Development Guide](./LOCAL_DEVELOPMENT.md) | Step-by-step local developer workflows without `.env` files. |
| [Environment Files & Diffing](./ENV_FILES.md) | Safe `.env` parsing, dry-run diff previews, gitignore warnings, and bulk import. |
| [Security & Threat Model](./SECURITY.md) | Security controls, command injection immunity, memory zeroing, and threat matrix. |
| [CI/CD & Headless Execution](./CI_USAGE.md) | Non-interactive execution in GitHub Actions, GitLab CI, and container environments. |
| [Architecture & Client Design](./ARCHITECTURE.md) | High-level CLI structure, REST client, idempotency, and Phase 9/11 compatibility. |
| [Troubleshooting & Diagnostics](./TROUBLESHOOTING.md) | Diagnostic checks with `secretvault doctor`, common errors, and mitigation steps. |

---

## Quickstart in 60 Seconds

```bash
# 1. Check CLI installation and doctor health
secretvault doctor

# 2. Authenticate
secretvault auth login --email developer@secretvault.io

# 3. Discover workspaces and projects
secretvault workspace list
secretvault project list

# 4. Bind local repository to project context
secretvault dev init --project my-project --environment development

# 5. Run local app with zero plaintext on disk
secretvault run -- npm run dev
```
