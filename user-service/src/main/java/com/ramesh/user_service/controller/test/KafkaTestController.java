package com.ramesh.user_service.controller.test;

import com.ramesh.events.UserCreatedEvent;
import com.ramesh.user_service.common.CorrelationConstants;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/kafka")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Kafka Events Validation", description = "Operations related to Kafka event validation")
public class KafkaTestController {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @PostMapping("/test/publish")
    public void publish(@RequestBody UserCreatedEvent event) {

        try {

            String traceId = MDC.get(CorrelationConstants.TRACE_ID);

            ProducerRecord<String, Object> record =
                    new ProducerRecord<>(
                            "user-created-topic",
                            event.getEventId().toString(),
                            event
                    );

            if (traceId != null) {
                record.headers().add(
                        CorrelationConstants.KAFKA_TRACE_ID_HEADER,
                        traceId.getBytes(StandardCharsets.UTF_8)
                );
            }

            kafkaTemplate.send(record);

            log.info("Published test UserCreatedEvent | eventId: {} | traceId: {}",
                    event.getEventId(),
                    traceId);

        } catch (Exception ex) {

            log.error("Failed to publish test UserCreatedEvent | eventId: {} | error: {}",
                    event.getEventId(),
                    ex.getMessage(),
                    ex);

            throw new RuntimeException(ex);
        }
    }
}
