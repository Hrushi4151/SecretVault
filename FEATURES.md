# SecretVault — Feature List by Category

## 1. Authentication and onboarding

- Sign in with email/password.
- Account registration.
- Password recovery.
- Organization creation during onboarding.
- Organization switching for users who belong to multiple teams.
- First-project setup.
- Guided first-integration setup.
- OAuth login support — Roadmap.
- MFA and passkeys — Roadmap.
- Enterprise SSO, SAML, and SCIM — Roadmap.

## 2. Organization and team management

- Create and manage organizations.
- Organization profile: name and logo.
- Invite team members.
- View member status and roles.
- Remove members.
- Assign project-level access.
- Assign environment-level access.
- Manage organization security policies.
- Configure secret-rotation policy.
- Require MFA for sensitive access — Roadmap.
- Billing and subscription management — Roadmap.

## 3. Roles and permissions

Initial roles:

- Owner
- Admin
- Developer
- Viewer

Core permissions:

- View projects.
- Create secrets.
- Update secrets.
- Delete secrets.
- Reveal secrets.
- Rotate secrets.
- Manage integrations.
- Start synchronization.
- View audit logs.
- View security findings.
- Manage team members.
- Manage organization settings.

Access is restricted by:

- Organization.
- Project.
- Environment.
- API-key scope.
- Role and future granular permission rules.

Example: a developer can work in Development and Staging but have no Production access.

## 4. Project and environment management

- Create projects for applications or services.
- Add project descriptions.
- Create Development, Staging, and Production environments.
- View project health.
- View secret count per environment.
- View connected platforms.
- View drift count.
- View security score.
- View last synchronization time.
- Project-level quick actions: add secret, connect provider, sync all.
- Custom environments — Roadmap.

## 5. Secret management

- Create secrets.
- Store secret name, description, type, tags, environment, and metadata.
- Mask secret values by default.
- Encrypt values before database storage.
- Never store plaintext secret values in PostgreSQL.
- Edit secret metadata.
- Update secret values.
- Create a new version for every value update.
- View current and previous versions.
- Roll back to an authorized previous version.
- Rotate a secret.
- Revoke a secret.
- Delete a secret with confirmation.
- Search secrets by name.
- Filter by project, environment, tag, provider, risk, and sync status.
- Import `.env` files.
- Parse imported environment variables before confirmation.
- Controlled secret export — Roadmap.
- Secret classification — Roadmap.

## 6. Secret reveal protection

- Keep secrets hidden by default.
- Require explicit reveal action.
- Require re-authentication before reveal.
- Require MFA where configured.
- Reveal temporarily only.
- Record every reveal event in audit history.
- Never show secret values in normal secret lists.
- Never include values in logs, notifications, analytics, AI prompts, or audit events.

## 7. Secret lifecycle and versioning

- Create secret.
- Encrypt and store secret version.
- Synchronize to selected providers.
- Update by creating a new version.
- Rotate credentials.
- Retry failed provider updates.
- Revoke compromised credentials.
- Delete securely.
- Preserve version history for rollback and investigation.
- Audit all important lifecycle events.

## 8. Integrations

Initial planned integrations:

- Vercel.
- AWS.
- Railway.
- Render.
- Netlify.
- GitHub.

Integration capabilities:

- Connect through supported OAuth, token, access-key, or service-account flow.
- Display required provider permissions.
- Test provider connection.
- Discover provider accounts, projects, and environments.
- Explicitly map SecretVault projects and environments to provider resources.
- Synchronize secrets.
- Delete secrets where supported.
- Report provider connection health.
- Disconnect an integration.
- Track integration status.

Future integrations:

- Azure.
- Google Cloud.
- Kubernetes.
- Cloudflare.
- Fly.io.
- GitLab.

## 9. Synchronization engine

- Start individual-secret synchronization.
- Start environment synchronization.
- Start project-level “Sync All”.
- Use asynchronous jobs rather than making users wait in the browser.
- Queue jobs for worker processing.
- Show job progress.
- Verify provider updates where safe and supported.
- Track states:

  - Pending
  - Running
  - Synced
  - Failed
  - Retrying
  - Skipped
  - Cancelled

- Retry temporary failures with exponential backoff.
- Avoid endless retries for permanent failures.
- Make operations idempotent so retries do not create duplicate provider changes.
- Show per-provider and per-secret outcomes.
- Allow authorized manual retry.

## 10. Sync failure handling

Failure categories:

- Authentication failure.
- Permission failure.
- Invalid configuration.
- Validation failure.
- Provider rate limit.
- Temporary network issue.
- Provider outage.
- Unknown failure.

User-facing capabilities:

- View failure reason.
- Reconnect expired integration.
- Retry failed sync.
- Review job history.
- View affected secret names and safe metadata.
- Never expose secret values in failure messages.

## 11. Drift detection

- Compare SecretVault’s desired state with safe observable provider state.
- Detect outdated provider configuration.
- Detect missing secrets.
- Mark unknown state where a provider cannot safely verify it.
- Use states:

  - Synced
  - Drifted
  - Missing
  - Unknown

- Use safe comparison methods:

  - Version metadata.
  - Provider metadata.
  - Timestamps.
  - Safe hashes where possible.
  - Observable provider status.

- Provide an authorized “Sync Latest” action.
- Show drift by secret, environment, project, and provider.

## 12. Security dashboard and risk intelligence

- Overall security score — Roadmap.
- Security posture breakdown — Roadmap.
- Secret age analysis — Roadmap.
- Rotation status analysis — Roadmap.
- Privileged-secret detection — Roadmap.
- Production-secret risk analysis — Roadmap.
- Access-control health — Roadmap.
- Integration security health — Roadmap.
- Synchronization health — Roadmap.
- Risk severity:

  - Critical
  - High
  - Medium
  - Low

- Explain why a secret is risky, such as age, production scope, high privilege, drift, broad access, or potential exposure.

## 13. Leak detection and remediation

Roadmap features:

- Scan connected GitHub repositories.
- Scan commits and pull requests.
- Scan files and configuration files.
- Scan safely integrated CI/CD signals.
- Detect known secret patterns.
- Use entropy analysis.
- Use contextual classification.
- Identify likely credential type.
- Show repository, file, line, commit, severity, and confidence.
- Never display the exposed secret itself.
- Mark false positives.
- Investigate findings.
- Revoke or rotate affected secret.
- Remove exposure from code/history.
- Synchronize replacement secret.
- Verify remediation.
- Mark finding resolved.

## 14. Access anomaly detection

Roadmap features:

- Monitor secret-access patterns.
- Track access frequency.
- Track environment sensitivity.
- Compare normal behavior with current behavior.
- Flag unusual production access.
- Show severity and evidence.
- Investigate suspicious activity.
- Revoke sessions where authorized.
- Notify security administrators.

## 15. AI intelligence

Roadmap capabilities:

- Ask which secrets need rotation.
- Ask which projects have drift.
- Ask why a provider synchronization failed.
- Ask which integrations are unhealthy.
- Ask for a summary of security events.
- Ask for production secrets older than a policy threshold.
- Ask for payment-related or database-related secrets.
- Generate evidence-based security summaries.
- Generate risk explanations.
- Generate recommended remediation steps.
- Analyze sync failures.
- Analyze deployment failures.
- Provide root-cause analysis using authorized metadata, audit events, sync events, deployment events, and sanitized logs.
- Recommend actions without executing them automatically.
- Require explicit authorization before consequential actions, especially in Production.

AI safety rules:

- Never send plaintext secrets to an external LLM.
- Never use AI as the secret-value storage system.
- Never let AI bypass RBAC.
- Never let AI discover unauthorized resources.
- Prefer structured internal tools and APIs over unrestricted database access.
- Use sanitized metadata and logs only.

## 16. Audit logging

Audit events include:

- Secret created.
- Secret updated.
- Secret revealed.
- Secret rotated.
- Secret revoked.
- Secret deleted.
- Integration connected.
- Integration disconnected.
- Sync started.
- Sync completed.
- Sync failed.
- Sync retried.
- Drift detected.
- Member invited.
- Member removed.
- Role changed.
- API key created.
- API key revoked.
- Sensitive settings changed.

Each audit event records:

- Actor.
- Action.
- Resource.
- Organization.
- Project.
- Environment.
- Timestamp.
- Request ID.
- Result.
- Safe contextual metadata.

Audit logs never contain secret values.

## 17. Notifications

Roadmap notification types:

- Critical secret exposure.
- High-risk secret.
- Secret rotation required.
- Production sync failure.
- Drift detected.
- Provider connection failure.
- Suspicious access anomaly.
- Team invitation.
- Permission change.
- Integration disconnected.

Planned channels:

- In-app.
- Email.
- Slack.
- Webhook.

## 18. API keys and automation

Roadmap capabilities:

- Create API keys for CLI, CI/CD, and automation.
- Choose scopes.
- Set expiration.
- Track creation time.
- Track last-used time.
- Revoke keys.
- Show full key only once.
- Restrict keys to approved organization, project, environment, and permissions.
- Prevent keys from printing secret values into CI logs.

## 19. CLI roadmap

Planned developer commands:

- Authenticate.
- List accessible organizations and projects.
- List authorized secret metadata.
- Pull approved environment configuration.
- Push approved secret updates.
- Trigger synchronization.
- Check sync status.
- Inject secrets into supported developer or CI workflows.
- Use short-lived or scoped credentials where possible.
- Prevent accidental value output in terminal logs.

## 20. Frontend and user experience

Global interface features:

- Dashboard.
- Sidebar navigation.
- Organization switcher.
- Project switcher.
- Environment switcher.
- Global search.
- Command palette.
- Notification center.
- Breadcrumbs.
- Data tables.
- Secret input with masking.
- Secret value mask.
- Risk badges.
- Sync-status badges.
- Platform badges.
- Activity timeline.
- Sync progress.
- Confirmation dialogs.
- Dangerous-action dialogs.
- Toast messages.
- Loading skeletons.
- Empty states.
- Error states.
- Permission-denied states.
- Partial-success states.

Design direction:

- Professional DevSecOps SaaS appearance.
- High information density.
- Clear green, yellow, red, and neutral status states.
- Monospace presentation for secret names and identifiers.
- Desktop-first layout.
- Responsive tablet sidebar.
- Mobile priority for alerts, dashboard, secrets, sync state, notifications, and AI.

## 21. 54 planned screens

1. Sign in.
2. Create account.
3. Onboarding.
4. Dashboard.
5. Organization switcher.
6. Project switcher.
7. Global search.
8. Command palette.
9. Notification center.
10. Help and documentation.
11. Projects.
12. Create project.
13. Project overview.
14. Environment overview.
15. Project health.
16. Secrets list.
17. Create secret.
18. Import `.env`.
19. Secret details.
20. Reveal-secret confirmation.
21. Edit secret/new version.
22. Secret versions.
23. Rotation workflow.
24. Secret activity.
25. Delete/revoke confirmation.
26. Controlled export.
27. Integration marketplace.
28. Connect provider.
29. Provider details.
30. Project/environment mapping.
31. Sync center.
32. Sync job details.
33. Failed synchronizations.
34. Drift detection.
35. Security overview.
36. Risk center.
37. Secret leaks.
38. Leak investigation.
39. Access anomalies.
40. Audit logs.
41. Audit-event details.
42. AI assistant.
43. AI security analysis.
44. Deployment analysis and root-cause analysis.
45. AI recommendations.
46. Team members.
47. Invite member.
48. Roles and permissions.
49. Organization settings.
50. Account security.
51. API keys.
52. Active sessions.
53. Notification settings.
54. Billing and danger zone.

## 22. MVP feature scope

Build first:

- Authentication.
- Organizations.
- Team memberships.
- Projects.
- Development, Staging, and Production environments.
- RBAC foundation.
- Encrypted secret storage.
- Secret metadata.
- Secret versioning.
- Masked secret display.
- Controlled reveal.
- Audit logs.
- One or two provider integrations.
- Explicit provider mapping.
- Async synchronization.
- Sync jobs and retries.
- Sync status.
- Basic drift detection.
- Core dashboard and project/secret screens.

## 23. Post-MVP priorities

Build after the core is secure and reliable:

- More integrations.
- API keys and CLI.
- GitHub scanning.
- Leak detection.
- Risk scoring.
- Rotation policies.
- Notifications.
- Webhooks.
- Advanced drift reporting.
- MFA and passkeys.
- AI assistant.
- AI security analysis.
- Deployment risk prediction.
- AI root-cause analysis.
- Enterprise SSO and SCIM.
- Billing.
