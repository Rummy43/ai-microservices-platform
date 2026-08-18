package com.ramesh.notification_service.ai;

public record AiApiResponse(
        boolean success,
        String message,
        AiEnrichmentResult data
) {}
