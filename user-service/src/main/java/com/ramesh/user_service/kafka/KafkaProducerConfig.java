package com.ramesh.user_service.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaProducerConfig {

    @Bean
    public NewTopic userCreatedTopic() {
        return TopicBuilder.name("user-created-topic")
                .partitions(3)
                .replicas(1)
                .build();
    }

    // Pre-declare retry & DLT topics so Kafka doesn't auto-create them with wrong settings
    @Bean
    public NewTopic userCreatedRetry2000Topic() {
        return TopicBuilder.name("user-created-topic-retry-2000")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic userCreatedRetry4000Topic() {
        return TopicBuilder.name("user-created-topic-retry-4000")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic userCreatedRetry8000Topic() {
        return TopicBuilder.name("user-created-topic-retry-8000")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic userCreatedDltTopic() {
        return TopicBuilder.name("user-created-topic-dlt")
                .partitions(1)
                .replicas(1)
                .build();
    }
}