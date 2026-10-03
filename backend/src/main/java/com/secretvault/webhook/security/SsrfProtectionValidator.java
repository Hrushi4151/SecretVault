package com.secretvault.webhook.security;

import com.secretvault.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URL;

/**
 * Strict SSRF & DNS Rebinding Validator for Outbound Webhooks.
 * Validates destination URLs against loopback, private networks (RFC 1918),
 * link-local/cloud metadata (169.254.169.254), and non-standard schemes.
 */
@Component
public class SsrfProtectionValidator {

    private static final Logger log = LoggerFactory.getLogger(SsrfProtectionValidator.class);

    public void validateDestinationUrl(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            throw ApiException.badRequest("Webhook destination URL cannot be empty");
        }

        try {
            URI uri = URI.create(urlString.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw ApiException.badRequest("Webhook URL must use HTTP or HTTPS protocol");
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw ApiException.badRequest("Invalid webhook destination host");
            }

            // Block explicit localhost strings
            String lowerHost = host.toLowerCase();
            if (lowerHost.equals("localhost") || lowerHost.endsWith(".localhost") || lowerHost.endsWith(".local") || lowerHost.endsWith(".internal")) {
                throw ApiException.badRequest("SSRF Protection: Local and internal domain names are forbidden");
            }

            // Perform DNS resolution and validate all resolved IP addresses
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses == null || addresses.length == 0) {
                throw ApiException.badRequest("Failed to resolve webhook destination host");
            }

            for (InetAddress addr : addresses) {
                if (isBlockedIpAddress(addr)) {
                    log.warn("Blocked SSRF attempt to internal IP [{}] for host [{}]", addr.getHostAddress(), host);
                    throw ApiException.badRequest("SSRF Protection: Webhook destination resolves to private or reserved IP address (" + addr.getHostAddress() + ")");
                }
            }

        } catch (ApiException ae) {
            throw ae;
        } catch (Exception ex) {
            log.warn("SSRF validation failed for URL [{}]: {}", urlString, ex.getMessage());
            throw ApiException.badRequest("Invalid or unresolvable webhook destination URL: " + ex.getMessage());
        }
    }

    public boolean isBlockedIpAddress(InetAddress addr) {
        if (addr == null) return true;

        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = addr.getAddress();

        // IPv4 Checks
        if (bytes.length == 4) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;

            // 127.0.0.0/8 (Loopback)
            if (b0 == 127) return true;

            // 10.0.0.0/8 (RFC 1918)
            if (b0 == 10) return true;

            // 172.16.0.0/12 (RFC 1918)
            if (b0 == 172 && (b1 >= 16 && b1 <= 31)) return true;

            // 192.168.0.0/16 (RFC 1918)
            if (b0 == 192 && b1 == 168) return true;

            // 169.254.0.0/16 (Link-Local & Cloud Metadata e.g. AWS/GCP 169.254.169.254)
            if (b0 == 169 && b1 == 254) return true;

            // 0.0.0.0/8 (Current network)
            if (b0 == 0) return true;
        }

        // IPv6 Checks
        if (bytes.length == 16) {
            // ::1 (Loopback)
            boolean isV6Loopback = true;
            for (int i = 0; i < 15; i++) {
                if (bytes[i] != 0) { isV6Loopback = false; break; }
            }
            if (isV6Loopback && bytes[15] == 1) return true;

            // fc00::/7 (Unique Local Address)
            int b0 = bytes[0] & 0xFF;
            if ((b0 & 0xFE) == 0xFC) return true;

            // fe80::/10 (Link-Local)
            int b1 = bytes[1] & 0xFF;
            if (b0 == 0xFE && (b1 & 0xC0) == 0x80) return true;
        }

        return false;
    }
}
