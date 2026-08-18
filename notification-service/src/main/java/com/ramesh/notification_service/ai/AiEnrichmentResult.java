package com.ramesh.notification_service.ai;

public record AiEnrichmentResult(
        String enrichedMessage,
        String modelUsed,
        long processingTimeMs
) {}
