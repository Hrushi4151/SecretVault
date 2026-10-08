package com.secretvault;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SecretVault — DevSecOps Secret Management &amp; Security Control Plane.
 * Main entry point for the Spring Boot modular monolith application.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@ConfigurationPropertiesScan
public class SecretVaultApplication {

    public static void main(String[] args) {
        loadDotenvIfPresent();
        SpringApplication.run(SecretVaultApplication.class, args);
    }

    private static void loadDotenvIfPresent() {
        List<Path> candidatePaths = List.of(
                Path.of(".env"),
                Path.of("../.env"),
                Path.of("../../.env")
        );
        for (Path p : candidatePaths) {
            if (Files.isRegularFile(p)) {
                try (BufferedReader reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        int idx = line.indexOf('=');
                        if (idx > 0) {
                            String key = line.substring(0, idx).trim();
                            String val = line.substring(idx + 1).trim();
                            if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                                if (val.length() >= 2) {
                                    val = val.substring(1, val.length() - 1);
                                }
                            }
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, val);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                break;
            }
        }
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
