package com.ramesh.notification_service.ai;

import java.util.Map;
import java.util.UUID;

public record AiEnrichmentRequest(
        UUID userId,
        String userName,
        String userEmail,
        String eventType,
        Map<String, String> context
) {}
