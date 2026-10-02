# SecretVault Phase 5.7.2 — MFA Database Schema & Persistence Architecture

**Document Version:** 1.0  
**Phase:** 5.7.2 (MFA Database Schema & Persistence)  
**Migration Version:** `V10__user_mfa_and_recovery_codes_schema.sql`  
**Security Status:** Audited & Standards-Compliant

---

## 1. Overview & Data Model

Phase 5.7.2 establishes the PostgreSQL persistence layer for SecretVault Multi-Factor Authentication. PostgreSQL serves as the authoritative source of truth for user MFA enrollment status, envelope-encrypted TOTP secrets, and one-way hashed backup recovery codes.

```
┌─────────────────────────────────────────────────────────────┐
│                          users                              │
│  id (UUID PK), email, password_hash, status...              │
└──────────────────────────────┬──────────────────────────────┘
                               │ 1 : 1 (ON DELETE CASCADE)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                        user_mfa                             │
│  id (UUID PK)                                               │
│  user_id (UUID UK) ─────────► references users(id)          │
│  ciphertext (BYTEA)                                         │
│  encrypted_dek (BYTEA)                                      │
│  iv (BYTEA, 12 bytes)                                       │
│  auth_tag (BYTEA, 16 bytes)                                 │
│  key_reference (VARCHAR 255)                                │
│  status (VARCHAR 32) ───────► PENDING_VERIF / ENABLED / DIS │
│  enrolled_at, verified_at, last_used_at                     │
│  failed_attempts (INTEGER, chk >= 0)                        │
└──────────────────────────────┬──────────────────────────────┘
                               │ 1 : N (ON DELETE CASCADE)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                   mfa_recovery_codes                        │
│  id (UUID PK)                                               │
│  user_mfa_id (UUID FK) ─────► references user_mfa(id)       │
│  code_hash (VARCHAR 255) ───► BCrypt-12 hash                │
│  code_index (INTEGER, 0..N)                                 │
│  used (BOOLEAN, default FALSE)                              │
│  used_at (TIMESTAMP)                                        │
│  CONSTRAINT: (user_mfa_id, code_index) UNIQUE               │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Cryptographic Envelope Integration

TOTP secrets are encrypted using the platform's production-grade AES-256-GCM envelope encryption service (`AesGcmEnvelopeEncryptionService`):

```
Plaintext Base32 TOTP Secret (160 bits)
       │
       ▼
[AesGcmEnvelopeEncryptionService.encrypt(secretBytes, "user-mfa:" + userId)]
  ├── Fresh 256-bit DEK generated
  ├── Fresh 96-bit random IV generated
  ├── Authenticated Additional Data (AAD): "user-mfa:<userId>"
  ├── DEK wrapped via active KEK provider (KmsKeyProvider)
  └── Plaintext DEK zeroized in JVM memory
       │
       ▼
Stored in PostgreSQL [user_mfa] table:
  ├── ciphertext: AES-GCM encrypted payload
  ├── encrypted_dek: KEK-wrapped Data Encryption Key
  ├── iv: 12-byte initialization vector
  ├── auth_tag: 16-byte authentication tag
  └── key_reference: KMS key reference identifier
```

### AAD Context Binding Security
The Authenticated Additional Data is explicitly bound to `user-mfa:<userId>`. This mathematically prevents ciphertext transplant attacks where an attacker copies encrypted TOTP material from User A's record into User B's row. Any mismatch between the database user ID and the decrypted AAD causes decryption to fail immediately.

---

## 3. Database Schema Details

### 3.1 `user_mfa` Table
| Column | Type | Nullable | Constraints & Defaults | Description |
|---|---|---|---|---|
| `id` | `UUID` | No | `PRIMARY KEY DEFAULT gen_random_uuid()` | Immutable unique identifier |
| `user_id` | `UUID` | No | `UNIQUE REFERENCES users(id) ON DELETE CASCADE` | Owner account reference |
| `ciphertext` | `BYTEA` | No | | Encrypted TOTP secret bytes |
| `encrypted_dek` | `BYTEA` | No | | Wrapped DEK bytes |
| `iv` | `BYTEA` | No | | 96-bit random GCM IV |
| `auth_tag` | `BYTEA` | No | | 128-bit GCM authentication tag |
| `key_reference` | `VARCHAR(255)` | No | | KMS KEK reference identifier |
| `status` | `VARCHAR(32)` | No | `DEFAULT 'PENDING_VERIFICATION'` | `PENDING_VERIFICATION`, `ENABLED`, `DISABLED` |
| `enrolled_at` | `TIMESTAMPTZ`| No | `DEFAULT CURRENT_TIMESTAMP` | Initial enrollment timestamp |
| `verified_at` | `TIMESTAMPTZ`| Yes | | Timestamp when first OTP code verified |
| `last_used_at` | `TIMESTAMPTZ`| Yes | | Last successful MFA authentication |
| `failed_attempts`| `INTEGER` | No | `DEFAULT 0, CHECK (failed_attempts >= 0)` | Consecutive verification failures |
| `created_at` | `TIMESTAMPTZ`| No | `DEFAULT CURRENT_TIMESTAMP` | Entity creation timestamp |
| `updated_at` | `TIMESTAMPTZ`| No | `DEFAULT CURRENT_TIMESTAMP` | Entity update timestamp |

### 3.2 `mfa_recovery_codes` Table
| Column | Type | Nullable | Constraints & Defaults | Description |
|---|---|---|---|---|
| `id` | `UUID` | No | `PRIMARY KEY DEFAULT gen_random_uuid()` | Immutable unique identifier |
| `user_mfa_id` | `UUID` | No | `REFERENCES user_mfa(id) ON DELETE CASCADE` | Associated MFA record |
| `code_hash` | `VARCHAR(255)` | No | | One-way BCrypt-12 hash of normalized code |
| `code_index` | `INTEGER` | No | `CHECK (code_index >= 0)` | 0-based code index |
| `used` | `BOOLEAN` | No | `DEFAULT FALSE` | Consumption state |
| `used_at` | `TIMESTAMPTZ`| Yes | | Timestamp when code was consumed |
| `created_at` | `TIMESTAMPTZ`| No | `DEFAULT CURRENT_TIMESTAMP` | Record creation timestamp |
| `updated_at` | `TIMESTAMPTZ`| No | `DEFAULT CURRENT_TIMESTAMP` | Record update timestamp |

**Table Constraints:**
- `CONSTRAINT uq_mfa_recovery_code_index UNIQUE (user_mfa_id, code_index)`
- `CONSTRAINT chk_mfa_recovery_code_used CHECK ((used = FALSE AND used_at IS NULL) OR (used = TRUE AND used_at IS NOT NULL))`

---

## 4. Atomic Single-Use Recovery Code Consumption

To eliminate race conditions under concurrent authentication requests, single-use recovery code consumption is executed via an atomic SQL statement in `MfaRecoveryCodeRepository`:

```sql
UPDATE mfa_recovery_codes
SET used = true,
    used_at = :usedAt,
    updated_at = :usedAt
WHERE id = :id
  AND used = false;
```

- **Return Value `1`:** Code was unused and successfully consumed.
- **Return Value `0`:** Code was already used or does not exist (consumption denied).
- Tested and verified under 10 concurrent racing worker threads.

---

## 5. Security Invariants

1. **Zero Plaintext Storage:** Plaintext TOTP secrets, plaintext recovery codes, provisioning URIs, and QR images are **never** stored in database columns.
2. **Account-Level Scoping:** `user_mfa.user_id` is unique. MFA is bound to user accounts, not workspaces or projects.
3. **Cascade Deletion:** Deleting a user permanently purges all associated `user_mfa` and `mfa_recovery_codes` rows, preventing orphaned cryptographic material.
4. **Sanitized Representations:** `UserMfa.toString()` and `MfaRecoveryCode.toString()` omit ciphertext, keys, IVs, and code hashes to prevent accidental log leakage.

---

## 6. Scope Boundary (What is NOT in Phase 5.7.2)

- ❌ MFA login challenge state machine (Phase 5.7.3).
- ❌ Redis challenge storage (Phase 5.7.3).
- ❌ REST Controllers and DTOs (Phase 5.7.4).
- ❌ Frontend MFA Setup / Login UI (Phase 5.7.6).
