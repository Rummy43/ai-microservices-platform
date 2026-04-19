package com.ramesh.notification_service.kafka;

import com.ramesh.events.UserCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class KafkaConsumerService {

    @KafkaListener(topics = "user-created-topic", groupId = "notification-group")
    public void consume(UserCreatedEvent event) {
        log.info("Received: {}", event);
    }
}