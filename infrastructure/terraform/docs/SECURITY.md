# SecretVault Terraform Provider — Security & Threat Model

## 1. Threat Model & Mitigation Strategy

| Threat Category | Potential Risk | Provider Mitigation |
| :--- | :--- | :--- |
| **Credential Leakage in Logs** | API tokens or secret values leaking into stdout / CI output. | Automated regex redaction in `diagnostics` and `client` packages scrubbing bearer tokens, passwords, and authorization headers. |
| **Plaintext State Leakage** | Plaintext secrets persisting unnecessarily in Terraform state files. | `resource_secret` uses write-only/sensitive attributes and `Read()` strictly pulls non-sensitive metadata without calling `/reveal`. |
| **Insecure TLS Interception** | MITM attacks intercepting API communications. | Provider defaults strictly to `insecure_skip_verify = false`. Supports custom PEM CA bundles and SNI server name verification. |
| **Cross-Tenant IDOR** | Workspace A attempting to modify resources belonging to Workspace B. | Strict hierarchical path parameters paired with authoritative `X-Workspace-ID` header validation on every request. |
| **Stale Token Expiry** | Automation jobs failing due to expired tokens. | `MachineIdentityAuth` provider includes mutex-protected token caching with proactive renewal 60 seconds prior to expiry. |
| **Unbounded Retries / DoS** | Infinite retry loops overloading backend during outages. | Capped retries (default 3), bounded backoff with random jitter, and compliance with `Retry-After` response headers. |

---

## 2. Token & Credential Redaction

All error messages passing through `diagnostics.AddErrorFromClient` are sanitized using `client.RedactSensitiveInfo()`.

Patterns scrubbed automatically include:
- `Bearer [a-zA-Z0-9\-_.]+`
- `token="..."`
- `password="..."`
- `secret="..."`
- `authorization="..."`
- `key="..."`

---

## 3. Remote State Security Hardening

> [!CAUTION]
> **State File Security Responsibility:**
> Marking an attribute `sensitive = true` in Terraform prevents CLI stdout display during plans and applies. However, raw JSON state files (`terraform.tfstate`) will contain whatever attributes Terraform persists.

To guarantee complete end-to-end security:
1. **Remote State Backend**: Always use an encrypted backend (e.g. AWS S3 with AWS KMS CMK encryption, GCP Cloud Storage with CMEK, or Terraform Cloud).
2. **Access Control**: Restrict state file access via strict IAM policies to authorized CI/CD runners only.
3. **State Locking**: Enforce DynamoDB or backend native locking to prevent race conditions during concurrent applies.
