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
    @DisplayName("Public routable IP is not blocked")
    void testPublicIp() throws Exception {
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("8.8.8.8"))).isFalse();
        assertThat(validator.isBlockedIpAddress(InetAddress.getByName("1.1.1.1"))).isFalse();
    }
}
