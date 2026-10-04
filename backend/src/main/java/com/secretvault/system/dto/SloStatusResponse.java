package com.secretvault.system.dto;

import java.time.Instant;
import java.util.List;

public record SloStatusResponse(
        List<SloMetric> slos,
        double overallCompliancePercentage,
        Instant evaluatedAt
) {
    public record SloMetric(
            String name,
            String target,
            String current,
            boolean compliant,
            String unit,
            String description
    ) {}
}
