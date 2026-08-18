package com.ramesh.ai_service.dto.response;

import java.util.UUID;

public record EnrichmentResponse(
        UUID userId,
        String eventType,
        String enrichedMessage,
        String modelUsed,
        long processingTimeMs
) {}
