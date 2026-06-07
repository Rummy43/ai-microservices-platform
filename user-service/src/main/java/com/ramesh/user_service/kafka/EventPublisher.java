package com.ramesh.user_service.kafka;

import com.ramesh.events.UserCreatedEvent;
import com.ramesh.user_service.common.CorrelationConstants;
import com.ramesh.user_service.common.IdentityHeaders;
import com.ramesh.user_service.identity.IdentityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private static final String TOPIC = "user-created-topic";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes a {@link UserCreatedEvent} together with the originating request's
     * trace and identity context as Kafka headers.
     *
     * <p>The context is supplied explicitly (from the persisted outbox row) rather
     * than read from MDC/ThreadLocal, because publishing happens on the scheduled
     * outbox thread where that ambient context is not present.
     */
    public void publishUserCreatedEvent(UserCreatedEvent event,
                                        String traceId,
                                        IdentityContext actor) {

        log.info("Publishing UserCreatedEvent | userId: {} | email: {} | actor: {}",
                event.getId(), event.getEmail(), actor != null ? actor.username() : null);

        try {
            ProducerRecord<String, Object> record = new ProducerRecord<>(
                    TOPIC,
                    event.getId().toString(),
                    event
            );

            addHeader(record, CorrelationConstants.KAFKA_TRACE_ID_HEADER, traceId);
            if (actor != null) {
                addHeader(record, IdentityHeaders.KAFKA_USER_NAME, actor.username());
                addHeader(record, IdentityHeaders.KAFKA_USER_EMAIL, actor.email());
                addHeader(record, IdentityHeaders.KAFKA_USER_ROLES, actor.rolesAsString());
            }

            kafkaTemplate.send(record)
                    .whenComplete((result, ex) -> {
                        try {
                            if (traceId != null) {
                                MDC.put(CorrelationConstants.TRACE_ID, traceId);
                            }

                            if (ex != null) {
                                log.error("Failed to publish UserCreatedEvent | traceId: {} | userId: {} | email: {} | error: {}",
                                        traceId, event.getId(), event.getEmail(), ex.getMessage(), ex);
                            } else {
                                log.info("Successfully published UserCreatedEvent | traceId: {} | userId: {} | email: {} | partition: {} | offset: {}",
                                        traceId,
                                        event.getId(),
                                        event.getEmail(),
                                        result.getRecordMetadata().partition(),
                                        result.getRecordMetadata().offset());
                            }
                        } finally {
                            MDC.remove(CorrelationConstants.TRACE_ID);
                        }
                    });

        } catch (Exception ex) {
            log.error("Exception while preparing UserCreatedEvent for userId: {} | error: {}",
                    event.getId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    private void addHeader(ProducerRecord<String, Object> record, String key, String value) {
        if (StringUtils.hasText(value)) {
            record.headers().add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}