package com.secretvault.webhook.security;

import com.secretvault.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Hardened HTTP/1.1 Transport for Outbound Webhook Delivery.
 * Guarantees DNS Rebinding Protection by resolving and validating target IP addresses
 * before connection, pinning socket connection strictly to the validated IP,
 * and maintaining TLS SNI and hostname certificate verification.
 * Also enforces strict timeouts and bounded response body reading (preventing OOM).
 */
@Component
public class HardenedWebhookTransport {

    private static final Logger log = LoggerFactory.getLogger(HardenedWebhookTransport.class);

    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    public static final int DEFAULT_READ_TIMEOUT_MS = 10000;
    public static final int MAX_RESPONSE_BODY_BYTES = 64 * 1024; // 64 KB safe cap

    private final SsrfProtectionValidator ssrfValidator;
    private final SSLSocketFactory sslSocketFactory;

    public record WebhookHttpResponse(
            int statusCode,
            Map<String, String> headers,
            String body,
            long durationMs
    ) {
        public String getHeader(String name) {
            if (headers == null || name == null) return null;
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue();
                }
            }
            return null;
        }

        public Long getRetryAfterSeconds() {
            String val = getHeader("Retry-After");
            if (val == null || val.isBlank()) return null;
            try {
                long sec = Long.parseLong(val.trim());
                return Math.max(1, Math.min(300, sec)); // bounded between 1s and 300s
            } catch (NumberFormatException nfe) {
                return null;
            }
        }
    }

    public HardenedWebhookTransport(SsrfProtectionValidator ssrfValidator) {
        this.ssrfValidator = ssrfValidator;
        this.sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
    }

    public WebhookHttpResponse executePost(
            String destinationUrl,
            Map<String, String> headers,
            String payload,
            Duration connectTimeout,
            Duration readTimeout
    ) throws Exception {
        long startTime = System.currentTimeMillis();

        // 1. Resolve DNS and validate ALL resolved IP addresses against SSRF policies
        List<InetAddress> validatedAddresses = ssrfValidator.resolveAndValidateDestination(destinationUrl);
        if (validatedAddresses.isEmpty()) {
            throw ApiException.badRequest("No valid destination IP addresses found");
        }

        // 2. Pin connection to the first validated IP address (prevents DNS rebinding TOCTOU)
        InetAddress targetIp = validatedAddresses.get(0);

        URI uri = URI.create(destinationUrl.trim());
        String scheme = uri.getScheme().toLowerCase();
        boolean isHttps = "https".equals(scheme);
        String host = uri.getHost();
        int port = uri.getPort() != -1 ? uri.getPort() : (isHttps ? 443 : 80);

        String pathAndQuery = (uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath())
                + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");

        int connTimeoutMs = connectTimeout != null ? (int) connectTimeout.toMillis() : DEFAULT_CONNECT_TIMEOUT_MS;
        int rTimeoutMs = readTimeout != null ? (int) readTimeout.toMillis() : DEFAULT_READ_TIMEOUT_MS;

        byte[] payloadBytes = payload != null ? payload.getBytes(StandardCharsets.UTF_8) : new byte[0];

        Socket socket = new Socket();
        try {
            socket.setSoTimeout(rTimeoutMs);

            // Connect directly to the validated IP to defeat DNS rebinding
            socket.connect(new InetSocketAddress(targetIp, port), connTimeoutMs);

            Socket activeSocket = socket;
            if (isHttps) {
                // Wrap in SSLSocket with original hostname for SNI and TLS certificate verification
                SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(socket, host, port, true);
                sslSocket.startHandshake();

                boolean hostnameVerified = HttpsURLConnection.getDefaultHostnameVerifier()
                        .verify(host, sslSocket.getSession());
                if (!hostnameVerified) {
                    throw new SSLException("SSRF & TLS Guard: Hostname verification failed for host [" + host + "]");
                }
                activeSocket = sslSocket;
            }

            OutputStream out = new BufferedOutputStream(activeSocket.getOutputStream());
            InputStream in = new BufferedInputStream(activeSocket.getInputStream());

            // 3. Write HTTP/1.1 Request
            StringBuilder reqHeader = new StringBuilder();
            reqHeader.append("POST ").append(pathAndQuery).append(" HTTP/1.1\r\n");
            reqHeader.append("Host: ").append(host).append("\r\n");
            reqHeader.append("Content-Length: ").append(payloadBytes.length).append("\r\n");
            reqHeader.append("Connection: close\r\n");

            if (headers != null) {
                for (Map.Entry<String, String> h : headers.entrySet()) {
                    if (!h.getKey().equalsIgnoreCase("Host")
                            && !h.getKey().equalsIgnoreCase("Content-Length")
                            && !h.getKey().equalsIgnoreCase("Connection")) {
                        reqHeader.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
                    }
                }
            }
            reqHeader.append("\r\n");

            out.write(reqHeader.toString().getBytes(StandardCharsets.UTF_8));
            if (payloadBytes.length > 0) {
                out.write(payloadBytes);
            }
            out.flush();

            // 4. Read HTTP/1.1 Status Line and Headers
            String statusLine = readLine(in);
            if (statusLine == null || statusLine.isBlank()) {
                throw new IOException("Premature EOF reading HTTP status line from webhook destination");
            }

            int statusCode = parseStatusCode(statusLine);
            Map<String, String> responseHeaders = new LinkedHashMap<>();

            String headerLine;
            while ((headerLine = readLine(in)) != null && !headerLine.isEmpty()) {
                int colonIdx = headerLine.indexOf(':');
                if (colonIdx > 0) {
                    String hName = headerLine.substring(0, colonIdx).trim();
                    String hVal = headerLine.substring(colonIdx + 1).trim();
                    responseHeaders.put(hName, hVal);
                }
            }

            // 5. Read Bounded Response Body (Safe size limit max 64KB)
            ByteArrayOutputStream bodyBuf = new ByteArrayOutputStream();
            byte[] readChunk = new byte[4096];
            int readBytes;
            int totalBytes = 0;

            while ((readBytes = in.read(readChunk)) != -1) {
                if (totalBytes + readBytes <= MAX_RESPONSE_BODY_BYTES) {
                    bodyBuf.write(readChunk, 0, readBytes);
                    totalBytes += readBytes;
                } else {
                    int remaining = MAX_RESPONSE_BODY_BYTES - totalBytes;
                    if (remaining > 0) {
                        bodyBuf.write(readChunk, 0, remaining);
                        totalBytes += remaining;
                    }
                    log.warn("Webhook destination response body exceeded {} byte limit; truncated", MAX_RESPONSE_BODY_BYTES);
                    break;
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            String bodyString = bodyBuf.toString(StandardCharsets.UTF_8);

            return new WebhookHttpResponse(statusCode, responseHeaders, bodyString, duration);

        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
    }

    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\r') {
                continue;
            }
            if (b == '\n') {
                break;
            }
            lineBuf.write(b);
            if (lineBuf.size() > 8192) { // 8KB max header line
                throw new IOException("HTTP header line exceeded maximum length (8KB)");
            }
        }
        if (b == -1 && lineBuf.size() == 0) {
            return null;
        }
        return lineBuf.toString(StandardCharsets.UTF_8);
    }

    private int parseStatusCode(String statusLine) throws IOException {
        String[] parts = statusLine.split(" ");
        if (parts.length < 2) {
            throw new IOException("Malformed HTTP status line: " + statusLine);
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid HTTP status code in: " + statusLine, e);
        }
    }
}
