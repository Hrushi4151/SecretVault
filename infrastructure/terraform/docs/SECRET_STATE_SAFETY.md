# Secret State Safety & Lifecycle Audit

## 1. Executive Summary & Core Invariant

Terraform is an Infrastructure-as-Code client interacting with the SecretVault REST API control plane.

> [!CAUTION]
> **CRITICAL SECURITY NOTICE ON TERRAFORM STATE**:
> Marking an attribute with `Sensitive: true` in Terraform Plugin Framework only redacts it from CLI terminal output (`terraform plan`, `terraform apply`). It **DOES NOT** encrypt the JSON content of the `terraform.tfstate` file stored on disk or in remote backends. SecretVault envelope encryption (AES-256-GCM / KMS) protects secrets at rest in SecretVault databases, but **cannot protect local or remote `.tfstate` files**. Users MUST protect Terraform state files using an encrypted backend (e.g. S3 with KMS SSE, GCS CMEK, HCP Terraform), IAM least privilege, locking, and audit logs.

---

## 2. Explicit 10-Point Security Audit

| # | Audit Question | Answer | Technical Verification / Rationale |
| :--- | :--- | :--- | :--- |
| **1** | **Is plaintext stored in Terraform state?** | **YES (during Create/Update of `secretvault_secret`)** | Standard Terraform Core resource architecture stores configured inputs in the state file. Plaintext is preserved from HCL variables, but is **NEVER** re-fetched from the backend. |
| **2** | **Is `Sensitive: true` only hiding CLI display?** | **YES** | `Sensitive: true` prevents terminal exposure during `plan` and `apply`, but does not encrypt the unencrypted JSON state file. |
| **3** | **Does the provider ever return plaintext from `Read()`?** | **NO** | `Read()` strictly calls `GET /api/v1/workspaces/{ws}/projects/{proj}/environments/{env}/secrets/{id}` which returns metadata only (`SecretMetadataResponse`). `/reveal` is **NEVER** invoked. |
| **4** | **Does `ImportState()` ever retrieve plaintext?** | **NO** | `ImportState()` parses the 4-part URI ID and sets metadata attributes. Subsequent `Read()` fetches metadata only. Plaintext secret is never retrieved over the wire. |
| **5** | **Does `Refresh` ever retrieve plaintext?** | **NO** | Refresh invokes `Read()`, which queries metadata only. Out-of-band modifications are detected via the immutable `version` counter and SHA-256 `fingerprint`. |
| **6** | **Can diagnostics contain plaintext?** | **NO** | All error strings pass through `client.RedactSensitiveInfo()`, which scrubs Bearer tokens, secrets, keys, and passwords. |
| **7** | **Can HTTP error bodies contain plaintext?** | **NO** | API errors returned from SecretVault contain error envelopes (`code`, `message`, `traceId`) and never include decrypted secret ciphertext. |
| **8** | **Can debug logging contain plaintext?** | **NO** | `client.go` does not dump raw request/response payloads in logs. Sensitive attributes are excluded. |
| **9** | **Can request/response logging contain plaintext?** | **NO** | No request/response payload dump is enabled by default. `Authorization` headers are masked. |
| **10** | **Can panic/error strings contain plaintext?** | **NO** | Panic recovery and diagnostic conversions sanitize all exception strings before exposing them. |

---

## 3. Detailed Lifecycle Operations Matrix

```
┌───────────────────────────────────────────────────────────────────────────┐
│                             TERRAFORM LIFECYCLE                           │
└───────────────────────────────────────────────────────────────────────────┘

1. CREATE (terraform apply):
   HCL (var.secret_value) ──[POST /secrets (TLS 1.2+)]──> SecretVault Backend (Encrypted)
                                                                 │
   Terraform State <──[Metadata Response (ID, Version, Dates)]───┘
   (Computes SHA256 Fingerprint for drift detection)

2. READ / REFRESH (terraform plan / refresh):
   Terraform ──[GET /secrets/{id} (Metadata Only)]──> SecretVault Backend
                                                            │
   Terraform State <──[Metadata (Version, UpdatedAt, Status)]┘
   (NEVER calls /reveal; NEVER fetches decrypted plaintext)

3. UPDATE (terraform apply on value change):
   HCL (new_value) ──[PATCH /secrets/{id} (TLS 1.2+)]──> SecretVault Backend (New Version)
                                                                │
   Terraform State <──[Metadata Response (New Version)]─────────┘

4. DELETE (terraform destroy):
   Terraform ──[DELETE /secrets/{id}]──> SecretVault Backend (Soft/Hard Delete)

5. IMPORT (terraform import):
   Terraform Import ──[Parses ws/proj/env/id]──> Read() queries metadata only.
```

---

## 4. Drift Detection Architecture

Because `Read()` strictly avoids querying plaintext values, drift detection operates through cryptographic and versioning invariants:
1. **Backend Version Counter**: Every secret modification in SecretVault increments an immutable `version` integer. If a secret is rotated or updated out-of-band (via UI, CLI, or automated rotation), the version in SecretVault increments.
2. **SHA-256 Digest (`fingerprint`)**: When written via Terraform, the SHA-256 digest of the secret value is computed locally and stored in state (`fingerprint`).
3. Drift is detected deterministically without transmitting decrypted secrets over the network during plan or refresh cycles.
