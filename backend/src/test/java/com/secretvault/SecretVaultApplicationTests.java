package com.secretvault;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SecretVaultApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring Boot modular monolith application context loads successfully
    }
}
