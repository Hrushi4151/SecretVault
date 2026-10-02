# SecretVault Phase 5.7.1 — TOTP Cryptographic Engine Architecture

**Document Version:** 1.0
**Phase:** 5.7.1 (Backend Cryptographic & TOTP Engine)
**Security Status:** Audited & Standards-Compliant

---

## 1. Overview & RFC 6238 Compliance

Phase 5.7.1 introduces the pure cryptographic and algorithmic foundation for Multi-Factor Authentication (MFA) in SecretVault. It strictly adheres to:
- **RFC 6238:** Time-Based One-Time Password Algorithm (TOTP).
- **RFC 4226:** HMAC-Based One-Time Password Algorithm (HOTP) for dynamic truncation and code generation.
- **RFC 4648:** Base32 Data Encodings.

This phase is strictly stateless and decoupled from persistence, controllers, Redis challenge storage, and user interfaces.

---

## 2. Core Cryptographic Components

```
                            ┌────────────────────────┐
                            │    TotpProperties      │
                            │ (SHA1, 6 digits, 30s)  │
                            └───────────┬────────────┘
                                        │
┌─────────────────────────┐             ▼             ┌─────────────────────────┐
│         Base32          │◄────  TotpService  ──────►│   RecoveryCodeService   │
│ (RFC 4648 Codec / Val)  │   (RFC 6238 Engine)       │(Unambiguous 60-bit codes│
└─────────────────────────┘             │             │   + BCrypt Hashing)     │
                                        ▼             └─────────────────────────┘
                            ┌────────────────────────┐
                            │    Key Provisioning    │
                            │  (otpauth://totp/...)  │
                            └────────────────────────┘
```

### 2.1 TOTP Parameter Baseline
| Parameter | Default Value | Configurable Range | Rationale |
|---|---|---|---|
| **Algorithm** | `SHA1` (`HmacSHA1`) | `SHA1`, `SHA256`, `SHA512` | Standard authenticator apps (Google Auth, 1Password, Bitwarden) default to SHA1. |
| **Digits** | `6` | `6` or `8` | 6 digits (`000000`–`999999`) provides the optimal balance of security and usability. |
| **Time Step ($X$)** | `30` seconds | `1` to `300` seconds | Standard 30-second rollover window. |
| **Drift Tolerance** | `±1` step ($\pm 30$s) | `0` to `5` steps | Permits previous ($t-1$), current ($t$), and next ($t+1$) windows to tolerate client clock skew. |
| **Secret Entropy** | `160` bits (20 bytes) | `128` to `512` bits | 20 bytes yields 32 unpadded Base32 characters, standard across authenticators. |

### 2.2 Mathematical Algorithm
1. **Time Counter ($T$):**
   $$T = \left\lfloor \frac{\text{unixEpochSeconds}}{30} \right\rfloor$$
2. **Binary Conversion:** $T$ is packed as an 8-byte big-endian integer byte array.
3. **HMAC Calculation:**
   $$\text{Hash} = \text{HMAC}_{\text{algorithm}}(\text{SecretBytes}, T)$$
4. **Dynamic Truncation (RFC 4226 §5.4):**
   - Offset: Last 4 bits of the hash: $\text{offset} = \text{Hash}[|\text{Hash}| - 1] \ \& \ \text{0x0F}$.
   - Binary Code:
     $$\text{Binary} = ((\text{Hash}[\text{offset}] \ \& \ \text{0x7F}) \ll 24) \ | \ ((\text{Hash}[\text{offset}+1] \ \& \ \text{0xFF}) \ll 16) \ | \ ((\text{Hash}[\text{offset}+2] \ \& \ \text{0xFF}) \ll 8) \ | \ (\text{Hash}[\text{offset}+3] \ \& \ \text{0xFF})$$
5. **Digit Formatting:**
   $$\text{OTP} = \text{Binary} \pmod{10^{\text{digits}}}$$
   Formatted as zero-padded string (`%06d`), preserving leading zeros (e.g., `005924`).

---

## 3. Base32 Encoding & Decoding (RFC 4648)

The `Base32` utility provides high-performance encoding and decoding:
- **Alphabet:** `ABCDEFGHIJKLMNOPQRSTUVWXYZ234567` (32 characters, 5 bits/char).
- **Case-Insensitive Decoding:** Automatically maps lowercase input to uppercase in lookup table.
- **Formatting Tolerant:** Strips internal spaces, hyphens, and padding `=` during decoding.
- **Strict Validation:** Rejects illegal characters (`0`, `1`, `8`, `9`, special characters) with sanitized exceptions that never echo sensitive secret data.
- **Bit-Malleability Protection:** Verifies that remaining trailing bits in non-multiple inputs are strictly zero.

---

## 4. Key Provisioning URI Format

Provisioning URIs follow the Key URI format specification:
```
otpauth://totp/{encodedIssuer}:{encodedAccount}?secret={base32Secret}&issuer={encodedIssuer}&algorithm={algorithm}&digits={digits}&period={period}
```

### Example Output:
```
otpauth://totp/SecretVault:alice%40example.com?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=SecretVault&algorithm=SHA1&digits=6&period=30
```

### Security Rules for Provisioning URIs:
1. **Transient Memory Only:** Never persisted in database tables, cached in Redis, or stored in logs.
2. **Strict URL Encoding:** Accounts and issuers with spaces, `@`, `+`, or colons are RFC-3986 URL encoded (`%20`, `%40`, `%2B`, etc.).
3. **No Credential Pollution:** Passwords, tokens, and database IDs are never embedded into the URI.

---

## 5. Backup Recovery Codes Engine

The `RecoveryCodeService` provides secure generation, formatting, normalization, and hashing for single-use recovery codes.

### 5.1 Entropy & Non-Ambiguous Alphabet
- **Format:** `XXXX-XXXX-XXXX` (12 alphanumeric characters in 3 groups of 4).
- **Alphabet:** `23456789ABCDEFGHJKMNPQRSTUVWXYZ` (32 characters).
- **Ambiguity Removal:** Deliberately excludes easily confused characters:
  - `0` (Zero) and `O` (Letter O)
  - `1` (One), `I` (Capital i), and `L` (Letter L)
- **Entropy Calculation:** 12 characters $\times$ 5 bits/character = **60 bits of cryptographic entropy** per code.

### 5.2 Normalization & BCrypt Hashing
- **Normalization:** Converts input to uppercase, strips hyphens, spaces, and underscores (`2345-6789-ABCD` $\to$ `23456789ABCD`).
- **Hashed Storage:** Raw recovery codes are **never** stored plaintext. They are hashed using `BCryptPasswordEncoder(12)`.
- **Constant-Time Matching:** Verifications execute via `passwordEncoder.matches(normalizedCode, storedHash)` to prevent timing side channels.

---

## 6. Security Invariants & Guarantees

| Invariant | Implementation Mechanism |
|---|---|
| **Zero Secret Logging** | Unit tests verify exception messages and logging statements never contain Base32 secrets or recovery codes. |
| **Constant-Time Verification** | TOTP codes are verified using `MessageDigest.isEqual` to eliminate timing side-channels. |
| **Deterministic Clock Injection** | `TotpService` accepts an injectable `java.time.Clock`, allowing exact deterministic unit and boundary testing without modifying system clocks. |
| **Thread Safety** | `TotpService` and `RecoveryCodeService` are fully stateless and thread-safe under concurrent multi-threaded execution. |
| **Standardized RFC Verification** | Verified against official RFC 6238 Appendix B test vectors for SHA1, SHA256, and SHA512 across multiple epoch timestamps. |

---

## 7. Explicit Scope Boundary (What is NOT in Phase 5.7.1)

Phase 5.7.1 is strictly the mathematical and cryptographic engine. The following are deliberately **NOT** implemented in this phase:
- ❌ Database migrations / SQL tables.
- ❌ JPA entities (`UserMfa`, `MfaRecoveryCode`).
- ❌ REST Controllers / HTTP endpoints.
- ❌ Redis MFA challenge lifecycle.
- ❌ Login flow modifications.
- ❌ Frontend UI / QR code rendering.
- ❌ Step-up authentication aspect.

These components will be implemented sequentially in Phase 5.7.2 through Phase 5.7.6.
