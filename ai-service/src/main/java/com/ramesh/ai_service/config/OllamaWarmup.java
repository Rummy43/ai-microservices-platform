package com.ramesh.ai_service.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaWarmup {

    private final ChatClient chatClient;

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        // Load model weights before first real user request — runs on a virtual thread
        // so the health probe reports UP while warmup proceeds in the background.
        Thread.ofVirtual().name("ollama-warmup").start(() -> {
            log.info("Ollama warmup started — loading llama3.2 weights into memory");
            long start = System.currentTimeMillis();
            try {
                chatClient.prompt().user("ping").call().content();
                log.info("Ollama warmup complete | durationMs: {}", System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.warn("Ollama warmup failed — first real request will incur cold-start latency | error: {}",
                        e.getMessage());
            }
        });
    }
}
