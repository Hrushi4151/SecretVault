package com.secretvault.webhook;

import com.secretvault.common.exception.ApiException;
import com.secretvault.webhook.security.SsrfProtectionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Phase 13: SSRF Protection Validator Tests")
class SsrfProtectionValidatorTest {

    private SsrfProtectionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SsrfProtectionValidator();
    }

    @Test
    @DisplayName("Reject blank or invalid URLs")
    void testBlankUrl() {
        assertThatThrownBy(() -> validator.validateDestinationUrl(null))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validateDestinationUrl(""))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validateDestinationUrl("not-a-url"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Reject non-HTTP/HTTPS protocols like file://, ftp://, gopher://")
    void testUnsafeProtocols() {
        assertThatThrownBy(() -> validator.validateDestinationUrl("file:///etc/passwd"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validateDestinationUrl("ftp://example.com/secret"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Reject localhost and internal domain names")
    void testInternalDomains() {
        assertThatThrownBy(() -> validator.validateDestinationUrl("http://localhost:8080/hook"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validateDestinationUrl("http://service.local/hook"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> validator.validateDestinationUrl("http://database.internal/hook"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Verify blocked IP detection for loopback, RFC1918 and AWS metadata")
    void testBlockedIps() throws Exception {
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("127.0.0.1"))).isTrue();
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("10.0.0.1"))).isTrue();
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("172.16.5.10"))).isTrue();
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("192.168.1.100"))).isTrue();
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("169.254.169.254"))).isTrue();
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("::1"))).isTrue();
    }

    @Test
    @DisplayName("Reject IPv4-mapped IPv6, IPv4-compatible IPv6, and 6to4 embedded private IPs")
    void testIpv6UnwrappingAndVariants() throws Exception {
        // IPv4-mapped IPv6 to loopback & metadata
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("::ffff:127.0.0.1"))).isTrue();
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("::ffff:169.254.169.254"))).isTrue();
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("::ffff:10.0.0.1"))).isTrue();
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("::ffff:192.168.1.1"))).isTrue();

        // Unique Local & Link-Local IPv6
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("fc00::1"))).isTrue();
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("fd12:3456:789a::1"))).isTrue();
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("fe80::1"))).isTrue();

        // IPv6 Documentation & Multicast
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("2001:db8::1"))).isTrue();
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("ff02::1"))).isTrue();

        // 6to4 with embedded 127.0.0.1
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("2002:7f00:0001::"))).isTrue();
    }

    @Test
    @DisplayName("Reject Carrier-Grade NAT, Benchmark, Documentation, Multicast and Reserved Class E IPv4")
    void testSpecialIpv4Ranges() throws Exception {
        // Carrier-Grade NAT RFC 6598 (100.64.0.0/10)
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("100.64.0.1"))).isTrue();
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("100.127.255.254"))).isTrue();

        // Benchmark Testing RFC 2544 (198.18.0.0/15)
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("198.18.0.1"))).isTrue();
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("198.19.255.254"))).isTrue();

        // Documentation RFC 5737
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("192.0.2.1"))).isTrue();
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("198.51.100.1"))).isTrue();
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("203.0.113.1"))).isTrue();

        // Multicast & Class E Reserved
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("224.0.0.1"))).isTrue();
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("240.0.0.1"))).isTrue();
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("255.255.255.255"))).isTrue();
    }

    @Test
    @DisplayName("Reject user-info credentials in destination URLs")
    void testUserInfoInUrl() {
        assertThatThrownBy(() -> validator.validateDestinationUrl("https://admin:secret@example.com/webhook"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("User-info credentials in destination URLs are forbidden");
    }

    @Test
    @DisplayName("Enforce HTTPS-only destination policy when allowHttp is false (production mode)")
    void testHttpsEnforcementInProduction() {
        validator.setAllowHttp(false);

        // HTTP must be rejected
        assertThatThrownBy(() -> validator.validateDestinationUrl("http://example.com/webhook"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Webhooks must use HTTPS protocol in production");

        // HTTPS remains permitted
        validator.setAllowHttp(true);
    }

    @Test
    @DisplayName("Public routable IP is not blocked")
    void testPublicIp() throws Exception {
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("8.8.8.8"))).isFalse();
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("1.1.1.1"))).isFalse();
    }
}
