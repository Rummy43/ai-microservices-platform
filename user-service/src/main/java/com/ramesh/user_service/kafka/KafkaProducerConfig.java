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
}