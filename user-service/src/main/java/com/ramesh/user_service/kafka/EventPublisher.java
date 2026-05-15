package com.ramesh.user_service.kafka;

import com.ramesh.events.UserCreatedEvent;
import com.ramesh.user_service.common.CorrelationConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishUserCreatedEvent(UserCreatedEvent event) {
        log.info("Publishing UserCreatedEvent | userId: {} | email: {}",
                event.getId(), event.getEmail());
        try {
            String traceId = MDC.get(CorrelationConstants.TRACE_ID);

            ProducerRecord<String, Object> record = new ProducerRecord<>(
                    "user-created-topic",
                    event.getId().toString(),
                    event
            );

            if (traceId != null) {
                record.headers().add(
                        CorrelationConstants.KAFKA_TRACE_ID_HEADER,
                        traceId.getBytes(StandardCharsets.UTF_8)
                );
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
}