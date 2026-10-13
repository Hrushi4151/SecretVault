<div align="center">

# 🔌 SecretVault VS Code Extension

[![VS Code](https://img.shields.io/badge/Visual_Studio_Code-007ACC?style=for-the-badge&logo=visual-studio-code&logoColor=white)](https://code.visualstudio.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Seamless secret synchronization, intelligent autocompletion, and pre-commit leak detection directly inside VS Code.**

</div>

---

## ⚡ Features

- 🔍 **Live Workspace Explorer:** Browse workspaces, projects, environments, and secrets directly from the VS Code Activity Bar.
- 💡 **Intelligent Autocompletion:** Auto-complete secret references (`process.env.DB_PASSWORD`, `System.getenv("...")`, `os.environ[...]`) with rich metadata hover cards.
- 🛡️ **Pre-Commit Leak Warning:** Local file scanner warns you before you commit files containing high-entropy credentials or plaintext secrets.
- 🔄 **One-Click Sync:** Trigger cloud provider synchronization and drift detection scans without leaving your IDE.
- 🔒 **Zero Plaintext on Disk:** All secret lookups leverage memory-only ephemeral caching—never persisting decrypted values to `.env` files or workspace storage.

---

## 🚀 Quick Setup

1. Install the extension from the VS Code Marketplace or load it as an unpacked extension from this directory.
2. Open your command palette (`Ctrl+Shift+P` / `Cmd+Shift+P`) and run:
   ```text
   SecretVault: Authenticate
   ```
3. Enter your control plane server URL (e.g. `http://localhost:3000`) and login with your credentials or machine token.
4. Select your active Workspace, Project, and Environment in the status bar item.

---

## ⌨️ Available Commands

| Command | Description |
|---|---|
| `secretvault.login` | Authenticate with the SecretVault control plane |
| `secretvault.switchWorkspace` | Switch the active workspace and project context |
| `secretvault.sync` | Trigger an immediate multi-cloud synchronization |
| `secretvault.scanFile` | Manually scan the current file for leaked secrets |
| `secretvault.viewAudit` | Open the recent audit log for the current secret |
