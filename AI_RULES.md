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

## 2. Mandatory Git Synchronization & Pre-Implementation Protocol

> **ABSOLUTE RULE:** The GitHub repository (`origin/main`) is the shared source of truth. Because two developers and two AI coding agents are working on the same repository, **NEVER assume that the local repository is up to date.**

### 2.1 Complete Lifecycle Workflow

```text
                 START PHASE / TASK
                         │
                         ▼
                    GIT STATUS
                         │
                         ▼
                 FETCH ALL REMOTES
                         │
                         ▼
               CHECK BRANCH + HISTORY
                         │
                         ▼
                SYNC WITH origin/main
                         │
                         ▼
               CHECK TEAMMATE CHANGES
                         │
                         ▼
                  READ DOCUMENTS
                         │
                         ▼
               INSPECT STITCH DESIGN
                         │
                         ▼
                 INSPECT TARGET FILE
                         │
                         ▼
                      PLAN
                         │
                         ▼
                    IMPLEMENT
                         │
                         ▼
                       TEST
                         │
                         ▼
                 SECURITY REVIEW
                         │
                         ▼
                REVIEW GIT DIFF
                         │
                         ▼
                      COMMIT
                         │
                         ▼
                       PUSH
                         │
                         ▼
                        PR
                         │
                         ▼
                     MERGE MAIN
                         │
                         ▼
                 BOTH MEMBERS SYNC
                         │
                         ▼
              FULL PHASE INTEGRATION
                         │
                         ▼
                   PHASE SIGN-OFF
                         │
                         ▼
                    NEXT PHASE
```

---

### 2.2 Before Every Phase
Before starting ANY phase:
```bash
git status
git fetch --all --prune
git branch -a
git log --oneline --decorate -15
```
Then synchronize with the latest `origin/main`. If working tree is clean:
```bash
git checkout main
git pull --rebase origin main
```
Then create/update the feature branch for the phase. **Do NOT begin implementation until synchronization is complete.**

---

### 2.3 Before Every Task
Even if the phase has started, before beginning each new task:
```bash
git status
git fetch --all --prune
git log --oneline --decorate -15
```
Check whether `main` has changed. Synchronize feature branch before implementing:
```bash
git fetch origin
git rebase origin/main
```
Do not continue if there are unresolved conflicts.

---

### 2.4 Before Modifying Any File
Before editing ANY existing file:
```bash
git status
git fetch origin
git diff
git log --oneline -- <file>
git diff origin/main...HEAD -- <file>
```
Then inspect the actual file and determine:
- Is this file already modified locally?
- Did the teammate recently modify it?
- Has the file changed on `origin/main`?
- Is the file shared/high-conflict?
- Will my change conflict with teammate's work?
- Can I avoid modifying this file?
- Can I make a smaller, targeted change?

---

### 2.5 Never Blindly Overwrite
Never replace an existing file wholesale simply because an AI generated a new version.
**Preserve:**
- Teammate changes
- Existing functionality
- Existing architecture
- Existing components
- Existing API contracts
- Existing security controls
- Existing documentation

If a file contains changes from another developer, integrate with them rather than overwriting.

---

### 2.6 High-Conflict Files
Always perform extra inspection before modifying:
`pom.xml`, `package.json`, `package-lock.json`, `docker-compose.yml`, `application.yml`, `application.properties`, security configuration, Flyway migrations, GitHub Actions workflows, shared DTOs, API contracts, global exception handling, shared utilities, shared frontend components, routing, and design-system components.

**Protocol for High-Conflict Files:**
```text
FETCH → INSPECT HISTORY → INSPECT CURRENT CHANGES → CHECK TEAMMATE WORK → MAKE MINIMAL CHANGE → TEST
```

---

### 2.7 Handling Local Changes & Destructive Operations
If `git status` shows modifications that were not created during the current task: **STOP before editing.**
- ❌ **NEVER run:** `git reset --hard`, `git checkout .`, `git clean -fd`, `git push --force`.
- First determine whether changes are previous work, teammate work, uncommitted work, or generated files. Preserve them.

---

### 2.8 Before Commit
```bash
git status
git diff
git diff --check
```
Review every changed file. Verify diff contains ONLY intended changes. No debug code, no secrets, no unintended dependencies. Run tests (`mvn test` / `npm test`).

---

### 2.9 Before Push
```bash
git fetch origin
git status
git diff
git log --oneline --decorate -10
```
If `main` changed while working:
```bash
git fetch origin
git rebase origin/main
```
Re-run tests after rebase, then:
```bash
git push -u origin <branch>
```

---

### 2.10 Phase Transition Rule
```text
PHASE N IMPLEMENTATION ➔ MEMBER 1 & 2 COMPLETE ➔ PRs REVIEWED ➔ MERGED INTO MAIN ➔ BOTH MEMBERS SYNC (PULL MAIN) ➔ FULL INTEGRATION TEST ➔ SECURITY REVIEW ➔ STITCH UI REVIEW ➔ DOCS UPDATE ➔ PHASE SIGN-OFF ➔ START NEXT PHASE
```

---

### 2.11 Mandatory Specs to Read
Before coding in any domain, read:
1. `README.md` & `PROJECT_STATUS.md`
2. `docs/ARCHITECTURE.md` & `docs/SECURITY.md`
3. `docs/DATABASE.md` & `docs/API.md`
4. `docs/TEAM_OWNERSHIP.md` & `docs/GIT_WORKFLOW.md`
5. `stitch_secretvault_devsecops_platfor/` (for UI screens)

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

