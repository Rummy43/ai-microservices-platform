package com.ramesh.user_service.kafka;

import com.ramesh.events.UserCreatedEvent;
import com.ramesh.user_service.common.CorrelationConstants;
import com.ramesh.user_service.common.IdentityHeaders;
import com.ramesh.user_service.identity.IdentityContext;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the producer-side actor flow: the trace id and identity supplied to
 * {@link EventPublisher} (restored from the persisted outbox row) are written as
 * Kafka headers on the outgoing record. No broker required — the record is
 * captured from a mocked {@link KafkaTemplate}.
 */
@SuppressWarnings("unchecked")
class EventPublisherActorHeaderTest {

    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final EventPublisher eventPublisher = new EventPublisher(kafkaTemplate);

    @Test
    void actorAndTraceContextAreWrittenAsKafkaHeaders() {
        stubSend();
        IdentityContext actor = new IdentityContext("alice", "alice@example.com", List.of("USER", "ADMIN"));

        eventPublisher.publishUserCreatedEvent(sampleEvent(), "trace-abc-123", actor);

        ProducerRecord<String, Object> record = captureRecord();
        assertThat(headerValue(record, CorrelationConstants.KAFKA_TRACE_ID_HEADER)).isEqualTo("trace-abc-123");
        assertThat(headerValue(record, IdentityHeaders.KAFKA_USER_NAME)).isEqualTo("alice");
        assertThat(headerValue(record, IdentityHeaders.KAFKA_USER_EMAIL)).isEqualTo("alice@example.com");
        assertThat(headerValue(record, IdentityHeaders.KAFKA_USER_ROLES)).isEqualTo("USER,ADMIN");
    }

    @Test
    void nullActorOmitsIdentityHeadersButKeepsTrace() {
        stubSend();

        eventPublisher.publishUserCreatedEvent(sampleEvent(), "trace-xyz", null);

        ProducerRecord<String, Object> record = captureRecord();
        assertThat(headerValue(record, CorrelationConstants.KAFKA_TRACE_ID_HEADER)).isEqualTo("trace-xyz");
        assertThat(record.headers().lastHeader(IdentityHeaders.KAFKA_USER_NAME)).isNull();
        assertThat(record.headers().lastHeader(IdentityHeaders.KAFKA_USER_EMAIL)).isNull();
        assertThat(record.headers().lastHeader(IdentityHeaders.KAFKA_USER_ROLES)).isNull();
    }

    private void stubSend() {
        // Never-completing future: the publisher attaches a whenComplete callback
        // we don't need to run for header assertions.
        CompletableFuture<SendResult<String, Object>> pending = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(pending);
    }

    private ProducerRecord<String, Object> captureRecord() {
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        return captor.getValue();
    }

    private String headerValue(ProducerRecord<String, Object> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private UserCreatedEvent sampleEvent() {
        return UserCreatedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setId(UUID.randomUUID().toString())
                .setEmail("alice@example.com")
                .setFirstName("Alice")
                .setLastName("Smith")
                .build();
    }
}