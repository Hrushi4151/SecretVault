# SecretVault — 126 UI Screen Inventory & Architecture Mapping

The SecretVault Web Dashboard encompasses **126 distinct application screens** designed in Stitch (`stitch_secretvault_devsecops_platfor/stitch_secretvault_devsecops_platform/`).

> **Design Implementation Rule:** The Stitch designs are the **visual source of truth**. Every screen must be built from reusable components adhering to [docs/DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) with full loading, error, empty, and authorized states.

---

## Screen Inventory (Screens 1–126)

| # | Screen Name | Product Area | Purpose | Backend Dependencies | Security Requirements | Related Domain | Status |
|---|---|---|---|---|---|---|---|
| 1 | Login | Auth & Onboarding | User sign-in with email & password | `POST /auth/login` | Rate limited; BCrypt verification | `auth` | IMPLEMENTED |
| 2 | Sign Up | Auth & Onboarding | Account registration | `POST /auth/register` | Password complexity validation | `auth` | IMPLEMENTED |
| 3 | Forgot Password | Auth & Onboarding | Password reset request | `POST /auth/forgot-password` | Timing attack safe; token hash | `auth` | IMPLEMENTED |
| 4 | Reset Password | Auth & Onboarding | Setting new password via token | `POST /auth/reset-password` | Single-use token expiry | `auth` | NOT STARTED |
| 5 | MFA Verification | Auth & Onboarding | 2FA / TOTP challenge | `POST /auth/mfa/verify` | Ephemeral verification token | `auth` | IMPLEMENTED |
| 6 | Create Organization | Workspace | New tenant organization setup | `POST /organizations` | Unique slug; ownership assignment | `organization` | IMPLEMENTED |
| 7 | Create First Project | Workspace | First project onboarding wizard | `POST /projects` | Tenant scoping | `project` | NOT STARTED |
| 8 | Choose Environments | Workspace | Initial environment selection | `POST /environments` | Default Dev/Staging/Prod creation | `environment` | NOT STARTED |
| 9 | Dashboard | Workspace | Workspace executive overview | `GET /dashboard/summary` | Tenant isolation; aggregated metrics | `workspace` | NOT STARTED |
| 10 | Projects List | Projects | Catalog of all applications | `GET /projects` | RBAC project read check | `project` | NOT STARTED |
| 11 | Create Project | Projects | Modal/Form to add new project | `POST /projects` | Name validation; unique slug | `project` | NOT STARTED |
| 12 | Project Overview | Projects | Project metrics, health, envs | `GET /projects/{id}` | Workspace authorization | `project` | NOT STARTED |
| 13 | Environment Overview | Environments | Secrets, providers, drift in env | `GET /environments/{id}` | Environment-level RBAC | `environment` | NOT STARTED |
| 14 | Secrets List | Secret Engine | Data table of secrets (masked) | `GET /environments/{id}/secrets` | Values masked by default (`••••`) | `secret` | NOT STARTED |
| 15 | Create Secret | Secret Engine | Modal/Form to create secret | `POST /environments/{id}/secrets` | Payload encrypted before storage | `secret` | NOT STARTED |
| 16 | Secret Details | Secret Engine | Metadata, tags, version summary | `GET /secrets/{id}` | Audited metadata access | `secret` | NOT STARTED |
| 17 | Secret Reveal | Secret Engine | Protected reveal modal/flow | `POST /secrets/{id}/reveal` | `secret.reveal` permission + MFA | `secret` | NOT STARTED |
| 18 | Secret Versions | Secret Engine | Immutable version history table | `GET /secrets/{id}/versions` | Immutable history; no plaintexts | `secret` | NOT STARTED |
| 19 | Secret Activity | Secret Engine | Audit log timeline for a secret | `GET /secrets/{id}/activity` | Append-only audit events | `audit` | NOT STARTED |
| 20 | Integration Marketplace | Integrations | Catalog of supported providers | `GET /integrations/catalog` | Metadata catalog | `integration` | NOT STARTED |
| 21 | Connect Provider | Integrations | Provider setup wizard | `POST /integrations/connect` | Encrypted provider token storage | `integration` | NOT STARTED |
| 22 | Provider Details | Integrations | Connection health and configs | `GET /integrations/{id}` | Admin permission required | `integration` | NOT STARTED |
| 23 | Platform Mapping | Integrations | Mapping envs to provider targets | `POST /integrations/mappings` | Explicit mapping confirmation | `integration` | NOT STARTED |
| 24 | Sync Center | Synchronization | Central synchronization dashboard | `GET /sync/overview` | Tenant scoped sync jobs | `sync` | NOT STARTED |
| 25 | Sync Details | Synchronization | Individual sync job logs & diff | `GET /sync/jobs/{id}` | Audited failure/success output | `sync` | NOT STARTED |
| 26 | Failed Syncs | Synchronization | Failure triage & retry console | `POST /sync/jobs/{id}/retry` | Idempotent retry execution | `sync` | NOT STARTED |
| 27 | Security Overview | Security | High-level security posture | `GET /security/overview` | Security score aggregation | `security` | NOT STARTED |
| 28 | Risk Center | Security | Vulnerability & risk findings | `GET /security/risks` | Risk classification | `security` | NOT STARTED |
| 29 | Risk Detail | Security | Specific finding forensic view | `GET /security/risks/{id}` | Secret masking in evidence | `security` | NOT STARTED |
| 30 | Secret Leaks | Security | Detected leaks across git/CI | `GET /security/leaks` | Masked match snippets | `security` | NOT STARTED |
| 31 | Leak Investigation | Security | Remediation workbench | `POST /security/leaks/{id}/remediate`| Emergency rotation trigger | `security` | NOT STARTED |
| 32 | Drift Detection | Security | Reconciliation console | `GET /sync/drift` | Provider hash comparison | `sync` | NOT STARTED |
| 33 | Access Anomalies | Security | Unusual access patterns alert | `GET /security/anomalies` | Behavioral anomaly detection | `security` | NOT STARTED |
| 34 | Audit Logs | Audit | Cryptographic event ledger | `GET /audit/logs` | Immutable append-only log | `audit` | NOT STARTED |
| 35 | Audit Event Details | Audit | Full event metadata inspector | `GET /audit/logs/{id}` | Network & actor context | `audit` | NOT STARTED |
| 36 | AI Assistant | AI Co-pilot | Interactive security assistant | `POST /ai/chat` | Zero plaintext prompt policy | `ai` | NOT STARTED |
| 37 | AI Security Analysis | AI Co-pilot | AI posture report & insights | `GET /ai/security-analysis` | Advisory output only | `ai` | NOT STARTED |
| 38 | Deployment RCA | AI Co-pilot | Deployment failure diagnosis | `POST /ai/deployment-rca` | Sanitized log parsing | `ai` | NOT STARTED |
| 39 | Team Members | Organization | User list and roles | `GET /organization/members` | Tenant admin access check | `organization` | IMPLEMENTED |
| 40 | Invite Member | Organization | Invite user to workspace | `POST /organization/invitations` | Role assignment validation | `organization` | IMPLEMENTED |
| 41 | Roles & Permissions | Organization | Custom RBAC role editor | `GET /organization/roles` | `role.manage` permission | `access` | NOT STARTED |
| 42 | Organization Settings | Organization | Org profile, name, logo | `GET /organization/settings` | `organization.manage` permission | `organization` | NOT STARTED |
| 43 | Account Settings | User Profile | User preferences, password, MFA | `GET /user/profile` | Self-management authorization | `auth` | NOT STARTED |
| 44 | API Keys | Access | Human developer personal tokens | `GET /user/api-keys` | Masked key display; hash storage | `access` | NOT STARTED |
| 45 | Sessions | Access | Active login session manager | `GET /user/sessions` | Revoke session capability | `auth` | NOT STARTED |
| 46 | Notifications | Organization | Notification preferences | `GET /organization/notifications`| User preference isolation | `organization` | NOT STARTED |
| 47 | Billing | Organization | Subscriptions, quotas, usage | `GET /organization/billing` | Owner role check | `organization` | NOT STARTED |
| 48 | Danger Zone | Organization | Delete organization / workspace | `DELETE /organization` | Multi-step confirmation + owner | `organization` | NOT STARTED |
| 49 | Global Search | Navigation | Command palette / omnisearch | `GET /search?q=` | Filters out unauthorized records | `common` | NOT STARTED |
| 50 | Notification Center | Navigation | Real-time notification drawer | `GET /notifications` | Actor scoped notifications | `common` | NOT STARTED |
| 51 | Empty States | Global System | Standard empty views | N/A (Frontend Component) | Consistent UI design | `common` | NOT STARTED |
| 52 | Loading States | Global System | Skeletons and spinners | N/A (Frontend Component) | Glassmorphism shimmer | `common` | NOT STARTED |
| 53 | Error States | Global System | 404, 403, 500 boundary views | N/A (Frontend Component) | Displays safe `requestId` | `common` | NOT STARTED |
| 54 | Danger Modals | Global System | Destruction confirmation dialogs | N/A (Frontend Component) | Require explicit confirmation text | `common` | NOT STARTED |
| 55 | CLI Dashboard | Developer | CLI usage metrics and setup | `GET /developer/cli` | Workstation telemetry | `runtime` | NOT STARTED |
| 56 | CLI Setup | Developer | Guide for installing CLI | N/A (Frontend Static Guide) | Token generation wizard | `runtime` | NOT STARTED |
| 57 | CLI Command Center | Developer | Interactive command generator | N/A (Frontend Tool) | In-memory execution examples | `runtime` | NOT STARTED |
| 58 | SDK / Runtime Access | Developer | SDK keys & code snippets | `GET /developer/sdk` | Machine token issuance | `runtime` | NOT STARTED |
| 59 | Runtime Applications | Developer | Connected services consuming SDK | `GET /runtime/applications` | Dynamic token verification | `runtime` | NOT STARTED |
| 60 | Secret Usage Map | Developer | Secret dependency topology | `GET /secrets/dependencies` | Relationship graph without values | `security` | NOT STARTED |
| 61 | CI/CD Integrations | Automation | Pipeline connection list | `GET /cicd/integrations` | Machine credentials | `cicd` | NOT STARTED |
| 62 | CI/CD Setup | Automation | GitHub/GitLab pipeline wizard | `POST /cicd/setup` | OIDC federation instructions | `cicd` | NOT STARTED |
| 63 | Workload Identity / OIDC | Automation | Keyless federation setup | `POST /identity/oidc` | OIDC issuer validation | `identity` | NOT STARTED |
| 64 | Access Control Center | Security | Central IAM & grants overview | `GET /access/overview` | Admin access verification | `access` | NOT STARTED |
| 65 | JIT Access | Security | Active temporary access grants | `GET /access/jit/grants` | Ephemeral TTL tracking | `access` | NOT STARTED |
| 66 | Access Request | Security | Form to request temporary role | `POST /access/jit/requests` | Mandatory justification reason | `access` | NOT STARTED |
| 67 | Access Request Details | Security | Approval workbench | `POST /access/jit/approve` | Dual approval or admin gate | `access` | NOT STARTED |
| 68 | Access Reviews | Security | Access certification campaigns | `GET /access/reviews` | Periodic governance tracking | `access` | NOT STARTED |
| 69 | Network Access Policies | Security | IP allowlists and CIDR rules | `GET /security/network-policies`| CIDR syntax validation | `security` | NOT STARTED |
| 70 | Service Accounts | Identity | Machine identity management | `GET /identity/service-accounts`| Scoped service account tokens | `identity` | NOT STARTED |
| 71 | Create Service Account | Identity | Provision new machine identity | `POST /identity/service-accounts`| Least privilege scope assignment | `identity` | NOT STARTED |
| 72 | Service Account Details | Identity | Token rotation & usage metrics | `GET /identity/service-accounts/{id}` | Token hash display | `identity` | NOT STARTED |
| 73 | Secret Branches | Versioning | Git-like secret branching | `GET /secrets/branches` | Branch isolation | `secret` | NOT STARTED |
| 74 | Secret Diff | Versioning | Version & branch diff engine | `POST /secrets/diff` | Diffs metadata, not plaintext | `secret` | NOT STARTED |
| 75 | Environment Promotion | Versioning | Promote secrets Dev -> Staging -> Prod | `POST /environments/promote` | Production approval gate | `secret` | NOT STARTED |
| 76 | Rotation Center | Rotation | Credential rotation dashboard | `GET /rotation/overview` | Rotation health monitoring | `secret` | NOT STARTED |
| 77 | Rotation Policy | Rotation | Auto-rotation schedules | `POST /rotation/policies` | Expiration alerts | `secret` | NOT STARTED |
| 78 | Shadow Rotation | Rotation | Staged validation engine | `POST /rotation/shadow` | Validates before cutover | `secret` | NOT STARTED |
| 79 | Repository Protection | Security | Git repo scanning overview | `GET /security/repositories` | Webhook verification | `security` | NOT STARTED |
| 80 | Repository Details | Security | PR security shield & history | `GET /security/repositories/{id}` | Masked vulnerability evidence | `security` | NOT STARTED |
| 81 | Pull Request Security | Security | Pre-merge security gates | `GET /security/pull-requests` | PR blocker policies | `security` | NOT STARTED |
| 82 | Pre-Commit Protection | Security | Pre-commit hook configuration | N/A (Developer Guide / Script) | Local hook distribution | `security` | NOT STARTED |
| 83 | Terraform / IaC | Infrastructure | IaC provider and secret refs | `GET /infrastructure/terraform` | No plaintext in `.tfstate` | `deployment` | NOT STARTED |
| 84 | Kubernetes | Infrastructure | Cluster connection dashboard | `GET /infrastructure/kubernetes`| OIDC cluster authentication | `deployment` | NOT STARTED |
| 85 | Kubernetes Cluster Details | Infrastructure | Namespace mappings & operator | `GET /infrastructure/kubernetes/{id}` | Cluster health probe | `deployment` | NOT STARTED |
| 86 | Blast Radius | Security | Compromise impact predictor | `GET /security/blast-radius` | Graph traversal (no secrets) | `security` | NOT STARTED |
| 87 | Secret Dependency Graph | Security | Visual interactive node graph | `GET /security/graph` | Metadata relationship graph | `security` | NOT STARTED |
| 88 | Security Incident | Security | Active incident forensics | `GET /security/incidents/{id}` | Audited incident timeline | `security` | NOT STARTED |
| 89 | Security Policies | Security | Zero-trust guardrail policies | `GET /security/policies` | Invariant enforcement | `security` | NOT STARTED |
| 90 | Policy Details | Security | Edit specific guardrail rule | `PUT /security/policies/{id}` | Strict policy validation | `security` | NOT STARTED |
| 91 | Slack Integration | Integrations | Alert & approval dispatcher | `POST /integrations/slack` | Webhook signature verification | `integration` | NOT STARTED |
| 92 | Notification Rules | Organization | Routing rules for alerts | `GET /organization/notification-rules` | Channel preference isolation | `organization` | NOT STARTED |
| 93 | SSO / SAML | Enterprise | Enterprise SSO configuration | `POST /auth/saml/config` | SAML cert & XML validation | `auth` | NOT STARTED |
| 94 | Enterprise Identity | Enterprise | Okta / Azure AD directory setup | `GET /enterprise/identity` | Enterprise permission check | `auth` | NOT STARTED |
| 95 | SCIM Provisioning | Enterprise | Directory sync configuration | `POST /scim/v2/config` | SCIM bearer token issuance | `organization` | NOT STARTED |
| 96 | Compliance Center | Enterprise | SOC 2 / ISO 27001 posture | `GET /compliance/attestation` | Continuous compliance ledger | `security` | NOT STARTED |
| 97 | Deployment Settings | Deployment | Host configuration & topology | `GET /deployment/settings` | Admin permission | `deployment` | NOT STARTED |
| 98 | System Health | Deployment | Cluster, DB, Redis health | `GET /actuator/health` | Sanitized telemetry | `health` | NOT STARTED |
| 99 | VS Code Extension | Developer | Extension setup & sync | N/A (Extension Guide) | Extension token generation | `runtime` | NOT STARTED |
| 100 | Local Development | Developer | In-memory injection guide | N/A (CLI Guide) | No `.env` on disk | `runtime` | NOT STARTED |
| 101 | Environment Profiles | Developer | Local dev profile manager | `GET /developer/profiles` | Developer-scoped configs | `runtime` | NOT STARTED |
| 102 | Environment Clone | Developer | Safe cross-environment clone | `POST /environments/clone` | Excludes production values | `environment` | NOT STARTED |
| 103 | Secret Watch | Developer | Real-time local secret sync | `GET /secrets/watch` (SSE/WS) | Ephemeral watch tokens | `runtime` | NOT STARTED |
| 104 | Secret Simulation | Developer | Dry-run change simulation | `POST /secrets/simulate` | Predicts drift & failure | `secret` | NOT STARTED |
| 105 | Config Generator | Developer | Secure artifact generator | `POST /templates/generate` | Schema validation | `template` | NOT STARTED |
| 106 | Template Manager | Developer | Reusable schema library | `GET /templates` | No real secrets in templates | `template` | NOT STARTED |
| 107 | Template Editor | Developer | Schema invariant rule builder | `POST /templates` | Type & regex validation | `template` | NOT STARTED |
| 108 | CLI Diagnostics | Developer | Workstation health & doctor | `GET /developer/cli/doctor` | Connection test | `runtime` | NOT STARTED |
| 109 | CLI Terminal UI | Developer | Browser-based terminal simulator| N/A (Interactive UI) | Client-side sandbox | `runtime` | NOT STARTED |
| 110 | Local Audit | Developer | Workstation CLI event ledger | `GET /audit/local` | Local event trace | `audit` | NOT STARTED |
| 111 | Secret History | Secret Engine | Detailed version timeline | `GET /secrets/{id}/history` | Immutable version entries | `secret` | NOT STARTED |
| 112 | Secret Rollback | Secret Engine | Rollback impact inspector | `POST /secrets/{id}/rollback` | Creates audit & new version | `secret` | NOT STARTED |
| 113 | Developer Overview | Developer | Developer platform hub | `GET /developer/overview` | Developer quick links | `runtime` | NOT STARTED |
| 114 | Workspace Switcher | Navigation | Multi-tenant tenant navigator | `GET /workspaces` | Workspace membership check | `workspace` | IMPLEMENTED |
| 115 | Workspace Management | Workspace | Identity governance & telemetry | `GET /workspaces/{id}` | Workspace admin check | `workspace` | IMPLEMENTED |
| 116 | MFA & Auth Security | Security | Hardware WebAuthn & passkeys | `GET /security/mfa-settings` | Step-up auth enforcement | `auth` | NOT STARTED |
| 117 | IP Allowlist Center | Security | CIDR network rules workbench | `GET /security/ip-allowlists` | CIDR validation | `security` | NOT STARTED |
| 118 | Threat Protection | Security | Threat intelligence timeline | `GET /security/threats` | Anomaly alert feeds | `security` | NOT STARTED |
| 119 | Access Review Campaign | Security | Certification campaign manager | `POST /access/reviews/campaigns`| Governance assignment | `access` | NOT STARTED |
| 120 | Access Review Details | Security | Certification workbench | `POST /access/reviews/certify` | Audited governance signoff | `access` | NOT STARTED |
| 121 | Secret Rotation Wizard | Rotation | Guided rotation walkthrough | `POST /rotation/wizard` | Provider verification step | `secret` | NOT STARTED |
| 122 | Rotation Schedule | Rotation | Calendar view of rotations | `GET /rotation/schedules` | Expiration forecast | `secret` | NOT STARTED |
| 123 | Runtime App Setup | Developer | In-memory SDK hydration wizard | `POST /runtime/applications/setup` | Token generation | `runtime` | NOT STARTED |
| 124 | Machine Identity Gov | Identity | Service account governance | `GET /identity/governance` | Token age & risk metrics | `identity` | NOT STARTED |
| 125 | Self-Hosted Deployment | Enterprise | Private cloud deployment console | `GET /enterprise/self-hosted` | License & health validation | `deployment` | NOT STARTED |
| 126 | Security Center Timeline| Security | Unified threat timeline | `GET /security/timeline` | Correlation of security events | `security` | NOT STARTED |
