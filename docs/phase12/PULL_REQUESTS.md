# Phase 12: Pull Requests & Pre-Commit Hook Integration

## 1. Overview

Preventing secrets from entering source repositories at commit and PR time is exponentially cheaper and safer than post-merge remediation. SecretVault provides pre-commit hooks and pull request commit status checks.

---

## 2. Pre-Commit Hooks (Client-Side Gate)

Developers can install the SecretVault pre-commit hook in their local repositories:

```bash
#!/usr/bin/env bash
# .git/hooks/pre-commit
set -e

echo "[SecretVault] Scanning staged changes for credentials..."
secretvault scan --staged --fail-on=HIGH
```

### Key Flags for Pre-Commit
- `--staged`: Scans only files currently in the Git index (staged via `git add`), ignoring unstaged work-in-progress edits.
- `--fail-on=HIGH`: Exits with code 1 if any finding with severity $\ge$ HIGH is detected, immediately aborting the commit before plaintext touches the Git object database.

---

## 3. Pull Request Automated Checks (Server-Side Gate)

When a pull request is opened or updated, SecretVault's Webhook ingestion evaluates the PR:

1. **Commit Diff Retrieval**: Scans only the additions (`+` lines) between base ref (e.g. `main`) and head ref (e.g. `feature/billing`).
2. **Evaluation Against Repository Policy**: Assesses finding severity against the repository's configured `blockPullRequestsOnLeak` setting.
3. **Commit Status Check Update**: Posts a commit check status back to GitHub/GitLab:
   - State: `success` (No leaks detected) or `failure` (High/Critical secret detected).
   - Target URL: Deep-link to SecretVault's "Why Exposed" finding page.
   - Summary: E.g., `SecretVault detected 1 CRITICAL secret (Stripe Live Secret Key) in billing.js:42`.

---

## 4. GitHub Actions Pre-Merge Workflow Example

```yaml
name: SecretVault PR Scan
on: [pull_request]

jobs:
  scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - name: Install SecretVault CLI
        run: curl -sSL https://get.secretvault.io | sh
      - name: Run Secret Leak Scan
        run: secretvault scan --git-history --sarif=results.sarif --fail-on=CRITICAL
      - name: Upload SARIF to GitHub Security tab
        uses: github/codeql-action/upload-sarif@v3
        if: always()
        with:
          sarif_file: results.sarif
```
