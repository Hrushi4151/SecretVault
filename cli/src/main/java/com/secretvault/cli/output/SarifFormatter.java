package com.secretvault.cli.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.secretvault.cli.client.dto.RepositoryCliDtos.SecretFindingDto;

import java.util.*;

/**
 * Generates standard SARIF v2.1.0 (Static Analysis Results Interchange Format)
 * reports for integration with CI/CD security gates, GitHub Code Scanning,
 * and GitLab Security Dashboards.
 * Zero plaintext secret values are exported.
 */
public class SarifFormatter {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static String formatSarif(List<SecretFindingDto> findings, String repositoryName) {
        Map<String, Object> sarif = new LinkedHashMap<>();
        sarif.put("$schema", "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json");
        sarif.put("version", "2.1.0");

        Map<String, Object> tool = new LinkedHashMap<>();
        Map<String, Object> driver = new LinkedHashMap<>();
        driver.put("name", "SecretVault Repository Scanner");
        driver.put("version", "1.0.0");
        driver.put("informationUri", "https://secretvault.security/docs/scanner");

        List<Map<String, Object>> rules = new ArrayList<>();
        Set<String> ruleIds = new HashSet<>();

        for (SecretFindingDto f : findings) {
            String ruleId = f.secretType() != null ? f.secretType() : "SECRET_LEAK";
            if (ruleIds.add(ruleId)) {
                Map<String, Object> rule = new LinkedHashMap<>();
                rule.put("id", ruleId);
                rule.put("name", ruleId);
                rule.put("shortDescription", Map.of("text", "Hardcoded " + ruleId + " credential detected"));
                rule.put("helpUri", "https://secretvault.security/rules/" + ruleId);
                rules.add(rule);
            }
        }
        driver.put("rules", rules);
        tool.put("driver", driver);

        List<Map<String, Object>> results = new ArrayList<>();
        for (SecretFindingDto f : findings) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ruleId", f.secretType() != null ? f.secretType() : "SECRET_LEAK");

            String level = switch (f.severity() != null ? f.severity().toUpperCase() : "MEDIUM") {
                case "CRITICAL", "HIGH" -> "error";
                case "MEDIUM" -> "warning";
                default -> "note";
            };
            result.put("level", level);
            result.put("message", Map.of("text", String.format("Exposed %s credential (%s) detected in %s at line %d",
                    f.secretType(), f.maskedEvidence() != null ? f.maskedEvidence() : "********",
                    f.filePath(), f.lineNumber() != null ? f.lineNumber() : 1)));

            Map<String, Object> region = new LinkedHashMap<>();
            region.put("startLine", f.lineNumber() != null ? f.lineNumber() : 1);
            region.put("startColumn", f.columnNumber() != null ? f.columnNumber() : 1);

            Map<String, Object> artifactLocation = new LinkedHashMap<>();
            artifactLocation.put("uri", f.filePath() != null ? f.filePath() : "unknown");

            Map<String, Object> physicalLocation = new LinkedHashMap<>();
            physicalLocation.put("artifactLocation", artifactLocation);
            physicalLocation.put("region", region);

            result.put("locations", List.of(Map.of("physicalLocation", physicalLocation)));
            results.add(result);
        }

        Map<String, Object> run = new LinkedHashMap<>();
        run.put("tool", tool);
        run.put("results", results);

        sarif.put("runs", List.of(run));

        try {
            return MAPPER.writeValueAsString(sarif);
        } catch (Exception e) {
            return "{\"version\": \"2.1.0\", \"runs\": []}";
        }
    }
}
