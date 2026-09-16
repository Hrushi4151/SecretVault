# SecretVault — Testing Strategy & Quality Standards

## 1. Testing Pyramid

```text
               ▲
              / \       End-to-End & Playwright UI Tests
             /───\
            /     \     Integration & Testcontainers Tests (Postgres / Redis)
           /───────\
          /         \   Security & RBAC Boundary Tests
         /───────────\
        /             \ Spring Boot WebMvc Slice Tests (@WebMvcTest)
       /───────────────\
      /                 \ JUnit 5 & Mockito Unit Tests (Fast JVM)
     ─────────────────────
```

---

## 2. Test Execution Commands

```powershell
# Run all unit and slice tests
mvn -f backend/pom.xml test

# Run a specific test class
mvn -f backend/pom.xml test -Dtest=GlobalExceptionHandlerTest

# Run complete verification suite with packaging
mvn -f backend/pom.xml clean verify
```

---

## 3. Specialized Testing Standards

### 3.1 Security & Multi-Tenancy Tests
- **IDOR Protection:** Assert that a request with valid User A token cannot access Project B belonging to a different organization (`403 Forbidden` / `404 Not Found`).
- **Secret Reveal Auditing:** Verify that every secret reveal endpoint call generates a persistent audit log event without storing plaintext.
- **Sanitized Error Payloads:** Verify that unhandled server exceptions produce generic error messages with `requestId` and zero database/stacktrace leakage.

### 3.2 Cryptographic Verification Tests
- Verify AES-256-GCM generates distinct ciphertexts and unique IVs for identical plaintext inputs.
- Assert that modifying a single bit of ciphertext triggers an `AEADBadTagException` on decryption.

### 3.3 CI Quality Gates
- **Zero Test Failures:** Build fails immediately if any test fails.
- **Minimum Code Coverage:** 80%+ branch coverage required across core security and domain service modules.
