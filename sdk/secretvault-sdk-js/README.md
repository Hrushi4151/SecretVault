# SecretVault JavaScript / TypeScript SDK (`@secretvault/sdk`)

Official client SDK for securely fetching, caching, and dynamically rotating runtime secrets in Node.js, Next.js, and TypeScript applications.

## Installation

```bash
npm install @secretvault/sdk
```

## Quick Start

```typescript
import { SecretVaultClient } from '@secretvault/sdk';

// 1. Initialize client
const vault = new SecretVaultClient({
  endpoint: 'http://localhost:8080',
  workspace: 'myspace',
  project: 'payment-gateway',
  environment: 'production',
  token: process.env.SECRETVault_TOKEN, // Machine Identity Token
  cache: {
    ttlSec: 60, // Cache in RAM for 60 seconds
  },
  failOpenStale: true // Resilience: serve stale cache if vault is momentarily down
});

// 2. Fetch secret
const dbPassword = await vault.secrets.get('DB_PASSWORD');
console.log(`Database Password: ${dbPassword.value} (v${dbPassword.versionNumber})`);

// 3. Batch fetch
const secrets = await vault.secrets.getMany(['API_KEY', 'STRIPE_SECRET', 'JWT_SECRET']);

// 4. Dynamic Secret Rotation Listener (Hot-Reloading without Restart)
const unwatch = vault.secrets.watch('JWT_SIGNING_KEY', (newSecret) => {
  console.log(`JWT Key rotated to version v${newSecret.versionNumber}`);
  updateAuthKeys(newSecret.value);
});
```
