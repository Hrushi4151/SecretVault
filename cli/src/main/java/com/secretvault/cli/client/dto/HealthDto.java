package com.secretvault.cli.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HealthDto(
        String status,
        Instant timestamp,
        String version
) {}
