package com.secretvault.security.finding.dto;

import java.util.UUID;

public record AssignFindingRequest(
        UUID assigneeUserId
) {}
