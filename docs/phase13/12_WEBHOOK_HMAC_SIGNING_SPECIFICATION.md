# Webhook HMAC-SHA256 Signing Specification

## 1. Overview

To allow receivers to authenticate that webhooks originated from SecretVault and have not been altered in transit, all outbound payloads are signed with an HMAC-SHA256 signature using the endpoint's unique signing secret.

## 2. Request Headers

Every outbound webhook delivery includes:

| Header Name | Format / Example | Description |
| :--- | :--- | :--- |
| `X-SecretVault-Signature` | `t=1759450800,v1=9c4a8b...` | Timestamp and computed HMAC hex digest |
| `X-SecretVault-Event-Id` | `a78d0f19-913b-419b-98b7-6a4ec701e85a` | Unique domain event UUID |
| `X-SecretVault-Event-Type`| `SECRET_ROTATION_COMPLETED` | Canonical event type name |
| `X-SecretVault-Delivery-Id`| `e415b3c1-0000-482a-9999-569269bb24e1` | Unique delivery attempt UUID |

## 3. Signature Construction

The signature payload is constructed as:
$$\text{signedPayload} = t + "." + \text{rawBody}$$
Where:
- $t$ is the current Unix epoch timestamp in seconds.
- $\text{rawBody}$ is the exact UTF-8 JSON request body.

The signature is computed using HMAC-SHA256 with the secret key:
$$\text{digest} = \text{HmacSHA256}(K_{\text{signing}}, \text{signedPayload})$$

The resulting header format is:
`X-SecretVault-Signature: t=1759450800,v1=4d8a1c9e37...`

## 4. Verification Algorithm (Receiver Pseudo-Code)

```python
import hmac, hashlib, time

def verify_signature(secret, header, body, tolerance_seconds=300):
    parts = dict(item.split("=") for item in header.split(","))
    timestamp = int(parts["t"])
    expected_v1 = parts["v1"]

    # Prevent replay attacks older than tolerance
    if abs(time.time() - timestamp) > tolerance_seconds:
        return False

    payload = f"{timestamp}.{body}".encode("utf-8")
    actual_v1 = hmac.new(secret.encode("utf-8"), payload, hashlib.sha256).hexdigest()

    return hmac.compare_digest(actual_v1, expected_v1)
```
