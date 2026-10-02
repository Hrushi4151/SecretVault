# Phase 9: Trust Policies & Claim Rule Engine

## Trust Policy Architecture

An **OIDC Trust Policy** (`OidcTrustPolicy`) defines the cryptographic conditions under which an identity provider's token can assume a specific `MachineIdentity`.

A machine identity can have one or more trust policies. If any policy matches, the machine identity is granted a session (logical OR across policies). Within a single policy, all defined claim rules must evaluate to true (logical AND across rules).

---

## Claim Match Operators

The `ClaimRuleEngine` supports the following match operators:

| Operator | Semantic | Example Rule | Matching Claim Value | Non-Matching Claim Value |
| :--- | :--- | :--- | :--- | :--- |
| `EQUALS` | Exact case-sensitive match | `repository` `EQUALS` `my-org/api` | `"my-org/api"` | `"my-org/frontend"` |
| `NOT_EQUALS` | Negated exact match | `ref` `NOT_EQUALS` `refs/heads/dev` | `"refs/heads/main"` | `"refs/heads/dev"` |
| `IN` | Value is contained within a comma-separated list or array | `environment` `IN` `prod,staging` | `"prod"` or `["staging", "qa"]` | `"dev"` |
| `NOT_IN` | Value is not in list or array | `environment` `NOT_IN` `dev,test` | `"prod"` | `"dev"` |
| `PREFIX` | String starts with prefix | `ref` `PREFIX` `refs/heads/release/` | `"refs/heads/release/v1.0"` | `"refs/heads/feat/ui"` |
| `SUFFIX` | String ends with suffix | `sub` `SUFFIX` `:main` | `"repo:org/app:ref:refs/heads:main"` | `"...:feature-1"` |
| `CONTAINS` | String contains substring | `workflow` `CONTAINS` `deploy` | `"prod-deploy-pipeline"` | `"lint-and-test"` |
| `REGEX` | Evaluates regular expression match | `ref` `REGEX` `^refs/tags/v[0-9]+\.[0-9]+\.[0-9]+$` | `"refs/tags/v1.2.0"` | `"refs/tags/nightly"` |

---

## Visual Trust Policy Summary Generation

Trust policies feature an automated natural language summary generator for human-readable governance audits:

### Example:
**Rules**:
1. `repository` `EQUALS` `Hrushi4151/Rally`
2. `ref` `EQUALS` `refs/heads/main`
3. `environment` `IN` `production,staging`

**Generated Summary**:
> "Matches when repository is 'Hrushi4151/Rally', ref is 'refs/heads/main', and environment is in [production, staging]"

---

## Overly Broad Policy Risk Detection

The SecretVault Security Center actively scans all trust policies for over-permissive wildcard and unconstrained rules via `OverlyBroadTrustPolicyRule`:

- **Flags missing repository pins**: If a policy matches only on `repository_owner` or has empty claim rules.
- **Flags overly permissive regex**: e.g., `ref` `REGEX` `.*`.
- **Assigns Risk Scores**: Surfaces actionable alerts in the Security Center dashboard.
