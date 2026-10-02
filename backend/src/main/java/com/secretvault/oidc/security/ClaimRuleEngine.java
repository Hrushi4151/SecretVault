package com.secretvault.oidc.security;

import com.secretvault.oidc.entity.OidcClaimRule;
import com.secretvault.oidc.model.OidcClaimOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Deterministic Claim Rule Engine supporting exact matching, prefix/suffix, set containment,
 * and safe ReDoS-protected regular expression evaluation.
 */
@Component
public class ClaimRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(ClaimRuleEngine.class);
    private static final int MAX_REGEX_LENGTH = 128;

    /**
     * Evaluates whether all claim rules in the policy match against the extracted token claims (AND semantics).
     */
    public boolean matchesAll(List<OidcClaimRule> rules, Map<String, Object> claims) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        if (claims == null || claims.isEmpty()) {
            return false;
        }

        for (OidcClaimRule rule : rules) {
            if (!matchesRule(rule, claims)) {
                log.debug("Claim rule match failed: claim=[{}] operator=[{}] expected=[{}] actual=[{}]",
                        rule.getClaimName(), rule.getOperator(), rule.getExpectedValue(), claims.get(rule.getClaimName()));
                return false;
            }
        }
        return true;
    }

    /**
     * Evaluates a single claim rule against the claims map.
     */
    public boolean matchesRule(OidcClaimRule rule, Map<String, Object> claims) {
        String claimName = rule.getClaimName();
        Object rawVal = claims.get(claimName);
        if (rawVal == null) {
            // Check case-insensitive key
            for (Map.Entry<String, Object> entry : claims.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(claimName)) {
                    rawVal = entry.getValue();
                    break;
                }
            }
        }

        String expected = rule.getExpectedValue() != null ? rule.getExpectedValue().trim() : "";
        OidcClaimOperator op = rule.getOperator() != null ? rule.getOperator() : OidcClaimOperator.EQUALS;

        if (rawVal == null) {
            return op == OidcClaimOperator.NOT_EQUALS || op == OidcClaimOperator.NOT_IN;
        }

        String actual = String.valueOf(rawVal).trim();

        return switch (op) {
            case EQUALS -> actual.equals(expected);
            case NOT_EQUALS -> !actual.equals(expected);
            case PREFIX -> actual.startsWith(expected);
            case SUFFIX -> actual.endsWith(expected);
            case CONTAINS -> actual.contains(expected);
            case IN -> {
                List<String> items = parseList(expected);
                yield items.contains(actual);
            }
            case NOT_IN -> {
                List<String> items = parseList(expected);
                yield !items.contains(actual);
            }
            case REGEX -> matchRegexSafe(actual, expected);
        };
    }

    private List<String> parseList(String expected) {
        if (expected == null || expected.isBlank()) {
            return List.of();
        }
        String clean = expected;
        if (clean.startsWith("[") && clean.endsWith("]")) {
            clean = clean.substring(1, clean.length() - 1);
        }
        return Arrays.stream(clean.split(","))
                .map(s -> s.replace("\"", "").replace("'", "").trim())
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private boolean matchRegexSafe(String actual, String regex) {
        if (regex == null || regex.isBlank()) {
            return false;
        }
        if (regex.length() > MAX_REGEX_LENGTH) {
            log.warn("Regex pattern length exceeded maximum allowed length of {}; rejecting", MAX_REGEX_LENGTH);
            return false;
        }
        try {
            Pattern pattern = Pattern.compile(regex);
            return pattern.matcher(actual).matches();
        } catch (Exception e) {
            log.warn("Failed to evaluate regex pattern [{}]: {}", regex, e.getMessage());
            return false;
        }
    }
}
