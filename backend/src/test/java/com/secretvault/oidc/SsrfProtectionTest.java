package com.secretvault.oidc;

import com.secretvault.common.exception.ApiException;
import com.secretvault.oidc.security.SsrfSafeHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SsrfProtectionTest {

    private SsrfSafeHttpClient strictClient;

    @BeforeEach
    void setUp() {
        strictClient = new SsrfSafeHttpClient(false); // Insecure local HTTP disabled
    }

    @Test
    @DisplayName("SSRF client blocks loopback localhost addresses in strict mode")
    void testLoopbackBlocked() {
        ApiException ex1 = assertThrows(ApiException.class, () ->
                strictClient.executeSafeGet("http://localhost:8080/jwks"));
        assertTrue(ex1.getMessage().contains("HTTPS is strictly required") || ex1.getMessage().contains("loopback"));

        ApiException ex2 = assertThrows(ApiException.class, () ->
                strictClient.executeSafeGet("http://127.0.0.1:8080/jwks"));
        assertTrue(ex2.getMessage().contains("HTTPS is strictly required") || ex2.getMessage().contains("loopback"));
    }

    @Test
    @DisplayName("SSRF client blocks RFC1918 private network IPs")
    void testPrivateIpsBlocked() {
        assertThrows(ApiException.class, () -> strictClient.executeSafeGet("https://10.0.0.1/jwks"));
        assertThrows(ApiException.class, () -> strictClient.executeSafeGet("https://192.168.1.100/jwks"));
        assertThrows(ApiException.class, () -> strictClient.executeSafeGet("https://172.16.0.5/jwks"));
    }

    @Test
    @DisplayName("SSRF client blocks AWS / Cloud metadata endpoints (169.254.169.254)")
    void testCloudMetadataBlocked() {
        ApiException ex = assertThrows(ApiException.class, () ->
                strictClient.executeSafeGet("http://169.254.169.254/latest/meta-data"));
        assertTrue(ex.getMessage().contains("HTTPS is strictly required") || ex.getMessage().contains("Link-local") || ex.getMessage().contains("Private"));
    }

    @Test
    @DisplayName("SSRF client enforces HTTPS in strict production mode")
    void testHttpsEnforcedInProduction() {
        ApiException ex = assertThrows(ApiException.class, () ->
                strictClient.executeSafeGet("http://token.actions.githubusercontent.com/.well-known/jwks"));
        assertTrue(ex.getMessage().contains("HTTPS is strictly required"));
    }
}
