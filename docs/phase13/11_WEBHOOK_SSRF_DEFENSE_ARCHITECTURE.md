# Webhook SSRF Defense Architecture & Pre-Flight DNS Verification

## 1. Threat Landscape

Webhooks make outbound HTTP requests to user-supplied URLs. An attacker could register URLs pointing to:
- Cloud metadata services (`http://169.254.169.254/latest/meta-data/`) to steal IAM roles.
- Internal network services (`http://192.168.1.5:5432`, `http://10.0.0.1:6379`) to probe internal infrastructure.
- Localhost loopback addresses (`http://127.0.0.1:8080`) to bypass authentication boundaries.
- DNS rebinding endpoints returning a public IP on first resolve, then a private IP on connect.

## 2. Hardened Multi-Stage Defense

SecretVault implements defense-in-depth through `SsrfProtectionValidator`:

1. **Scheme Validation:** Only `https` (and in test mode, local `http`) is allowed. Schemes like `file://`, `gopher://`, `ftp://` are rejected.
2. **Pre-Connect DNS Resolution:** The destination hostname is resolved into all resolved `InetAddress` entries.
3. **CIDR Range Blacklist:**
   - Loopback: `127.0.0.0/8`, `::1`
   - RFC1918 Private: `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`
   - Link-Local / AWS/GCP Metadata: `169.254.0.0/16`, `fe80::/10`
   - Unique Local IPv6: `fc00::/7`
   - Any Local Address: `0.0.0.0`, `::`
4. **Disallowed HTTP Redirects:**
   Redirects (`followRedirects(Redirect.NEVER)`) are disabled to prevent an attacker from redirecting an allowed public URL to an internal IP address.
5. **Strict Timeouts & Size Limits:**
   Connection timeout of 5 seconds, request timeout of 10 seconds, and response body truncation at 1 MB.
