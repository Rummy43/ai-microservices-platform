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
        log.info("Publishing UserCreatedEvent | userId: {} | email: {}",
                event.getId(), event.getEmail());
        try {
            kafkaTemplate.send("user-created-topic", event.getId().toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish UserCreatedEvent | userId: {} | email: {} | error: {}",
                                    event.getId(), event.getEmail(), ex.getMessage(), ex);
                        } else {
                            log.info("Successfully published UserCreatedEvent | userId: {} | email: {} | partition: {} | offset: {}",
                                    event.getId(),
                                    event.getEmail(),
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception ex) {
            log.error("Exception while preparing UserCreatedEvent for userId: {} | error: {}",
                    event.getId(), ex.getMessage(), ex);
            throw ex;
        }
    }
}