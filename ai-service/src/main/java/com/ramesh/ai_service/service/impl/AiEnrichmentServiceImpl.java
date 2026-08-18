package com.ramesh.ai_service.service.impl;

import com.ramesh.ai_service.dto.request.EnrichmentRequest;
import com.ramesh.ai_service.dto.response.EnrichmentResponse;
import com.ramesh.ai_service.service.AiEnrichmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiEnrichmentServiceImpl implements AiEnrichmentService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    @Value("${spring.ai.ollama.chat.model:llama3.2}")
    private String chatModel;

    @Override
    public EnrichmentResponse enrich(EnrichmentRequest request) {
        log.info("Enriching notification | userId: {} | eventType: {} | model: {}",
                request.userId(), request.eventType(), chatModel);

        long start = Instant.now().toEpochMilli();

        String prompt = buildPrompt(request);
        String enrichedMessage = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        long processingTimeMs = Instant.now().toEpochMilli() - start;

        storeEmbedding(request, enrichedMessage);

        log.info("Enrichment complete | userId: {} | processingTimeMs: {}",
                request.userId(), processingTimeMs);

        return new EnrichmentResponse(
                request.userId(),
                request.eventType(),
                enrichedMessage,
                chatModel,
                processingTimeMs
        );
    }

    private String buildPrompt(EnrichmentRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate a welcome notification for a new user.\n");
        sb.append("User name: ").append(request.userName()).append("\n");
        sb.append("User email: ").append(request.userEmail()).append("\n");
        sb.append("Event: ").append(request.eventType()).append("\n");

        if (request.context() != null && !request.context().isEmpty()) {
            sb.append("Additional context:\n");
            request.context().forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
        }

        return sb.toString();
    }

    private void storeEmbedding(EnrichmentRequest request, String enrichedMessage) {
        try {
            Map<String, Object> metadata = Map.of(
                    "userId", request.userId().toString(),
                    "eventType", request.eventType(),
                    "userEmail", request.userEmail()
            );
            vectorStore.add(List.of(new Document(enrichedMessage, metadata)));
            log.info("Embedding stored | userId: {}", request.userId());
        } catch (Exception e) {
            log.warn("Embedding storage failed — enrichment result still returned | userId: {} | error: {}",
                    request.userId(), e.getMessage());
        }
    }
}
