# SecretVault — AI Collaboration & Engineering Rules

> **Audience:** AI Coding Assistants (Antigravity and other LLM agents) & Human Engineers.  
> **Mission:** Build and maintain **SecretVault** as a high-security, resilient DevSecOps Secret Management & Security Control Plane.

---

## 1. Core Operating Principles

1. **Security First, Always**: Never compromise security for speed or convenience.
2. **Zero Plaintext Secret Storage**: Plaintext secret values MUST NEVER be stored in the database, logged in logs/metrics, included in API error responses, output in AI prompts, or sent over unencrypted channels.
3. **Modular Monolith Discipline**: Maintain strict domain boundaries in `com.secretvault.<domain>`. Do not cross domain borders without well-defined interfaces or application services.
4. **Senior Engineering Standard**: Code must be safe, readable, tested, observable, and strictly typed. No shortcuts.

---

## 2. Mandatory Pre-Implementation Workflow

Before generating or editing ANY code, every AI agent MUST:

1. **SYNC & INSPECT**:
   - Check git status (`git status`).
   - Pull/rebase latest changes from `main`.
   - Inspect files that will be touched.
   - Check git history for recent changes by team members.
2. **READ RELEVANT SPECS**:
   - `README.md`
   - `AI_RULES.md`
   - `PROJECT_STATUS.md`
   - `docs/ARCHITECTURE.md`
   - `docs/SECURITY.md`
   - `docs/DATABASE.md`
   - `docs/API.md`
   - `docs/GIT_WORKFLOW.md`
   - `docs/TEAM_OWNERSHIP.md`
3. **PLAN & COMMUNICATE**:
   - Check for existing patterns in the codebase.
   - Plan minimal, high-impact edits.
   - Never replace entire working files when a targeted chunk replacement is sufficient.

---

## 3. Forbidden Actions

- ❌ **NEVER commit secrets, API keys, passwords, private keys, or tokens.**
- ❌ **NEVER log plaintext secret values** (use masking like `••••••••` or hashes/fingerprints).
- ❌ **NEVER trust frontend IDs or inputs**; always enforce tenant checks and RBAC server-side.
- ❌ **NEVER expose JPA Entities directly via REST controllers**; always use DTOs.
- ❌ **NEVER alter applied Flyway migrations**; always add a new forward migration (`V<N>__*.sql`).
- ❌ **NEVER make AI a hard dependency** for core secret retrieval or storage.
- ❌ **NEVER send plaintext production secrets to external LLM APIs.**
- ❌ **NEVER create premature microservices.** Stay within the modular monolith architecture.
- ❌ **NEVER blindly overwrite merge conflicts or working code.**

## 4. Code Quality & Implementation Standards

### Backend (Java 21 / Spring Boot 3)
- **Domain Organization**: Group classes by domain (`com.secretvault.<domain>`), containing sub-packages for `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `exception`.
- **Validation**: Use Jakarta Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Size`, `@Pattern`) on all incoming request DTOs.
- **Error Handling**: Throw domain exceptions mapped by `GlobalExceptionHandler`. Return standardized error payloads (`ErrorResponse`).
- **Authorization**: Enforce method/endpoint level security (`@PreAuthorize` or tenant verification filters).
- **Audit Logging**: Every sensitive action (create, update, reveal, rotate, delete, sync) MUST record an audit event without logging the secret value.
- **Correlation ID**: Every HTTP request must be tagged with a correlation ID (`X-Correlation-ID`) tracked in MDC logs.
- **Testing Standards**: Every PR/feature must have unit tests (JUnit 5, Mockito) and slice/integration tests where applicable.

### Frontend (React / TypeScript / Tailwind CSS)
- **Stitch Design Source of Truth**: The designs in `stitch_secretvault_devsecops_platfor/` are the definitive visual source of truth. NEVER redesign UI from scratch.
- **Inspect Before Implement**: Always locate and inspect the corresponding Stitch folder before writing any frontend code.
- **Reusable Component First**: Extract reusable components (`AppShell`, `DataTable`, `SecretTable`, `StatusBadge`, `Modal`, `PageHeader`, `WorkspaceSwitcher`, etc.) instead of building monolithic isolated pages.
- **Visual Baseline**: Preserve Apple/iOS glassmorphism, neutral charcoal background, purple primary brand accent, semantic alerts, Inter/Geist typography, monospace code values, and Lucide icons.
- **Design Priority**:
  1. Security and correctness
  2. Existing SecretVault architecture
  3. Stitch designs in `stitch_secretvault_devsecops_platfor/`
  4. Established SecretVault design system
  5. Reusable component patterns
- **No Premature Batch Generation**: Do NOT generate all 126 screens at once. Implement incrementally per roadmap phase with real API connectivity, error handling, loading states, and authorization checks.


---

## 5. Definition of Done (DoD)

A feature is done ONLY when:
1. Code compiles and satisfies Java 21 / Spring Boot and React/TypeScript standards.
2. Tests pass (unit, security edge cases, validation).
3. Authorization & multi-tenant isolation are verified.
4. Input validation and error handling are in place.
5. Frontend matches Stitch visual intent with responsive states (loading, error, empty).
6. No sensitive data is logged or exposed.
7. Documentation in `docs/` and `PROJECT_STATUS.md` is updated.
8. CI pipeline passes completely.

