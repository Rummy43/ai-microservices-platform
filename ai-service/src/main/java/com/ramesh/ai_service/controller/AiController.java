package com.ramesh.ai_service.controller;

import com.ramesh.ai_service.dto.request.EnrichmentRequest;
import com.ramesh.ai_service.dto.response.ApiResponse;
import com.ramesh.ai_service.dto.response.EnrichmentResponse;
import com.ramesh.ai_service.service.AiEnrichmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiEnrichmentService aiEnrichmentService;

    @PostMapping("/enrich")
    public ResponseEntity<ApiResponse<EnrichmentResponse>> enrich(
            @Valid @RequestBody EnrichmentRequest request) {
        log.info("Enrichment request received | userId: {} | eventType: {}",
                request.userId(), request.eventType());
        EnrichmentResponse result = aiEnrichmentService.enrich(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Enrichment complete", result));
    }
}
