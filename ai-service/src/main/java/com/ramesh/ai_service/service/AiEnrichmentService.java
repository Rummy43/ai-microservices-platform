package com.ramesh.ai_service.service;

import com.ramesh.ai_service.dto.request.EnrichmentRequest;
import com.ramesh.ai_service.dto.response.EnrichmentResponse;

public interface AiEnrichmentService {
    EnrichmentResponse enrich(EnrichmentRequest request);
}
