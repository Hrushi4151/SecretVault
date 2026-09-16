# SecretVault — Developer Contribution Guidelines

## 1. Code Standards & Behavioral Rules

All contributions must adhere to our senior engineering standards:
- **Security First:** Never compromise secret safety or multi-tenant isolation for speed.
- **Strictly Typed & Tested:** Java 21 LTS with constructor injection; React 18+ with TypeScript in strict mode.
- **DTO Isolation:** Never expose JPA entities directly through controllers.
- **Sanitized Errors:** Catch all unhandled exceptions in `GlobalExceptionHandler` without leaking stack traces or database internals.

---

## 2. Conventional Commit Standards

All commits must follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```text
feat(secret): implement secret rollback endpoint
fix(sync): handle provider timeout gracefully
security(auth): enforce step-up MFA challenge on reveal
test(crypto): add AES-256-GCM tamper-resistance tests
docs(api): document rate limiting error codes
refactor(provider): isolate Vercel provider adapter
chore(ci): update GitHub Actions cache keys
```

---

## 3. Pull Request Checklist (Definition of Done)

Before submitting or merging any PR:
- [ ] Code compiles cleanly with Java 21 (`mvn -f backend/pom.xml clean compile`).
- [ ] All automated unit and integration tests pass (`mvn -f backend/pom.xml test`).
- [ ] New unit tests added for new business logic and edge cases.
- [ ] Multi-tenant isolation verified (`organization_id` & `workspace_id` checked).
- [ ] Input validation applied on all DTO fields.
- [ ] Zero secrets logged, returned in responses, or committed to git.
- [ ] Relevant documentation in `docs/` and `PROJECT_STATUS.md` updated.
- [ ] Peer review completed and approved by the other team member.
