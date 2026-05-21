package com.ramesh.user_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ramesh.events.UserCreatedEvent;
import com.ramesh.user_service.entity.OutboxEvent;
import com.ramesh.user_service.enums.OutboxEventStatus;
import com.ramesh.user_service.kafka.EventPublisher;
import com.ramesh.user_service.outbox.payload.UserCreatedOutboxPayload;
import com.ramesh.user_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherService {

    private final OutboxEventRepository outboxEventRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents =
                outboxEventRepository.findTop20ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Found pending outbox events | count: {}", pendingEvents.size());

        for (OutboxEvent outboxEvent : pendingEvents) {
            publishSingleEvent(outboxEvent);
        }
    }

    private void publishSingleEvent(OutboxEvent outboxEvent) {
        try {
            if (!"USER_CREATED".equals(outboxEvent.getEventType())) {
                log.warn("Unsupported outbox event type | eventId: {} | eventType: {}",
                        outboxEvent.getEventId(), outboxEvent.getEventType());
                return;
            }

            UserCreatedOutboxPayload payload =
                    objectMapper.readValue(outboxEvent.getPayload(), UserCreatedOutboxPayload.class);

            UserCreatedEvent event = UserCreatedEvent.newBuilder()
                    .setEventId(payload.eventId())
                    .setId(payload.id())
                    .setFirstName(payload.firstName())
                    .setLastName(payload.lastName())
                    .setEmail(payload.email())
                    .build();

            eventPublisher.publishUserCreatedEvent(event);

            outboxEvent.setStatus(OutboxEventStatus.PUBLISHED);
            outboxEvent.setPublishedAt(LocalDateTime.now());
            outboxEvent.setLastError(null);

            log.info("Outbox event published successfully | outboxId: {} | eventId: {} | eventType: {}",
                    outboxEvent.getId(), outboxEvent.getEventId(), outboxEvent.getEventType());

        } catch (Exception ex) {
            int nextRetryCount = outboxEvent.getRetryCount() + 1;

            outboxEvent.setRetryCount(nextRetryCount);
            outboxEvent.setLastError(ex.getMessage());

            if (nextRetryCount >= 5) {
                outboxEvent.setStatus(OutboxEventStatus.FAILED);
                log.error("Outbox event permanently failed after max retries | outboxId: {} | eventId: {} | retryCount: {}",
                        outboxEvent.getId(), outboxEvent.getEventId(), nextRetryCount);
            } else {
                outboxEvent.setStatus(OutboxEventStatus.PENDING);
                log.warn("Outbox event publish failed, will retry | outboxId: {} | eventId: {} | retryCount: {} | error: {}",
                        outboxEvent.getId(), outboxEvent.getEventId(), nextRetryCount, ex.getMessage());
            }
        }
    }
}