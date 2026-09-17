# SecretVault — Mandatory Git Synchronization & Collaboration Workflow

> **MANDATORY FOR ALL DEVELOPERS & AI CODING AGENTS.**
> The GitHub repository (`origin/main`) is the single shared source of truth. Because two engineers and AI pair programmers develop on the same codebase, **NEVER assume your local repository is up to date.**

---

## 1. Full Lifecycle Workflow

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

## 2. Branch Strategy

The `main` branch is protected and represents production-ready code. Direct development on `main` is strictly prohibited.

```text
main (Protected)
  │
  ├── feature/<task-description>
  ├── fix/<bug-description>
  ├── security/<hardening-description>
  └── infra/<docker-or-ci-description>
```

---

## 3. Mandatory Synchronization Rules

### 3.1 Before Every Phase
Before starting ANY development phase:
```bash
git status
git fetch --all --prune
git branch -a
git log --oneline --decorate -15
```
Then synchronize with latest `origin/main`. If the working tree is clean:
```bash
git checkout main
git pull --rebase origin main
```
Then create/update the dedicated feature branch for the phase. **Do NOT begin implementation until synchronization is complete.**

---

### 3.2 Before Every Task
Even if the phase has started, before beginning each new task:
```bash
git status
git fetch --all --prune
git log --oneline --decorate -15
```
Check whether `main` has changed. Synchronize the current feature branch before implementing:
```bash
git fetch origin
git rebase origin/main
```
Do not continue if there are unresolved conflicts.

---

### 3.3 Before Modifying Any File
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

Only after this inspection may you edit the file.

---

## 4. Preservation & Conflict Prevention

### 4.1 Never Blindly Overwrite
Never replace an existing file wholesale simply because an AI generated a new version.
**Always Preserve:**
- Teammate changes
- Existing functionality
- Existing architecture
- Existing components
- Existing API contracts
- Existing security controls
- Existing documentation

If a file contains changes from another developer, integrate with them surgically rather than overwriting.

### 4.2 High-Conflict Shared Files
Always perform extra inspection before modifying:
- `pom.xml`, `package.json`, `package-lock.json`
- `docker-compose.yml`
- `backend/src/main/resources/application.yml` / `application.properties`
- `backend/src/main/resources/db/migration/*`
- `.github/workflows/*`
- Shared DTOs, API contracts, Global Exception Handling, Shared Utilities, Design System & Routing

**Protocol for High-Conflict Files:**
```text
FETCH → INSPECT HISTORY → INSPECT CURRENT CHANGES → CHECK TEAMMATE WORK → MAKE MINIMAL CHANGE → TEST
```

### 4.3 Handling Local Changes & Destructive Operations
If `git status` shows modifications that you did not create during the current task: **STOP before editing.**
- ❌ **NEVER run:** `git reset --hard`, `git checkout .`, `git clean -fd`, `git push --force`.
- First determine whether changes are previous work, teammate work, uncommitted work, or generated files. Preserve them.

---

## 5. Commit, Push & Phase Transitions

### 5.1 Before Every Commit
```bash
git status
git diff
git diff --check
```
Review every changed file. Verify diff contains ONLY intended changes (no secrets, no debug code, no unintended dependency edits). Run tests:
```bash
# Backend
mvn -f backend/pom.xml test

# Frontend
npm test
```

### 5.2 Before Pushing
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

### 5.3 Phase Transition Rule
```text
PHASE N IMPLEMENTATION
        ↓
MEMBER 1 COMPLETE
        ↓
MEMBER 2 COMPLETE
        ↓
PRs REVIEWED
        ↓
MERGED INTO MAIN
        ↓
BOTH MEMBERS FETCH
        ↓
BOTH MEMBERS PULL LATEST MAIN
        ↓
FULL INTEGRATION TEST
        ↓
SECURITY REVIEW
        ↓
STITCH UI REVIEW
        ↓
DOCUMENTATION UPDATE
        ↓
PHASE SIGN-OFF
        ↓
START NEXT PHASE
```

---

## 6. Absolute Rule for AI Agents

```text
CHECK GIT ➔ FETCH REMOTE ➔ INSPECT CHANGES ➔ SYNC ➔ CHECK TEAMMATE WORK ➔ READ FILE ➔ PLAN ➔ MODIFY
```
Before you write or modify even one line of existing project code, you must know what has changed in the shared repository since your last synchronization. If you cannot establish that safely: **DO NOT MODIFY THE FILE. STOP AND REPORT THE SITUATION.**
