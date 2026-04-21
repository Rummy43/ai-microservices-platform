package com.ramesh.notification_service.kafka;

import com.ramesh.events.UserCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.KafkaMessageHeaderAccessor;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class KafkaConsumerService {

    @RetryableTopic(
            attempts = "4",
            backOff = @BackOff(delay = 2000, multiplier = 2.0),
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoCreateTopics = "true"
    )
    @KafkaListener(topics = "user-created-topic", groupId = "notification-group")
    public void consume(UserCreatedEvent event,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        KafkaMessageHeaderAccessor accessor) {

        int attempt = accessor.getNonBlockingRetryDeliveryAttempt();

        log.info(">>> ATTEMPT #{} | topic: {} | email: {}",
                attempt, topic, event.getEmail());

        throw new RuntimeException("Simulated failure on attempt #" + attempt);
    }

    @DltHandler
    public void handleDlt(UserCreatedEvent event,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error(">>> DLQ REACHED — all retries exhausted for email: {} on topic: {}",
                event.getEmail(), topic);
    }
}