# Phase 9: GitHub Actions OIDC Integration

## Overview

GitHub Actions allows workflows to authenticate with cloud services and SecretVault using short-lived OpenID Connect (OIDC) tokens, eliminating the need to store long-lived credentials in GitHub repository secrets.

---

## 1. Provider Setup in SecretVault

Configure the GitHub Actions OIDC Provider in SecretVault:

- **Name**: `GitHub Actions`
- **Type**: `GITHUB_ACTIONS`
- **Issuer URL**: `https://token.actions.githubusercontent.com`
- **JWKS URL**: `https://token.actions.githubusercontent.com/.well-known/jwks`
- **Audience**: `secretvault` (or your custom organization audience)

---

## 2. GitHub Actions Claims Reference

GitHub Actions injects rich workflow metadata claims into the OIDC token:

| Claim Key | Description | Example |
| :--- | :--- | :--- |
| `repository` | Organization and repository name | `Hrushi4151/Rally` |
| `repository_owner` | GitHub username or organization | `Hrushi4151` |
| `ref` | Git branch or tag reference | `refs/heads/main` |
| `ref_type` | Type of ref | `branch` or `tag` |
| `actor` | GitHub user who triggered the run | `octocat` |
| `workflow` | Workflow name or filename | `Release Pipeline` |
| `job_workflow_ref`| Target reusable workflow reference | `Hrushi4151/Rally/.github/workflows/deploy.yml@refs/heads/main` |
| `environment` | GitHub Environment name | `production` |
| `sub` | Subject identifier | `repo:Hrushi4151/Rally:ref:refs/heads/main` |

---

## 3. Creating a Trust Policy

In SecretVault, create an `OidcTrustPolicy` attached to your Machine Identity (e.g., `github-rally-ci`):

### Example: Main Branch Deployment
- **Provider**: `GitHub Actions`
- **Policy Name**: `Rally Production CI`
- **Claim Rules**:
  1. `repository` `EQUALS` `Hrushi4151/Rally`
  2. `ref` `EQUALS` `refs/heads/main`
  3. `environment` `EQUALS` `production`

---

## 4. GitHub Actions Workflow Example

Add the `id-token: write` permission to your GitHub Actions workflow:

```yaml
name: Deploy Application

on:
  push:
    branches: [ "main" ]

permissions:
  id-token: write # Mandatory for requesting the JWT
  contents: read

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Fetch SecretVault Machine Token
        id: secretvault-auth
        run: |
          # 1. Request GitHub OIDC ID Token with audience 'secretvault'
          OIDC_TOKEN=$(curl -sLS "${ACTIONS_ID_TOKEN_REQUEST_URL}&audience=secretvault" \
            -H "User-Agent: actions/oidc-client" \
            -H "Authorization: Bearer ${ACTIONS_ID_TOKEN_REQUEST_TOKEN}" | jq -r '.value')

          # 2. Exchange OIDC token for SecretVault Machine Session
          AUTH_RESP=$(curl -s -X POST "https://vault.internal.net/api/v1/auth/oidc/token" \
            -H "Content-Type: application/json" \
            -d "{
              \"oidcProviderId\": \"${{ vars.SECRETVAULT_PROVIDER_ID }}\",
              \"machineIdentityId\": \"${{ vars.SECRETVAULT_MACHINE_ID }}\",
              \"idToken\": \"${OIDC_TOKEN}\"
            }")

          SV_TOKEN=$(echo "${AUTH_RESP}" | jq -r '.accessToken')
          echo "::add-mask::${SV_TOKEN}"
          echo "token=${SV_TOKEN}" >> "$GITHUB_OUTPUT"

      - name: Retrieve Database Secret
        run: |
          SECRET_VAL=$(curl -s "https://vault.internal.net/api/v1/workspaces/${{ vars.WORKSPACE_ID }}/secrets/${{ vars.SECRET_ID }}/reveal" \
            -H "Authorization: Bearer ${{ steps.secretvault-auth.outputs.token }}" | jq -r '.value')
          echo "::add-mask::${SECRET_VAL}"
          echo "Deploying with DB credentials..."
```

---

## 5. Security Recommendations for GitHub Actions
1. **Always pin `repository` and `ref`**: Avoid matching on `repository_owner` alone, as any repository in your organization could assume the machine identity.
2. **Restrict Fork Pull Requests**: Ensure `job_workflow_ref` or `ref` matches `refs/heads/main` to avoid untrusted forks claiming secrets.
3. **Use GitHub Environments**: Combine with GitHub Environment protection rules (e.g. required reviewers) for high-sensitivity production secrets.
