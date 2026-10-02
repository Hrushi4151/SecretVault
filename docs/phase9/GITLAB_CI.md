# Phase 9: GitLab CI/CD OIDC Integration

## Overview

GitLab CI/CD provides native support for OpenID Connect (OIDC) authentication using ID tokens (JWTs) generated directly within CI/CD pipelines.

---

## 1. Provider Setup in SecretVault

Configure the GitLab CI OIDC Provider in SecretVault:

- **Name**: `GitLab CI/CD` (or `Self-Hosted GitLab`)
- **Type**: `GITLAB_CI`
- **Issuer URL**: `https://gitlab.com` (or `https://gitlab.example.corp`)
- **JWKS URL**: `https://gitlab.com/oauth/discovery/keys` (or `https://gitlab.example.corp/oauth/discovery/keys`)
- **Audience**: `https://vault.example.com` (matches the `aud` configured in `.gitlab-ci.yml`)

---

## 2. GitLab CI Claims Reference

GitLab CI embeds comprehensive project, branch, environment, and user claims into the token payload:

| Claim Key | Description | Example |
| :--- | :--- | :--- |
| `project_path` | Full namespace and project path | `my-org/backend-services/payment-gateway` |
| `project_id` | Unique numerical ID of the project | `12345678` |
| `namespace_id` | Unique numerical ID of the root group | `987654` |
| `ref` | Git branch or tag name | `main` |
| `ref_type` | Reference type (`branch` or `tag`) | `branch` |
| `ref_protected` | `true` if the branch is protected in GitLab | `true` |
| `environment` | GitLab deployment environment tier | `production` |
| `environment_protected` | `true` if environment has approval protection | `true` |
| `pipeline_source`| Trigger source (`push`, `schedule`, `web`, `merge_request_event`) | `push` |
| `user_login` | GitLab username triggering the pipeline | `jdoe` |
| `sub` | Subject identifier | `project_path:my-org/backend-services/payment-gateway:ref_type:branch:ref:main` |

---

## 3. Creating a Trust Policy

In SecretVault, create an `OidcTrustPolicy` for the designated Machine Identity:

### Example: Protected Production Deployment
- **Provider**: `GitLab CI/CD`
- **Policy Name**: `Payment Gateway Production`
- **Claim Rules**:
  1. `project_path` `EQUALS` `my-org/backend-services/payment-gateway`
  2. `ref` `EQUALS` `main`
  3. `ref_protected` `EQUALS` `true`
  4. `environment` `EQUALS` `production`

---

## 4. GitLab CI Workflow Configuration (`.gitlab-ci.yml`)

```yaml
deploy_prod:
  stage: deploy
  environment:
    name: production
  id_tokens:
    SECRETVAULT_ID_TOKEN:
      aud: https://vault.example.com
  script:
    - echo "Authenticating with SecretVault..."
    - |
      AUTH_RESP=$(curl -s -X POST "${SECRETVAULT_URL}/api/v1/auth/oidc/token" \
        -H "Content-Type: application/json" \
        -d "{
          \"oidcProviderId\": \"${SECRETVAULT_PROVIDER_ID}\",
          \"machineIdentityId\": \"${SECRETVAULT_MACHINE_ID}\",
          \"idToken\": \"${SECRETVAULT_ID_TOKEN}\"
        }")
      SV_TOKEN=$(echo "${AUTH_RESP}" | jq -r '.accessToken')
    - |
      API_KEY=$(curl -s "${SECRETVAULT_URL}/api/v1/workspaces/${WORKSPACE_ID}/secrets/${SECRET_ID}/reveal" \
        -H "Authorization: Bearer ${SV_TOKEN}" | jq -r '.value')
      echo "Deploying application using SecretVault secrets..."
```

---

## 5. Security Recommendations for GitLab CI
1. **Require `ref_protected: "true"`**: Ensures only code reviewed and merged into protected branches can authenticate.
2. **Restrict `pipeline_source`**: If scheduled tasks should not access deployment secrets, enforce `pipeline_source` `NOT_EQUALS` `schedule`.
3. **Use Environment Protection**: Leverage GitLab's protected environments with manual deployment approvals.
