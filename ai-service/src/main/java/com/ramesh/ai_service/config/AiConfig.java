package com.ramesh.ai_service.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        You are an AI enrichment service for a distributed microservices platform.
                        Your role is to generate concise, personalized notification messages for users.
                        Keep messages professional, warm, and under 3 sentences.
                        Respond with only the notification text — no preamble, no explanation.
                        """)
                .build();
    }
}
