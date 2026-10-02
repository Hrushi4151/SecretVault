# SecretVault — Developer CLI (`secretvault`)

The **SecretVault CLI** (`secretvault`) is the enterprise developer interface for runtime secret injection, multi-profile management, safe environment file handling, and local DevSecOps control plane workflows.

---

## Quick Reference

- **Executable Launcher:** `cli/bin/secretvault` (macOS / Linux), `cli/bin/secretvault.bat` (Windows)
- **Shaded Package:** `cli/target/secretvault.jar`

---

## Phase 10 Documentation

For comprehensive guides and architecture specifications:

- [Phase 10 Overview](../docs/phase10/README.md)
- [Installation Guide](../docs/phase10/INSTALLATION.md)
- [Authentication & Profiles](../docs/phase10/AUTHENTICATION.md)
- [Command Reference](../docs/phase10/COMMANDS.md)
- [Local Development Guide](../docs/phase10/LOCAL_DEVELOPMENT.md)
- [Environment Files (.env)](../docs/phase10/ENV_FILES.md)
- [Security & Threat Model](../docs/phase10/SECURITY.md)
- [CI/CD & Headless Execution](../docs/phase10/CI_USAGE.md)
- [Architecture & Design](../docs/phase10/ARCHITECTURE.md)
- [Troubleshooting](../docs/phase10/TROUBLESHOOTING.md)

---

## Quickstart

```bash
# 1. Build CLI
mvn clean package -DskipTests

# 2. Authenticate
./bin/secretvault auth login --email developer@secretvault.io

# 3. Run application with in-memory secrets
./bin/secretvault run -- npm run dev
```
