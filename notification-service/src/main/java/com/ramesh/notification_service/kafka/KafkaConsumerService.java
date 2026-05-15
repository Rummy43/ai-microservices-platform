package com.ramesh.notification_service.kafka;

import com.ramesh.events.UserCreatedEvent;
import com.ramesh.notification_service.common.CorrelationConstants;
import com.ramesh.notification_service.entity.DeadLetterEvent;
import com.ramesh.notification_service.repository.DeadLetterEventRepository;
import com.ramesh.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.KafkaMessageHeaderAccessor;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final NotificationService notificationService;
    private final DeadLetterEventRepository deadLetterEventRepository;

    @RetryableTopic(
            attempts = "4",
            backOff = @BackOff(delay = 2000, multiplier = 2.0),
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            exclude = {
                    org.springframework.kafka.support.serializer.DeserializationException.class
            }
    )
    @KafkaListener(topics = "user-created-topic", groupId = "notification-group")
    public void consume(UserCreatedEvent event,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset,
                        @Header(name = "traceId", required = false) byte[] traceIdHeader,
                        KafkaMessageHeaderAccessor accessor) {

        int attempt = accessor.getNonBlockingRetryDeliveryAttempt();

        String traceId = null;

        if (traceIdHeader != null) {
            traceId = new String(traceIdHeader, StandardCharsets.UTF_8);
            MDC.put("traceId", traceId);
        }

        try {
            log.info("Received UserCreatedEvent | attempt: {} | topic: {} | partition: {} | offset: {} | eventId: {} | email: {}",
                    attempt, topic, partition, offset, event.getEventId(), event.getEmail());

            boolean processed = notificationService.sendWelcomeNotification(
                    event, topic, partition, offset, attempt
            );

            if (processed) {
                log.info("Successfully processed UserCreatedEvent | eventId: {} | email: {} | attempt: {}",
                        event.getEventId(), event.getEmail(), attempt);
            }

        } catch (DataIntegrityViolationException ex) {
            log.warn("Duplicate event caught at DB level — skipping | eventId: {} | userId: {}",
                    event.getEventId(), event.getId());

        } catch (Exception ex) {
            log.warn("Failed to process UserCreatedEvent | attempt: {}/4 | userId: {} | email: {} | error: {}",
                    attempt, event.getId(), event.getEmail(), ex.getMessage());
            throw ex;

        } finally {
            MDC.remove("traceId");
        }
    }

    @DltHandler
    public void handleDlt(UserCreatedEvent event,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(KafkaHeaders.EXCEPTION_MESSAGE) String errorMessage) {

        log.error("DEAD LETTER — all retries exhausted | eventId: {} | userId: {} | email: {} | topic: {} | error: {}",
                event.getEventId(), event.getId(), event.getEmail(), topic, errorMessage);

        // ✅ Persist to DB for manual reprocessing / ops alerting
        deadLetterEventRepository.save(DeadLetterEvent.builder()
                .eventId(event.getEventId().toString())
                .eventType("USER_CREATED")
                .payload(String.format(
                        "{\"eventId\":\"%s\",\"userId\":\"%s\",\"email\":\"%s\",\"firstName\":\"%s\",\"lastName\":\"%s\"}",
                        event.getEventId(), event.getId(), event.getEmail(),   // ✅ eventId in payload too
                        event.getFirstName(), event.getLastName()))
                .topic(topic)
                .lastError(errorMessage)
                .failedAt(LocalDateTime.now())
                .reprocessed(false)
                .build());

        // TODO: alert ops team — PagerDuty / Slack webhook
    }
}