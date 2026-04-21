package com.ramesh.user_service.kafka;

import com.ramesh.events.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishUserCreatedEvent(UserCreatedEvent event) {
        log.info(">>> Publishing event for email: {}", event.getEmail()); // add this

        kafkaTemplate.send("user-created-topic", event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error(">>> FAILED to publish event for email: {} | error: {}",
                                event.getEmail(), ex.getMessage(), ex);
                    } else {
                        log.info(">>> PUBLISHED event for email: {} | partition: {} | offset: {}",
                                event.getEmail(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}