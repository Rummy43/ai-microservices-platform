package com.ramesh.notification_service.identity;

import com.ramesh.events.UserCreatedEvent;
import com.ramesh.notification_service.entity.NotificationLog;
import com.ramesh.notification_service.kafka.KafkaConsumerService;
import com.ramesh.notification_service.metrics.NotificationMetricsService;
import com.ramesh.notification_service.repository.DeadLetterEventRepository;
import com.ramesh.notification_service.repository.NotificationLogRepository;
import com.ramesh.notification_service.repository.ProcessedEventRepository;
import com.ramesh.notification_service.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.kafka.support.KafkaMessageHeaderAccessor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the consumer-side actor flow: identity carried on Kafka headers is
 * bound to the consumer thread and persisted onto the notification log row.
 *
 * <p>Exercises the real {@link KafkaConsumerService} → {@link NotificationService}
 * chain with mocked repositories, so no broker or database is required.
 */
class ActorPropagationFlowTest {

    private final ProcessedEventRepository processedEventRepository = mock(ProcessedEventRepository.class);
    private final NotificationLogRepository notificationLogRepository = mock(NotificationLogRepository.class);
    private final DeadLetterEventRepository deadLetterEventRepository = mock(DeadLetterEventRepository.class);

    private final NotificationMetricsService metricsService =
            new NotificationMetricsService(new SimpleMeterRegistry());

    private final NotificationService notificationService =
            new NotificationService(processedEventRepository, notificationLogRepository, metricsService);

    private final KafkaConsumerService consumerService =
            new KafkaConsumerService(notificationService, deadLetterEventRepository, metricsService);

    @AfterEach
    void tearDown() {
        IdentityContextHolder.clear();
    }

    @Test
    void actorHeadersFlowThroughToPersistedNotificationLog() {
        when(processedEventRepository.existsByEventId(any())).thenReturn(false);

        consumerService.consume(
                sampleEvent(),
                "user-created-topic", 0, 0L,
                bytes("trace-abc-123"),
                bytes("alice"),
                bytes("alice@example.com"),
                bytes("USER,ADMIN"),
                deliveryAttempt(1)
        );

        NotificationLog saved = captureSavedLog();
        assertThat(saved.getActorUsername()).isEqualTo("alice");
        assertThat(saved.getActorEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getActorRoles()).isEqualTo("USER,ADMIN");
    }

    @Test
    void identityContextIsClearedAfterProcessing() {
        when(processedEventRepository.existsByEventId(any())).thenReturn(false);

        consumerService.consume(
                sampleEvent(),
                "user-created-topic", 0, 0L,
                bytes("trace"), bytes("bob"), bytes("bob@example.com"), bytes("USER"),
                deliveryAttempt(1)
        );

        // No leakage onto the listener thread after the record is processed.
        assertThat(IdentityContextHolder.get()).isEmpty();
    }

    @Test
    void missingActorHeadersPersistNullActor() {
        when(processedEventRepository.existsByEventId(any())).thenReturn(false);

        consumerService.consume(
                sampleEvent(),
                "user-created-topic", 0, 0L,
                null, null, null, null,
                deliveryAttempt(1)
        );

        NotificationLog saved = captureSavedLog();
        assertThat(saved.getActorUsername()).isNull();
        assertThat(saved.getActorEmail()).isNull();
        assertThat(saved.getActorRoles()).isNull();
    }

    private NotificationLog captureSavedLog() {
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(captor.capture());
        return captor.getValue();
    }

    private KafkaMessageHeaderAccessor deliveryAttempt(int attempt) {
        KafkaMessageHeaderAccessor accessor = mock(KafkaMessageHeaderAccessor.class);
        when(accessor.getNonBlockingRetryDeliveryAttempt()).thenReturn(attempt);
        return accessor;
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

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}