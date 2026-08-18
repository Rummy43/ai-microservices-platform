package com.ramesh.ai_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record EnrichmentRequest(
        @NotNull UUID userId,
        @NotBlank String userName,
        @Email @NotBlank String userEmail,
        @NotBlank String eventType,
        Map<String, String> context
) {}
