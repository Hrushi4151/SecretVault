# SecretVault Python SDK (`secretvault`)

Official Python client SDK for secure secret retrieval, in-memory caching, resilience, and dynamic key rotation in Python (FastAPI, Django, Flask, Celery, scripts).

## Installation

```bash
pip install secretvault
```
*(Or install locally: `pip install -e ./sdk/secretvault-sdk-python`)*

## Quick Start

```python
import os
from secretvault import SecretVaultClient

# 1. Initialize client
vault = SecretVaultClient(
    endpoint="http://localhost:8080",
    workspace="myspace",
    project="auth-service",
    environment="production",
    token=os.getenv("SECRETVault_TOKEN"), # Machine Identity Token
    cache_ttl_sec=60.0,                   # In-memory cache for 60 seconds
    fail_open_stale=True                  # Resilience: serve cached stale value on network blip
)

# 2. Fetch single secret
db_pass = vault.secrets.get("DB_PASSWORD")
print(f"Secret: {db_pass.value} (v{db_pass.version_number})")

# 3. Batch fetch
secrets = vault.secrets.get_many(["REDIS_HOST", "REDIS_PORT", "REDIS_PASSWORD"])
print(secrets["REDIS_HOST"].value)

# 4. Dynamic Secret Rotation Listener (Hot-Reloading without Restart)
def on_rotated(new_secret):
    print(f"Key rotated to version v{new_secret.version_number}")
    # Re-initialize database connection pool

unwatch = vault.secrets.watch("DB_PASSWORD", on_rotated, poll_interval_sec=15.0)
```
