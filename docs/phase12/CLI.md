# Phase 12: CLI Reference — secretvault scan

## 1. Overview

The `secretvault scan` command provides local and CI scanning capabilities directly from the command line.

---

## 2. Command Synopsis

```bash
secretvault scan [PATH] [OPTIONS]
```

If `PATH` is omitted, the current working directory (`.`) is scanned.

---

## 3. Options Reference

| Option | Type | Description |
| :--- | :--- | :--- |
| `--git-history` | Boolean | Scan full Git commit diff history instead of just working tree |
| `--staged` | Boolean | Scan only staged changes in Git index (ideal for pre-commit hooks) |
| `--all-branches` | Boolean | Scan all Git branches and tags |
| `--depth <N>` | Integer | Limit Git history traversal to `N` commits (default: 500) |
| `--sarif <FILE>`| File Path| Export results in SARIF v2.1.0 JSON format |
| `--fail-on <SEV>`| Enum | Exit with code 1 if findings meet or exceed severity: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW` |
| `--format <FMT>`| String | Output format: `TABLE`, `JSON`, `SARIF` |
| `--workspace-id <ID>` | UUID | Scope findings to a specific remote workspace |
| `--repo-id <ID>`| UUID | Associate findings with a registered repository entity |

---

## 4. Subcommands

### Repository Management
- `secretvault repository list`: List all connected repositories.
- `secretvault repository get <id>`: Show repository connection details and risk metrics.
- `secretvault repository connect --name <n> --url <u>`: Register a new repository.
- `secretvault repository scan <id>`: Trigger a server-side repository scan.

### Finding Triage
- `secretvault finding list [--repo <id>] [--status <st>]`: List detected secret findings.
- `secretvault finding get <id>`: Show finding details and masked evidence.
- `secretvault finding why-exposed <id>`: View the explainability analysis report.
- `secretvault finding confirm <id> [--reason <r>]`: Confirm finding as a real leak.
- `secretvault finding ignore <id> [--reason <r>]`: Ignore finding as accepted risk.
- `secretvault finding remediate <id> --action <a-name>`: Trigger remediation (e.g. `ROTATE_SECRET`).
