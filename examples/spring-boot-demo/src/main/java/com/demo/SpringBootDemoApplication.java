package com.demo;

import io.secretvault.sdk.api.SecretVaultClient;
import io.secretvault.starter.annotation.SecretVaultValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@SpringBootApplication
@RestController
@RequestMapping("/api/demo")
public class SpringBootDemoApplication {

    @Autowired(required = false)
    private SecretVaultClient secretVaultClient;

    // Direct annotation injection
    @SecretVaultValue(value = "DB_PASSWORD", autoRefresh = true)
    private String directInjectedPassword;

    // Property placeholder resolution from application.yml
    @Value("${app.database-password:not_set}")
    private String propertyPassword;

    public static void main(String[] args) {
        SpringApplication.run(SpringBootDemoApplication.class, args);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getDemoStatus() {
        return ResponseEntity.ok(Map.of(
                "status", "RUNNING",
                "directInjectedResolved", directInjectedPassword != null && !directInjectedPassword.isEmpty(),
                "propertyResolved", propertyPassword != null && !propertyPassword.isEmpty(),
                "directMasked", mask(directInjectedPassword),
                "propertyMasked", mask(propertyPassword),
                "cacheSize", secretVaultClient != null ? secretVaultClient.getCache().size() : 0,
                "circuitBreaker", secretVaultClient != null ? secretVaultClient.getCircuitBreaker().getState().name() : "N/A"
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> triggerRefresh(@RequestParam String secretName) {
        if (secretVaultClient == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "SecretVaultClient bean not present"));
        }
        secretVaultClient.secrets().refresh(secretName);
        return ResponseEntity.ok(Map.of(
                "message", "Secret '" + secretName + "' refreshed successfully",
                "updatedDirectMasked", mask(directInjectedPassword)
        ));
    }

    private String mask(String val) {
        if (val == null || val.isEmpty()) return "[EMPTY]";
        return "•••••••• (" + val.length() + " chars)";
    }
}
