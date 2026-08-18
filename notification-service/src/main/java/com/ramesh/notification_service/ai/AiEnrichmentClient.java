package com.ramesh.notification_service.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiEnrichmentClient {

    private final RestClient aiRestClient;

    private static final String ENRICH_PATH = "/api/v1/ai/enrich";

    public Optional<String> enrich(UUID userId, String userName, String userEmail) {
        try {
            AiEnrichmentRequest request = new AiEnrichmentRequest(
                    userId, userName, userEmail, "USER_CREATED", null);

            AiApiResponse response = aiRestClient.post()
                    .uri(ENRICH_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiApiResponse.class);

            if (response != null && response.success() && response.data() != null
                    && response.data().enrichedMessage() != null) {
                log.info("AI enrichment successful | userId: {} | model: {} | processingTimeMs: {}",
                        userId, response.data().modelUsed(), response.data().processingTimeMs());
                return Optional.of(response.data().enrichedMessage());
            }

            log.warn("AI enrichment returned empty result | userId: {}", userId);
            return Optional.empty();

        } catch (Exception e) {
            log.warn("AI enrichment unavailable — using fallback | userId: {} | error: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }
}
