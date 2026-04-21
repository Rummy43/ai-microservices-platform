package com.ramesh.notification_service.kafka;

import com.ramesh.events.UserCreatedEvent;
import com.ramesh.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final NotificationService notificationService;

    @RetryableTopic(
            attempts = "4",
            backOff = @BackOff(delay = 2000, multiplier = 2.0),
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            autoCreateTopics = "true",
            exclude = {
                    org.springframework.kafka.support.serializer.DeserializationException.class
            }
    )
    @KafkaListener(topics = "user-created-topic", groupId = "notification-group")
    public void consume(UserCreatedEvent event,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        KafkaMessageHeaderAccessor accessor) {

        int attempt = accessor.getNonBlockingRetryDeliveryAttempt();

        log.info("Processing UserCreatedEvent | attempt: {} | topic: {} | userId: {} | email: {}",
                attempt, topic, event.getId(), event.getEmail());

        try {
            notificationService.sendWelcomeNotification(event);

            log.info("Successfully processed UserCreatedEvent | userId: {} | email: {}",
                    event.getId(), event.getEmail());

        } catch (Exception ex) {
            log.warn("Failed to process UserCreatedEvent | attempt: {}/{} | userId: {} | email: {} | error: {}",
                    attempt, 4, event.getId(), event.getEmail(), ex.getMessage());
            throw ex;
        }
    }

    @DltHandler
    public void handleDlt(UserCreatedEvent event,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(KafkaHeaders.EXCEPTION_MESSAGE) String errorMessage) {

        log.error("DEAD LETTER — all retries exhausted | userId: {} | email: {} | topic: {} | lastError: {}",
                event.getId(), event.getEmail(), topic, errorMessage);

        // TODO: Store in a dead_letter_events table for manual reprocessing
        // TODO: Send alert to ops team (PagerDuty, Slack, etc.)
        // deadLetterRepository.save(new DeadLetterEvent(event, errorMessage));
    }
}