# SecretVault Operational Runbook 15: CI/CD Pipeline Credential Compromise

## Incident Summary
Triggered when GitHub Actions repository secrets, deployment tokens, or build pipeline runner credentials are compromised.

---

### Step-by-Step Response Procedure

1. **Detect**: Alert from GitHub secret scanning or suspicious deployment triggered from unauthorized branch/pull request.
2. **Immediate Pipeline Containment**:
   - Temporarily disable GitHub Actions workflow in repository settings.
3. **Revoke GitHub OIDC Role Trust Policy**:
   - Update `aws_iam_role.github_actions_oidc` condition to block the compromised repository or branch.
4. **Revoke All Repository Secrets**:
   - Rotate `GITHUB_TOKEN`, npm tokens, and Maven package publishing credentials.
5. **Inspect Recent Docker Images & Build Artifacts**:
   - Verify SHA-256 digests of images pushed to ECR against commit signatures.
6. **Re-establish Clean OIDC Credentials & Re-enable Workflows**:
   - Resume CI/CD with pinned GitHub Action SHAs.
7. **Audit**: Publish full security disclosure and remediation ledger.
