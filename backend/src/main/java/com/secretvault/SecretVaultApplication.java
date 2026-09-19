package com.secretvault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import org.springframework.context.annotation.Bean;
import java.time.Clock;

/**
 * SecretVault — DevSecOps Secret Management &amp; Security Control Plane.
 * Main entry point for the Spring Boot modular monolith application.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SecretVaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecretVaultApplication.class, args);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
