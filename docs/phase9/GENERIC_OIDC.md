# Phase 9: Generic RFC 7519 OIDC Provider Integration

## Overview

SecretVault supports any RFC 7519 / OpenID Connect compliant identity provider, enabling workload federation with:
- **HashiCorp Vault / HCP Vault**
- **Kubernetes ServiceAccount OIDC Tokens** (`serviceaccount_issuer`)
- **Keycloak / Red Hat SSO**
- **Okta / Auth0 / PingFederate**
- **AWS IAM Roles Anywhere / Cognito**
- **Google Cloud Workload Identity Federation**
- **Azure Entra Workload ID**

---

## 1. Provider Configuration

When configuring a Generic OIDC provider:

- **Type**: `GENERIC_OIDC`
- **Issuer URL**: The canonical OIDC issuer URL matching the `iss` claim in issued JWTs.
- **Audience**: Expected audience (`aud`) claim.
- **JWKS URL**: The endpoint serving public signing keys. Can be manually specified or auto-discovered via standard `/.well-known/openid-configuration`.

---

## 2. Kubernetes Service Account Example

Kubernetes can project OIDC service account tokens into pods (`serviceAccountToken` volume projection):

### Kubernetes Pod Spec Example:
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: payment-processor
  namespace: prod-banking
spec:
  serviceAccountName: payment-service-sa
  containers:
    - name: app
      image: myregistry/payment-processor:v2.1
      volumeMounts:
        - name: vault-token
          mountPath: /var/run/secrets/tokens
  volumes:
    - name: vault-token
      projected:
        sources:
          - serviceAccountToken:
              path: secretvault-token
              expirationSeconds: 3600
              audience: https://vault.internal.net
```

### SecretVault Trust Policy:
- **Provider**: `Kubernetes Cluster Prod-East`
- **Claim Rules**:
  1. `kubernetes.io/namespace` `EQUALS` `prod-banking`
  2. `kubernetes.io/serviceaccount/name` `EQUALS` `payment-service-sa`
  3. `sub` `EQUALS` `system:serviceaccount:prod-banking:payment-service-sa`

---

## 3. Generic Claims Flattening & Extraction

For generic OIDC tokens with nested JSON claims (e.g. `{"kubernetes.io": {"namespace": "prod-banking", "serviceaccount": {"name": "app"}}}`), SecretVault's `GenericOidcClaimAdapter` flattens claims using dot notation:
- `kubernetes.io.namespace` -> `"prod-banking"`
- `kubernetes.io.serviceaccount.name` -> `"app"`
- `groups` -> `["payments-team", "engineering"]` (supported with `IN` / `NOT_IN` operators)

---

## 4. Discovery & Key Rotation

- For providers supporting standard OIDC discovery, SecretVault queries `${issuerUrl}/.well-known/openid-configuration` to discover `jwks_uri`.
- The discovery payload and JWKS keys are cached with configurable TTLs.
- If a token is presented with an unrecognized `kid`, SecretVault performs an on-demand cache refresh to support transparent key rotation.
