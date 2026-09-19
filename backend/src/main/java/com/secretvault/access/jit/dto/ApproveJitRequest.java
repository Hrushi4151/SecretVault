package com.secretvault.access.jit.dto;

import jakarta.validation.constraints.Size;

public record ApproveJitRequest(
        @Size(max = 1000, message = "Reviewer notes cannot exceed 1000 characters")
        String reviewerNotes
) {}
