package com.ramesh.notification_service.kafka;

import com.ramesh.events.UserCreatedEvent;
import com.ramesh.notification_service.common.CorrelationConstants;
import com.ramesh.notification_service.common.IdentityHeaders;
import com.ramesh.notification_service.entity.DeadLetterEvent;
import com.ramesh.notification_service.identity.IdentityContext;
import com.ramesh.notification_service.identity.IdentityContextHolder;
import com.ramesh.notification_service.metrics.NotificationMetricsService;
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
    private final NotificationMetricsService notificationMetricsService;

    @RetryableTopic(
            attempts = "4",
            backOff = @BackOff(delay = 2000, multiplier = 2.0),
            // ALWAYS_RETRY_ON_ERROR: a failing @DltHandler (e.g. audit DB down)
            // re-publishes the record to the DLT for another attempt instead of
            // dropping it — verified to prevent audit-row loss during outages.
            dltStrategy = DltStrategy.ALWAYS_RETRY_ON_ERROR,
            exclude = {
                    org.springframework.kafka.support.serializer.DeserializationException.class
            }
    )
    @KafkaListener(topics = "user-created-topic", groupId = "notification-group")
    public void consume(UserCreatedEvent event,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset,
                        @Header(name = CorrelationConstants.KAFKA_TRACE_ID_HEADER, required = false) byte[] traceIdHeader,
                        @Header(name = IdentityHeaders.KAFKA_USER_NAME, required = false) byte[] userNameHeader,
                        @Header(name = IdentityHeaders.KAFKA_USER_EMAIL, required = false) byte[] userEmailHeader,
                        @Header(name = IdentityHeaders.KAFKA_USER_ROLES, required = false) byte[] userRolesHeader,
                        KafkaMessageHeaderAccessor accessor) {

        int attempt = accessor.getNonBlockingRetryDeliveryAttempt();

        String traceId = decodeHeader(traceIdHeader);
        if (traceId != null) {
            MDC.put(CorrelationConstants.TRACE_ID, traceId);
        }

        IdentityContext actor = restoreIdentityContext(userNameHeader, userEmailHeader, userRolesHeader);

        try {
            log.info("Received UserCreatedEvent | attempt: {} | topic: {} | partition: {} | offset: {} | eventId: {} | email: {} | actor: {}",
                    attempt, topic, partition, offset, event.getEventId(), event.getEmail(), actor.username());

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
            clearIdentityContext();
            MDC.remove(CorrelationConstants.TRACE_ID);
        }
    }

    /** Rebuilds the originating user's identity from Kafka headers and binds it to the consumer thread + MDC. */
    private IdentityContext restoreIdentityContext(byte[] userNameHeader,
                                                   byte[] userEmailHeader,
                                                   byte[] userRolesHeader) {

        IdentityContext actor = IdentityContext.fromCsvRoles(
                decodeHeader(userNameHeader),
                decodeHeader(userEmailHeader),
                decodeHeader(userRolesHeader)
        );

        IdentityContextHolder.set(actor);

        if (actor.username() != null) {
            MDC.put(IdentityHeaders.MDC_USERNAME, actor.username());
        }
        if (actor.email() != null) {
            MDC.put(IdentityHeaders.MDC_EMAIL, actor.email());
        }
        if (!actor.roles().isEmpty()) {
            MDC.put(IdentityHeaders.MDC_ROLES, actor.rolesAsString());
        }

        return actor;
    }

    private void clearIdentityContext() {
        MDC.remove(IdentityHeaders.MDC_USERNAME);
        MDC.remove(IdentityHeaders.MDC_EMAIL);
        MDC.remove(IdentityHeaders.MDC_ROLES);
        IdentityContextHolder.clear();
    }

    private String decodeHeader(byte[] header) {
        return header != null ? new String(header, StandardCharsets.UTF_8) : null;
    }

    @DltHandler
    public void handleDlt(UserCreatedEvent event,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(KafkaHeaders.EXCEPTION_MESSAGE) String errorMessage,
                          @Header(name = CorrelationConstants.KAFKA_TRACE_ID_HEADER, required = false) byte[] traceIdHeader,
                          @Header(name = IdentityHeaders.KAFKA_USER_NAME, required = false) byte[] userNameHeader,
                          @Header(name = IdentityHeaders.KAFKA_USER_EMAIL, required = false) byte[] userEmailHeader,
                          @Header(name = IdentityHeaders.KAFKA_USER_ROLES, required = false) byte[] userRolesHeader) {

        String traceId = decodeHeader(traceIdHeader);
        if (traceId != null) {
            MDC.put(CorrelationConstants.TRACE_ID, traceId);
        }

        IdentityContext actor = restoreIdentityContext(userNameHeader, userEmailHeader, userRolesHeader);

        try {
            log.error("DEAD LETTER — all retries exhausted | eventId: {} | userId: {} | email: {} | topic: {} | actor: {} | error: {}",
                    event.getEventId(), event.getId(), event.getEmail(), topic, actor.username(), errorMessage);

            // Counted before persistence so the signal survives even if the
            // audit insert itself fails; DLT redeliveries may recount.
            notificationMetricsService.incrementDlt();

            // ✅ Persist to DB for manual reprocessing / ops alerting — including
            // the originating actor so triage retains the audit context.
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
                    .actorUsername(actor.username())
                    .actorEmail(actor.email())
                    .actorRoles(actor.roles().isEmpty() ? null : actor.rolesAsString())
                    .build());

            // TODO: alert ops team — PagerDuty / Slack webhook
        } finally {
            clearIdentityContext();
            MDC.remove(CorrelationConstants.TRACE_ID);
        }
    }
}