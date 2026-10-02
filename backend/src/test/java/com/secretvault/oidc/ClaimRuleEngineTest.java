package com.secretvault.oidc;

import com.secretvault.oidc.entity.OidcClaimRule;
import com.secretvault.oidc.model.OidcClaimOperator;
import com.secretvault.oidc.security.ClaimRuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClaimRuleEngineTest {

    private ClaimRuleEngine engine;
    private UUID dummyPolicyId;

    @BeforeEach
    void setUp() {
        engine = new ClaimRuleEngine();
        dummyPolicyId = UUID.randomUUID();
    }

    @Test
    @DisplayName("EQUALS operator matches exact string values")
    void testEqualsOperator() {
        OidcClaimRule rule = new OidcClaimRule(dummyPolicyId, "repository", OidcClaimOperator.EQUALS, "Hrushi4151/Rally");

        assertTrue(engine.matchesAll(List.of(rule), Map.of("repository", "Hrushi4151/Rally")));
        assertFalse(engine.matchesAll(List.of(rule), Map.of("repository", "Hrushi4151/Other")));
        assertFalse(engine.matchesAll(List.of(rule), Map.of("repository", "hrushi4151/rally"))); // case-sensitive exact match
    }

    @Test
    @DisplayName("NOT_EQUALS operator matches when value differs")
    void testNotEqualsOperator() {
        OidcClaimRule rule = new OidcClaimRule(dummyPolicyId, "ref", OidcClaimOperator.NOT_EQUALS, "refs/heads/experimental");

        assertTrue(engine.matchesAll(List.of(rule), Map.of("ref", "refs/heads/main")));
        assertFalse(engine.matchesAll(List.of(rule), Map.of("ref", "refs/heads/experimental")));
    }

    @Test
    @DisplayName("PREFIX and SUFFIX operators match boundary strings")
    void testPrefixAndSuffix() {
        OidcClaimRule prefixRule = new OidcClaimRule(dummyPolicyId, "ref", OidcClaimOperator.PREFIX, "refs/heads/");
        OidcClaimRule suffixRule = new OidcClaimRule(dummyPolicyId, "workflow", OidcClaimOperator.SUFFIX, ".yml");

        assertTrue(engine.matchesAll(List.of(prefixRule), Map.of("ref", "refs/heads/release/v1.0")));
        assertFalse(engine.matchesAll(List.of(prefixRule), Map.of("ref", "refs/tags/v1.0")));

        assertTrue(engine.matchesAll(List.of(suffixRule), Map.of("workflow", ".github/workflows/deploy.yml")));
        assertFalse(engine.matchesAll(List.of(suffixRule), Map.of("workflow", ".github/workflows/deploy.json")));
    }

    @Test
    @DisplayName("IN and NOT_IN operators match comma-separated sets")
    void testInAndNotIn() {
        OidcClaimRule inRule = new OidcClaimRule(dummyPolicyId, "environment", OidcClaimOperator.IN, "staging, production, prod");
        OidcClaimRule notInRule = new OidcClaimRule(dummyPolicyId, "actor", OidcClaimOperator.NOT_IN, "untrusted-bot, rogue-actor");

        assertTrue(engine.matchesAll(List.of(inRule), Map.of("environment", "production")));
        assertTrue(engine.matchesAll(List.of(inRule), Map.of("environment", "staging")));
        assertFalse(engine.matchesAll(List.of(inRule), Map.of("environment", "development")));

        assertTrue(engine.matchesAll(List.of(notInRule), Map.of("actor", "legit-developer")));
        assertFalse(engine.matchesAll(List.of(notInRule), Map.of("actor", "untrusted-bot")));
    }

    @Test
    @DisplayName("CONTAINS operator matches substrings")
    void testContains() {
        OidcClaimRule rule = new OidcClaimRule(dummyPolicyId, "job_workflow_ref", OidcClaimOperator.CONTAINS, "actions/deploy");

        assertTrue(engine.matchesAll(List.of(rule), Map.of("job_workflow_ref", "company/actions/deploy@v1")));
        assertFalse(engine.matchesAll(List.of(rule), Map.of("job_workflow_ref", "company/actions/test@v1")));
    }

    @Test
    @DisplayName("REGEX operator evaluates safe regular expressions")
    void testRegexOperator() {
        OidcClaimRule rule = new OidcClaimRule(dummyPolicyId, "ref", OidcClaimOperator.REGEX, "^refs/heads/(main|release/.*)$");

        assertTrue(engine.matchesAll(List.of(rule), Map.of("ref", "refs/heads/main")));
        assertTrue(engine.matchesAll(List.of(rule), Map.of("ref", "refs/heads/release/2.0")));
        assertFalse(engine.matchesAll(List.of(rule), Map.of("ref", "refs/pull/123/merge")));
    }

    @Test
    @DisplayName("Multiple rules must ALL match (AND semantics)")
    void testMultipleRulesAndSemantics() {
        List<OidcClaimRule> rules = List.of(
                new OidcClaimRule(dummyPolicyId, "repository", OidcClaimOperator.EQUALS, "Hrushi4151/Rally"),
                new OidcClaimRule(dummyPolicyId, "ref", OidcClaimOperator.EQUALS, "refs/heads/main"),
                new OidcClaimRule(dummyPolicyId, "environment", OidcClaimOperator.EQUALS, "production")
        );

        Map<String, Object> matchingClaims = Map.of(
                "repository", "Hrushi4151/Rally",
                "ref", "refs/heads/main",
                "environment", "production"
        );

        Map<String, Object> wrongBranch = Map.of(
                "repository", "Hrushi4151/Rally",
                "ref", "refs/heads/feature-123",
                "environment", "production"
        );

        Map<String, Object> wrongRepo = Map.of(
                "repository", "Hrushi4151/Fork",
                "ref", "refs/heads/main",
                "environment", "production"
        );

        assertTrue(engine.matchesAll(rules, matchingClaims));
        assertFalse(engine.matchesAll(rules, wrongBranch));
        assertFalse(engine.matchesAll(rules, wrongRepo));
    }
}
