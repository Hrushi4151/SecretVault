# Phase 12: Detection Engine — Regex, Shannon Entropy & Context Scoring

## 1. Overview

The SecretVault Detection Engine combines three independent signals to maximize recall while suppressing false positives:
1. **Deterministic Regular Expressions**: Bounded, ReDoS-safe patterns matching specific vendor formats.
2. **Shannon Entropy Quantifier**: Information-theoretic randomness measurement for detecting opaque credentials.
3. **Context Analyzer**: Semantic analysis of variable naming, assignment syntax, and test/sample markers.

---

## 2. Shannon Entropy Formulation

Cryptographic secrets (such as private keys, random tokens, and hashed passwords) exhibit high randomness compared to natural language code or repetitive identifiers.

Entropy $H(X)$ is computed as:
$$H(X) = -\sum_{i=1}^{n} P(x_i) \log_2 P(x_i)$$

Where:
- $n$ is the number of distinct characters in candidate string $X$.
- $P(x_i) = \frac{\text{count}(x_i)}{\text{length}(X)}$ is the empirical probability of character $x_i$.

### Adaptive Entropy Thresholds
- **Hexadecimal Candidates** (`[0-9a-fA-F]+`): $H \ge 3.0$ bits per character.
- **Base64 / Alphanumeric Candidates**: $H \ge 4.5$ bits per character.
- Minimum Candidate Length: 16 characters.

Strings with $H < 3.0$ or repeated characters (e.g., `aaaaaaaaaaaaaaaa`) are rejected as low-entropy identifiers.

---

## 3. Context Analysis & Confidence Scoring

The `ContextAnalyzer` assesses surrounding code syntax to assign confidence:

### High-Risk Keywords
Surrounding tokens containing keywords increment detection confidence:
- `password`, `passwd`, `pwd`, `secret`, `token`, `apikey`, `api_key`
- `access_key`, `secret_key`, `client_secret`, `private_key`, `auth_token`
- `jwt_secret`, `db_pass`, `database_password`, `connection_string`, `webhook_secret`

### Test & Placeholder Suppression
Strings containing the following patterns are classified as samples or test fixtures:
- `dummy`, `example`, `sample`, `fake`, `placeholder`, `changeme`, `replace_me`
- `your_api_key_here`, `insert_token_here`, `00000000`, `11111111`, `abcdef`

### Confidence Matrix
| Criteria | Resulting Confidence |
| :--- | :--- |
| Sample pattern matched or in known mock fixture | `LOW` |
| Known vendor prefix + high entropy + keyword assignment | `CRITICAL` / `HIGH` |
| Known vendor prefix without assignment keyword | `HIGH` |
| Generic high entropy without keyword context | `MEDIUM` |
| Test file path (e.g. `src/test/...`, `*.spec.ts`) | Downgraded to `LOW` / `MEDIUM` |

---

## 4. ReDoS Mitigation

All regular expressions are:
1. Anchored with word boundaries (`\b`) or strict delimiters.
2. Formulated without nested or catastrophic backtracking quantifiers (e.g., avoiding `(a+)+`).
3. Evaluated on a line-by-line or bounded substring basis rather than whole-file scans where possible.
