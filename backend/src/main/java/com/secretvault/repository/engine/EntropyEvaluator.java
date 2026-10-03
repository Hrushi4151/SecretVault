package com.secretvault.repository.engine;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Calculates Shannon entropy for candidate secret strings.
 * High entropy indicates random, cryptographic, or pseudo-random values.
 */
@Component
public class EntropyEvaluator {

    private static final double BASE64_HIGH_ENTROPY_THRESHOLD = 4.5;
    private static final double HEX_HIGH_ENTROPY_THRESHOLD = 3.0;

    /**
     * Calculates Shannon entropy: H = -sum(p * log2(p))
     */
    public double calculateEntropy(String input) {
        if (input == null || input.isEmpty()) {
            return 0.0;
        }

        Map<Character, Integer> frequencyMap = new HashMap<>();
        for (char c : input.toCharArray()) {
            frequencyMap.put(c, frequencyMap.getOrDefault(c, 0) + 1);
        }

        double length = input.length();
        double entropy = 0.0;

        for (int count : frequencyMap.values()) {
            double probability = count / length;
            entropy -= probability * (Math.log(probability) / Math.log(2));
        }

        return entropy;
    }

    public boolean isHighEntropy(String candidate) {
        if (candidate == null || candidate.length() < 16) {
            return false;
        }

        double entropy = calculateEntropy(candidate);

        // Check if string is predominantly hexadecimal
        boolean isHex = candidate.matches("^[0-9a-fA-F]+$");
        if (isHex) {
            return entropy >= HEX_HIGH_ENTROPY_THRESHOLD;
        }

        return entropy >= BASE64_HIGH_ENTROPY_THRESHOLD;
    }
}
