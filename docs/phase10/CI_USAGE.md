# SecretVault CLI — CI/CD & Headless Automation

The SecretVault CLI is designed to operate seamlessly in automated, headless environments such as GitHub Actions, GitLab CI/CD, CircleCI, Jenkins, and Kubernetes init containers.

---

## 1. Environment Variable Configuration

In CI environments, pass configuration parameters via environment variables rather than interactive prompts:

| Variable | Description | Example |
| :--- | :--- | :--- |
| `SECRET_VAULT_SERVER` | Server URL | `https://vault.company.com` |
| `SECRET_VAULT_PROFILE` | Profile name | `ci` |
| `SECRET_VAULT_WORKSPACE` | Target Workspace slug | `production-org` |
| `SECRET_VAULT_PROJECT` | Target Project slug | `payment-api` |
| `SECRET_VAULT_ENVIRONMENT` | Target Environment tier | `staging` |
| `SECRET_VAULT_OUTPUT` | Output formatting | `json` |
| `NO_COLOR` | Disables ANSI colors | `1` |

---

## 2. GitHub Actions Integration Example

```yaml
name: Deploy Application

on:
  push:
    branches: [ main ]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Install SecretVault CLI
        run: |
          cd cli
          mvn clean package -DskipTests
          echo "$(pwd)/bin" >> $GITHUB_PATH

      - name: Authenticate CLI
        env:
          SECRET_VAULT_SERVER: ${{ secrets.VAULT_SERVER }}
          VAULT_CI_PASSWORD: ${{ secrets.VAULT_SERVICE_ACCOUNT_PASSWORD }}
        run: |
          echo "$VAULT_CI_PASSWORD" | secretvault auth login \
            --server "$SECRET_VAULT_SERVER" \
            --email "ci-bot@company.com" \
            --password-stdin

      - name: Run Test Suite with Injected Secrets
        env:
          SECRET_VAULT_WORKSPACE: "company-main"
          SECRET_VAULT_PROJECT: "payment-api"
          SECRET_VAULT_ENVIRONMENT: "staging"
        run: |
          secretvault run -- mvn clean test
```

---

## 3. Headless Execution Guidelines

1. **Always use `--password-stdin`:** Ensures secrets are read from pipe streams without prompting on non-TTY stdin.
2. **Always supply `--yes` on mutations:** Destructive operations (like `secret delete` or `env push`) require `--yes` to proceed without interactive confirmation.
3. **Use `--json` for scripts:** Returns parseable JSON structures for jq / python automation.
