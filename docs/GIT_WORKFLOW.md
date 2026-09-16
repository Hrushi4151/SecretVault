# SecretVault — Two-Developer Git & GitHub Collaboration Workflow

## 1. Branch Strategy

The `main` branch is protected and represents production-ready code. Direct commits to `main` are strictly prohibited.

```text
main (Protected)
  │
  ├── feature/<task-description>
  ├── fix/<bug-description>
  ├── security/<hardening-description>
  └── infra/<docker-or-ci-description>
```

---

## 2. Standard Task Lifecycle

```text
CHECK GIT & SYNC
      ↓
CREATE BRANCH
      ↓
INSPECT CODE & RECENT COMMITS
      ↓
IMPLEMENT & TEST
      ↓
COMMIT (Conventional Commits)
      ↓
REBASE & PUSH
      ↓
PULL REQUEST & CI CHECKS
      ↓
PEER REVIEW & MERGE
      ↓
SYNCHRONIZE
```

### Step-by-Step Commands:
```bash
# 1. Check status and sync with main
git status
git fetch origin
git checkout main
git pull --rebase origin main

# 2. Create a new branch
git checkout -b feature/<task-name>

# 3. Make atomic commits
git add <files>
git commit -m "feat(domain): add clear description"

# 4. Rebase against latest main before pushing
git fetch origin
git rebase origin/main

# 5. Push to GitHub
git push -u origin feature/<task-name>
```

---

## 3. High-Conflict Shared Files

The following files require special care:
- `backend/pom.xml`
- `docker-compose.yml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/migration/*`
- `.github/workflows/*`

### Rules for High-Conflict Files:
1. Always rebase immediately before modifying.
2. Keep edits focused and minimal.
3. Notify your teammate when modifying shared dependencies or Flyway scripts.
4. Open PR and merge promptly.
