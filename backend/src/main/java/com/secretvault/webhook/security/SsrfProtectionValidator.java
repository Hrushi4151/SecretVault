package com.secretvault.webhook.security;

import com.secretvault.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Strict SSRF & DNS Rebinding Validator for Outbound Webhooks.
 * Validates destination URLs against loopback, private networks (RFC 1918),
 * link-local/cloud metadata (169.254.169.254), Carrier-Grade NAT (RFC 6598),
 * multicast, reserved networks, IPv4-mapped IPv6, and non-standard schemes.
 */
@Component
public class SsrfProtectionValidator {

    private static final Logger log = LoggerFactory.getLogger(SsrfProtectionValidator.class);

    private boolean allowHttp = false;

    @Autowired
    public SsrfProtectionValidator(
            @Value("${secretvault.webhook.allow-http:false}") boolean allowHttp,
            Environment env
    ) {
        boolean isDevOrTest = env != null && (env.acceptsProfiles(Profiles.of("dev", "test"))
                || env.getProperty("spring.profiles.active", "").contains("test")
                || env.getProperty("spring.profiles.active", "").contains("dev"));
        this.allowHttp = allowHttp || isDevOrTest;
    }

    public SsrfProtectionValidator() {
        this(true, null); // Default permissive in standalone unit tests
    }

    public void setAllowHttp(boolean allowHttp) {
        this.allowHttp = allowHttp;
    }

    public boolean isAllowHttp() {
        return allowHttp;
    }

    /**
     * Validates destination URL against SSRF policy and resolves DNS records.
     * Returns list of all validated InetAddress instances.
     */
    public List<InetAddress> resolveAndValidateDestination(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            throw ApiException.badRequest("Webhook destination URL cannot be empty");
        }

        try {
            URI uri = URI.create(urlString.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw ApiException.badRequest("Webhook URL must use HTTP or HTTPS protocol");
            }

            if (scheme.equalsIgnoreCase("http") && !allowHttp) {
                throw ApiException.badRequest("SSRF Protection: Webhooks must use HTTPS protocol in production");
            }

            if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
                throw ApiException.badRequest("SSRF Protection: User-info credentials in destination URLs are forbidden");
            }

            int port = uri.getPort();
            if (port != -1 && (port <= 0 || port > 65535)) {
                throw ApiException.badRequest("Invalid port number: " + port);
            }

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw ApiException.badRequest("Invalid webhook destination host");
            }

            // Block explicit internal and cloud metadata hostnames
            String lowerHost = host.toLowerCase();
            if (lowerHost.equals("localhost")
                    || lowerHost.endsWith(".localhost")
                    || lowerHost.endsWith(".local")
                    || lowerHost.endsWith(".internal")
                    || lowerHost.endsWith(".lan")
                    || lowerHost.endsWith(".home.arpa")
                    || lowerHost.equals("metadata.google.internal")
                    || lowerHost.equals("169.254.169.254")) {
                throw ApiException.badRequest("SSRF Protection: Local and internal domain names are forbidden");
            }

            // Perform DNS resolution and validate all resolved IP addresses
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses == null || addresses.length == 0) {
                throw ApiException.badRequest("Failed to resolve webhook destination host: " + host);
            }

            List<InetAddress> validated = new ArrayList<>();
            for (InetAddress addr : addresses) {
                if (isBlockedIpAddress(addr)) {
                    log.warn("Blocked SSRF attempt to internal/reserved IP [{}] for host [{}]", addr.getHostAddress(), host);
                    throw ApiException.badRequest("SSRF Protection: Webhook destination resolves to private or reserved IP address (" + addr.getHostAddress() + ")");
                }
                validated.add(addr);
            }

            return validated;
        } catch (ApiException ae) {
            throw ae;
        } catch (Exception ex) {
            log.warn("SSRF validation failed for URL [{}]: {}", urlString, ex.getMessage());
            throw ApiException.badRequest("Invalid or unresolvable webhook destination URL: " + ex.getMessage());
        }
    }

    public void validateDestinationUrl(String urlString) {
        resolveAndValidateDestination(urlString);
    }

    public boolean isBlockedIpAddress(InetAddress addr) {
        if (addr == null) return true;

        if (addr.isAnyLocalAddress()
                || addr.isLoopbackAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()
                || addr.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = addr.getAddress();

        // IPv6 Checks & unwrapping
        if (bytes.length == 16) {
            // Check for IPv4-mapped IPv6 (::ffff:x.x.x.x)
            boolean isV4Mapped = true;
            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) { isV4Mapped = false; break; }
            }
            if (isV4Mapped && bytes[10] == (byte) 0xFF && bytes[11] == (byte) 0xFF) {
                byte[] v4Bytes = Arrays.copyOfRange(bytes, 12, 16);
                return isBlockedIpv4Bytes(v4Bytes);
            }

            // Check for IPv4-compatible IPv6 (::x.x.x.x)
            boolean isV4Compat = true;
            for (int i = 0; i < 12; i++) {
                if (bytes[i] != 0) { isV4Compat = false; break; }
            }
            if (isV4Compat) {
                byte[] v4Bytes = Arrays.copyOfRange(bytes, 12, 16);
                return isBlockedIpv4Bytes(v4Bytes);
            }

            // Check 6to4 prefix (2002::/16) -> bytes 2..5 contain embedded IPv4
            if (bytes[0] == 0x20 && bytes[1] == 0x02) {
                byte[] embeddedV4 = Arrays.copyOfRange(bytes, 2, 6);
                if (isBlockedIpv4Bytes(embeddedV4)) return true;
            }

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

            // 2001:db8::/32 (Documentation)
            if (bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x0d && bytes[3] == (byte) 0xb8) return true;

            // 100::/64 (Discard prefix)
            if (bytes[0] == 0x01 && bytes[1] == 0x00) return true;

            // ff00::/8 (Multicast)
            if ((bytes[0] & 0xFF) == 0xFF) return true;
        }

        // IPv4 Checks
        if (bytes.length == 4) {
            return isBlockedIpv4Bytes(bytes);
        }

        return false;
    }

    private boolean isBlockedIpv4Bytes(byte[] bytes) {
        if (bytes == null || bytes.length != 4) return true;

        int b0 = bytes[0] & 0xFF;
        int b1 = bytes[1] & 0xFF;
        int b2 = bytes[2] & 0xFF;
        int b3 = bytes[3] & 0xFF;

        // 0.0.0.0/8 (Current network / default route)
        if (b0 == 0) return true;

        // 10.0.0.0/8 (RFC 1918 Private)
        if (b0 == 10) return true;

        // 100.64.0.0/10 (Carrier-Grade NAT, RFC 6598)
        if (b0 == 100 && (b1 >= 64 && b1 <= 127)) return true;

        // 127.0.0.0/8 (Loopback)
        if (b0 == 127) return true;

        // 169.254.0.0/16 (Link-Local & Cloud Metadata e.g. AWS/GCP/Azure 169.254.169.254)
        if (b0 == 169 && b1 == 254) return true;

        // 172.16.0.0/12 (RFC 1918 Private)
        if (b0 == 172 && (b1 >= 16 && b1 <= 31)) return true;

        // 192.0.2.0/24 (TEST-NET-1 Documentation, RFC 5737)
        if (b0 == 192 && b1 == 0 && b2 == 2) return true;

        // 192.168.0.0/16 (RFC 1918 Private)
        if (b0 == 192 && b1 == 168) return true;

        // 198.18.0.0/15 (Network Benchmark Testing, RFC 2544)
        if (b0 == 198 && (b1 == 18 || b1 == 19)) return true;

        // 198.51.100.0/24 (TEST-NET-2 Documentation, RFC 5737)
        if (b0 == 198 && b1 == 51 && b2 == 100) return true;

        // 203.0.113.0/24 (TEST-NET-3 Documentation, RFC 5737)
        if (b0 == 203 && b1 == 0 && b2 == 113) return true;

        // 224.0.0.0/4 (Multicast, RFC 5771)
        if (b0 >= 224 && b0 <= 239) return true;

        // 240.0.0.0/4 (Reserved / Class E, RFC 1112)
        if (b0 >= 240) return true;

        // 255.255.255.255 (Broadcast)
        if (b0 == 255 && b1 == 255 && b2 == 255 && b3 == 255) return true;

        return false;
    }
}
