# Phase 12: Secret Patterns Catalog & Validation Rules

## 1. Overview

SecretVault scans for credentials across 14 high-value categories. Each pattern is designed with boundary anchors, character class constraints, and ReDoS safety.

---

## 2. Supported Detector Matrix

| ID | Secret Type | Default Severity | Pattern / Signature | Validation Strategy |
| :--- | :--- | :--- | :--- | :--- |
| **AWS-01** | AWS Access Key ID | `HIGH` | `\b(AKIA[0-9A-Z]{16})\b` | Format check & STS validation |
| **AWS-02** | AWS Secret Key | `CRITICAL` | `(?i)(?:aws_secret_access_key\|aws_secret_key\|secret_access_key)\s*[=:]\s*['"]?([A-Za-z0-9/+=]{40})['"]?` | High entropy check ($H \ge 4.5$) |
| **GH-01** | GitHub Token (PAT / Fine-Grained) | `CRITICAL` | `\b(gh[pous]_[0-9a-zA-Z]{36}\|github_pat_[0-9a-zA-Z_]{22,82})\b` | Non-destructive GitHub `/user` probe |
| **GL-01** | GitLab PAT | `CRITICAL` | `\b(glpat-[0-9a-zA-Z_\-]{20,40})\b` | GitLab API `/api/v4/user` |
| **GCP-01** | Google Cloud API Key | `HIGH` | `\b(AIza[0-9A-Za-z_\-]{35})\b` | Google API key format validation |
| **SLACK-01** | Slack Bot / User Token | `HIGH` | `\b(xox[baprs]-[0-9]{10,13}-[0-9]{10,13}-[a-zA-Z0-9]{24,34})\b` | Slack `auth.test` non-destructive call |
| **STRIPE-01** | Stripe Live Key | `CRITICAL` | `\b([sr]k_live_[0-9a-zA-Z]{24,34})\b` | Stripe `/v1/balance` check |
| **TWILIO-01** | Twilio API Key / SID | `HIGH` | `\b(AC[0-9a-fA-F]{32}\|SK[0-9a-fA-F]{32})\b` | Hex length & character class check |
| **SENDGRID-01**| SendGrid API Key | `HIGH` | `\b(SG\.[0-9a-zA-Z_\-]{22}\.[0-9a-zA-Z_\-]{43})\b` | SendGrid 3-part format check |
| **SSH-01** | SSH / RSA Private Key | `CRITICAL` | `(-----BEGIN (?:RSA\|OPENSSH\|DSA\|EC\|PGP)? PRIVATE KEY-----[\s\S]*?-----END ...)` | PEM header and trailer structure |
| **DB-01** | Database URI with Credentials | `CRITICAL` | `\b(?:postgres\|postgresql\|mysql\|mongodb(?:\+srv)?\|redis)://([^:]+):([^@\s'"]+)@([^/\s'"]+)/[^\s'"]+` | Connection string URI parser |
| **JWT-01** | JSON Web Token (JWT) | `MEDIUM` | `\b(eyJ[A-Za-z0-9-_]{10,}\.eyJ[A-Za-z0-9-_]{10,}\.[A-Za-z0-9-_]{10,})\b` | 3 Base64URL dot-separated segments |
| **GEN-01** | Hardcoded Password | `HIGH` | `(?i)(?:password\|passwd\|pwd\|db_pass)\s*[=:]\s*['"]([^'"\r\n\t\f\v]{8,64})['"]` | Assignment syntax context |
| **GEN-02** | Hardcoded API Key | `HIGH` | `(?i)(?:api_key\|apikey\|secret_key\|client_secret)\s*[=:]\s*['"]([^'"\r\n\t\f\v]{12,128})['"]` | Assignment syntax + Shannon entropy |

---

## 3. Masking Rules (`SecretFingerprinter`)

To uphold the zero-plaintext invariant, tokens are masked using deterministic vendor-specific rules:

1. **Prefixed Tokens** (e.g. `AKIA...`, `ghp_...`, `sk_live_...`):
   - Retain prefix (up to 4–8 characters).
   - Insert 12 asterisks (`************`).
   - Retain last 4 characters.
   - Example: `AKIAIOSFODNN7EXAMPLE` $\longrightarrow$ `AKIA************MPLE`.

2. **PEM Private Keys**:
   - Retain first header line: `-----BEGIN PRIVATE KEY----- [MASKED] -----END PRIVATE KEY-----`.

3. **Database URIs**:
   - Redact password segment: `postgres://admin:********@db.internal:5432/app`.

4. **Cryptographic Fingerprint**:
   - $Fingerprint = \text{SHA-256}(\text{trimmed raw secret})$.
   - 64-character lowercase hexadecimal string used for deduplication, allowlisting, and inventory matching.
