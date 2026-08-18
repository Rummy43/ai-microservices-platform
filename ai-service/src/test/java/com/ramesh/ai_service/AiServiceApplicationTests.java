package com.ramesh.ai_service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class AiServiceApplicationTests {

    @MockitoBean
    private ChatClient chatClient;

    @MockitoBean
    private VectorStore vectorStore;

    @Test
    void contextLoads() {
    }
}
