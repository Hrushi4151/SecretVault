# Secret State Safety & Write-Only Lifecycle

## 1. The Core State Safety Challenge

In traditional Terraform providers for secrets management, defining a secret resource often results in the secret value being stored in plaintext in the `.tfstate` file and re-queried across every plan/refresh cycle.

If state storage is unencrypted or accessed by developers with low-privilege access, this creates an unauthorized lateral reveal vector that bypasses JIT approvals and access review policies.

---

## 2. SecretVault's Zero-Plaintext Design

The SecretVault provider addresses this with a **Zero-Plaintext Read Lifecycle**:

```
[ Terraform Apply (Create/Update) ]
                 │
                 ├── Sends: POST /secrets { name, value, contentType }
                 │
                 ▼
     [ SecretVault Backend ]
                 │
                 ├── AES-GCM-256 Envelope Encrypts Value
                 ├── Creates Immutable Version in Database
                 └── Returns SecretMetadataResponse { id, name, version, status, ... }
                 │
                 ▼
[ Terraform State ]
   - id: 123e4567-...
   - name: DATABASE_URL
   - version: 1
   - fingerprint: sha256(value)
   - value: (preserved from configuration, marked Sensitive: true)

-------------------------------------------------------------------------

[ Terraform Plan / Refresh ]
                 │
                 ├── Sends: GET /secrets/{id} (Metadata Endpoint)
                 │
                 ▼
     [ SecretVault Backend ]
                 └── Returns SecretMetadataResponse (NO PLAINTEXT / NO REVEAL AUDIT)
                 │
                 ▼
[ Terraform State ]
   - Updates version & timestamps
   - NEVER fetches decrypted plaintext across the network
```

---

## 3. Drift Detection via Fingerprints

Because `Read()` never queries plaintext, how does Terraform detect if a secret was modified out-of-band?

1. **Version Number**: When a secret is modified in SecretVault (e.g. via UI or CLI), the backend increments the immutable `version` counter.
2. **Fingerprint**: The provider calculates a SHA-256 digest (`fingerprint`) when writing the secret.
3. If the backend version or fingerprint differs, Terraform detects external drift and prompts to synchronize or update without ever transmitting plaintext in the refresh phase.
