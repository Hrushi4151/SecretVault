package com.secretvault.oidc.security;

import com.secretvault.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * SSRF-Safe HTTP client for outbound OIDC discovery and JWKS requests.
 * Blocks private IP ranges, loopback addresses, cloud metadata endpoints,
 * enforces HTTPS (with development bypass flag), and limits timeouts & payload sizes.
 */
@Component
public class SsrfSafeHttpClient {

    private static final Logger log = LoggerFactory.getLogger(SsrfSafeHttpClient.class);

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final int MAX_RESPONSE_BYTES = 512 * 1024; // 512 KB

    private final boolean allowInsecureLocalHttp;

    public SsrfSafeHttpClient(
            @Value("${secretvault.oidc.allow-insecure-local-http:true}") boolean allowInsecureLocalHttp) {
        this.allowInsecureLocalHttp = allowInsecureLocalHttp;
    }

    /**
     * Safely executes an HTTP GET request to the target URL with SSRF protection.
     */
    public String executeSafeGet(String targetUrl) {
        if (targetUrl == null || targetUrl.isBlank()) {
            throw ApiException.badRequest("Target URL cannot be empty");
        }

        try {
            URI uri = URI.create(targetUrl.trim());
            String scheme = uri.getScheme();

            if (scheme == null) {
                throw ApiException.badRequest("Invalid URL scheme in: " + targetUrl);
            }

            if (!"https".equalsIgnoreCase(scheme)) {
                if ("http".equalsIgnoreCase(scheme) && allowInsecureLocalHttp && isLocalHostName(uri.getHost())) {
                    log.warn("Allowing non-HTTPS request to localhost in local/test environment: {}", targetUrl);
                } else {
                    throw ApiException.badRequest("HTTPS is strictly required for OIDC provider URLs: " + targetUrl);
                }
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw ApiException.badRequest("Invalid host in URL: " + targetUrl);
            }

            // Resolve DNS and perform SSRF checks on resolved IP addresses
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses == null || addresses.length == 0) {
                throw ApiException.badRequest("Failed to resolve host: " + host);
            }

            for (InetAddress address : addresses) {
                validateAddressAgainstSsrf(address, host);
            }

            // Open connection without following redirects automatically to avoid redirect-to-private-IP attacks
            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "SecretVault-OIDC-Validator/1.0");
            conn.setRequestProperty("Accept", "application/json");

            int responseCode = conn.getResponseCode();
            if (responseCode >= 300 && responseCode < 400) {
                String location = conn.getHeaderField("Location");
                throw ApiException.badRequest("Redirects are prohibited on OIDC metadata endpoints: redirect to " + location);
            }

            if (responseCode != 200) {
                throw ApiException.badRequest("OIDC endpoint returned HTTP status " + responseCode + " for URL: " + targetUrl);
            }

            // Read response with bounded size to prevent memory exhaustion / DoS
            try (InputStream in = conn.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int totalBytes = 0;
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    totalBytes += bytesRead;
                    if (totalBytes > MAX_RESPONSE_BYTES) {
                        throw ApiException.badRequest("Response from OIDC endpoint exceeded maximum allowed size of 512KB");
                    }
                    out.write(buffer, 0, bytesRead);
                }
                return out.toString(StandardCharsets.UTF_8);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Outbound OIDC request failed for URL [{}]: {}", targetUrl, e.getMessage());
            throw ApiException.badRequest("Failed to fetch OIDC metadata: " + e.getMessage());
        }
    }

    private boolean isLocalHostName(String host) {
        if (host == null) return false;
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    public void validateAddressAgainstSsrf(InetAddress address, String originalHost) {
        if (allowInsecureLocalHttp && isLocalHostName(originalHost)) {
            return;
        }

        if (address.isLoopbackAddress() || address.isAnyLocalAddress()) {
            throw ApiException.badRequest("Access to loopback/local address is forbidden: " + address.getHostAddress());
        }

        if (address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
            throw ApiException.badRequest("Access to private/site-local network address is forbidden: " + address.getHostAddress());
        }

        byte[] raw = address.getAddress();

        if (address instanceof Inet4Address) {
            int firstOctet = raw[0] & 0xFF;
            int secondOctet = raw[1] & 0xFF;

            // 10.0.0.0/8
            if (firstOctet == 10) {
                throw ApiException.badRequest("Access to private IP range 10.0.0.0/8 is forbidden");
            }
            // 172.16.0.0/12
            if (firstOctet == 172 && (secondOctet >= 16 && secondOctet <= 31)) {
                throw ApiException.badRequest("Access to private IP range 172.16.0.0/12 is forbidden");
            }
            // 192.168.0.0/16
            if (firstOctet == 192 && secondOctet == 168) {
                throw ApiException.badRequest("Access to private IP range 192.168.0.0/16 is forbidden");
            }
            // 169.254.0.0/16 (AWS / GCP / Cloud metadata IP)
            if (firstOctet == 169 && secondOctet == 254) {
                throw ApiException.badRequest("Access to cloud instance metadata endpoint is forbidden");
            }
            // 0.0.0.0/8
            if (firstOctet == 0) {
                throw ApiException.badRequest("Access to 0.0.0.0/8 range is forbidden");
            }
        } else if (address instanceof Inet6Address) {
            // IPv6 Unique Local Address fc00::/7
            if ((raw[0] & 0xFE) == 0xFC) {
                throw ApiException.badRequest("Access to IPv6 Unique Local Address range is forbidden");
            }
            // IPv6 Link-Local fe80::/10
            if ((raw[0] & 0xFF) == 0xFE && (raw[1] & 0xC0) == 0x80) {
                throw ApiException.badRequest("Access to IPv6 Link-Local address range is forbidden");
            }
        }
    }
}
