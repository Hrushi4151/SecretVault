# SecretVault SDK — OIDC Workload Runtime Integration

## GitHub Actions Workload Integration

```yaml
permissions:
  id-token: write
  contents: read

jobs:
  run-app:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run Application with SecretVault SDK
        env:
          SECRETVAULT_ENDPOINT: ${{ secrets.SECRETVAULT_ENDPOINT }}
          SECRETVAULT_PROVIDER_ID: ${{ secrets.SECRETVAULT_PROVIDER_ID }}
          SECRETVAULT_MACHINE_ID: ${{ secrets.SECRETVAULT_MACHINE_ID }}
          SECRETVAULT_WORKSPACE: "default"
          SECRETVAULT_PROJECT: "payment-gateway"
          SECRETVAULT_ENVIRONMENT: "staging"
        run: |
          mvn spring-boot:run
```

The `OidcTokenProvider` requests an OIDC token from the GitHub Actions token service using `$ACTIONS_ID_TOKEN_REQUEST_TOKEN`, exchanges it for a 600s machine token at `/api/v1/oidc/auth/exchange`, and automatically refreshes prior to expiration.
