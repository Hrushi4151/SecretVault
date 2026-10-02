package com.secretvault.auth.session.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Lightweight, safe parser for extracting client browser, operating system, and device labels
 * without introducing heavy or vulnerable external dependencies.
 */
public final class UserAgentParser {

    private UserAgentParser() {
    }

    public record DeviceMetadata(
            String browser,
            String operatingSystem,
            String deviceName,
            String sanitizedUserAgent
    ) {}

    /**
     * Parses the HTTP User-Agent header into clean, sanitized device and client platform metadata.
     */
    public static DeviceMetadata parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new DeviceMetadata("Unknown Browser", "Unknown OS", "Unknown Device", null);
        }

        String sanitizedUA = userAgent.trim();
        if (sanitizedUA.length() > 512) {
            sanitizedUA = sanitizedUA.substring(0, 512);
        }

        String os = parseOperatingSystem(sanitizedUA);
        String browser = parseBrowser(sanitizedUA);

        String deviceName;
        if ("Unknown Browser".equals(browser) && "Unknown OS".equals(os)) {
            deviceName = "Unknown Device";
        } else if ("Unknown OS".equals(os)) {
            deviceName = browser;
        } else if ("Unknown Browser".equals(browser)) {
            deviceName = os + " Device";
        } else {
            deviceName = browser + " on " + os;
        }

        if (deviceName.length() > 255) {
            deviceName = deviceName.substring(0, 255);
        }

        return new DeviceMetadata(browser, os, deviceName, sanitizedUA);
    }

    private static String parseBrowser(String ua) {
        String lower = ua.toLowerCase();

        if (lower.contains("edg/") || lower.contains("edge/")) {
            return "Edge";
        }
        if (lower.contains("opr/") || lower.contains("opera")) {
            return "Opera";
        }
        if (lower.contains("postmanruntime")) {
            return "Postman";
        }
        if (lower.contains("curl")) {
            return "curl";
        }
        if (lower.contains("chrome/") && !lower.contains("chromium")) {
            return "Chrome";
        }
        if (lower.contains("firefox/") || lower.contains("fxios")) {
            return "Firefox";
        }
        if (lower.contains("safari/") && !lower.contains("chrome/")) {
            return "Safari";
        }
        return "Unknown Browser";
    }

    private static String parseOperatingSystem(String ua) {
        String lower = ua.toLowerCase();

        if (lower.contains("windows nt 10.0") || lower.contains("windows")) {
            return "Windows";
        }
        if (lower.contains("iphone") || lower.contains("ipad") || lower.contains("ipod")) {
            return "iOS";
        }
        if (lower.contains("macintosh") || lower.contains("mac os x")) {
            return "macOS";
        }
        if (lower.contains("android")) {
            return "Android";
        }
        if (lower.contains("linux") || lower.contains("x11")) {
            return "Linux";
        }
        return "Unknown OS";
    }

    /**
     * Resolves the caller's IP address respecting standard proxy forwarding headers.
     */
    public static String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String clientIp = xForwardedFor.split(",")[0].trim();
            return sanitizeIp(clientIp);
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return sanitizeIp(xRealIp.trim());
        }

        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? sanitizeIp(remoteAddr.trim()) : "127.0.0.1";
    }

    private static String sanitizeIp(String ip) {
        if (ip == null) return "127.0.0.1";
        if (ip.length() > 64) {
            return ip.substring(0, 64);
        }
        return ip;
    }
}
