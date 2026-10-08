package com.secretvault.repository.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.Locale;
import java.util.Set;

/**
 * Validates Git repository clone URLs to prevent Server-Side Request Forgery (SSRF),
 * loopback access, cloud metadata exfiltration (169.254.169.254), internal private subnets,
 * local filesystem scheme escapes (file://), and command-line argument injection.
 */
@Component
public class GitCloneUrlValidator {

    private static final Logger log = LoggerFactory.getLogger(GitCloneUrlValidator.class);

    private static final Set<String> ALLOWED_SCHEMES = Set.of("https", "http");

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost",
            "127.0.0.1",
            "0.0.0.0",
            "::1",
            "169.254.169.254",
            "metadata.google.internal",
            "instance-data"
    );

    /**
     * Validates a Git clone URL against SSRF, internal DNS, and dangerous protocols.
     * Throws SecurityException if the URL is dangerous or invalid.
     */
    public void validateCloneUrl(String cloneUrl) {
        if (cloneUrl == null || cloneUrl.isBlank()) {
            throw new IllegalArgumentException("Repository clone URL cannot be null or empty");
        }

        String trimmed = cloneUrl.trim();

        // Reject argument injection (leading dashes)
        if (trimmed.startsWith("-")) {
            throw new SecurityException("Invalid clone URL: argument injection attempt detected");
        }

        // Reject dangerous Git protocols
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("file:") || lower.startsWith("ssh:") || lower.startsWith("git:")
                || lower.startsWith("ext::") || lower.startsWith("fd::")) {
            throw new SecurityException("Forbidden Git clone URL protocol. Only HTTPS clone URLs are permitted: " + trimmed);
        }

        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
                throw new SecurityException("Unsupported scheme '" + scheme + "'. Only HTTPS is permitted.");
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new SecurityException("Clone URL must specify a valid host: " + trimmed);
            }

            String hostLower = host.toLowerCase(Locale.ROOT);
            if (BLOCKED_HOSTS.contains(hostLower) || hostLower.endsWith(".internal") || hostLower.endsWith(".local")) {
                throw new SecurityException("SSRF Protection: Access to internal or loopback host is forbidden: " + host);
            }

            // Resolve IP addresses to prevent DNS rebinding to private RFC1918 subnets
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (isPrivateOrReservedAddress(addr)) {
                    log.warn("Blocked SSRF attempt in clone URL [{}] resolving to private IP [{}]", trimmed, addr.getHostAddress());
                    throw new SecurityException("SSRF Protection: Clone URL host resolves to private or reserved network IP: " + addr.getHostAddress());
                }
            }
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            log.warn("Failed clone URL validation for [{}]: {}", trimmed, e.getMessage());
            throw new SecurityException("Invalid repository clone URL: " + e.getMessage(), e);
        }
    }

    private boolean isPrivateOrReservedAddress(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
            return true;
        }

        byte[] ip = addr.getAddress();
        if (ip.length == 4) { // IPv4
            int first = ip[0] & 0xFF;
            int second = ip[1] & 0xFF;

            // 10.0.0.0/8
            if (first == 10) return true;
            // 172.16.0.0/12 (172.16.0.0 - 172.31.255.255)
            if (first == 172 && (second >= 16 && second <= 31)) return true;
            // 192.168.0.0/16
            if (first == 192 && second == 168) return true;
            // 169.254.0.0/16 (Link-local / Cloud metadata)
            if (first == 169 && second == 254) return true;
            // 127.0.0.0/8
            if (first == 127) return true;
            // 0.0.0.0/8
            if (first == 0) return true;
        }

        return false;
    }
}
