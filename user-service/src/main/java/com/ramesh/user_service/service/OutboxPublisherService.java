package com.ramesh.user_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ramesh.events.UserCreatedEvent;
import com.ramesh.user_service.entity.OutboxEvent;
import com.ramesh.user_service.enums.OutboxEventStatus;
import com.ramesh.user_service.kafka.EventPublisher;
import com.ramesh.user_service.metrics.OutboxMetricsService;
import com.ramesh.user_service.outbox.payload.UserCreatedOutboxPayload;
import com.ramesh.user_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherService {

    private static final int MAX_RETRY_COUNT = 5;
    private static final String USER_CREATED_EVENT = "USER_CREATED";

    private final OutboxEventRepository outboxEventRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final OutboxEventProcessingService processingService;
    private final OutboxMetricsService metricsService;

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents =
                outboxEventRepository.findTop20ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Found pending outbox events | count: {}", pendingEvents.size());

        for (OutboxEvent outboxEvent : pendingEvents) {
            boolean claimed = processingService.markAsProcessing(outboxEvent.getId());

            if (!claimed) {
                log.warn("Outbox event already claimed by another processor | outboxId: {}",
                        outboxEvent.getId());
                continue;
            }

            outboxEvent.setStatus(OutboxEventStatus.PROCESSING);
            outboxEvent.setProcessingStartedAt(LocalDateTime.now());

            publishSingleEvent(outboxEvent);
        }
    }

    private void publishSingleEvent(OutboxEvent outboxEvent) {
        try {
            if (!USER_CREATED_EVENT.equals(outboxEvent.getEventType())) {
                outboxEvent.setStatus(OutboxEventStatus.FAILED);
                metricsService.incrementFailed();
                outboxEvent.setProcessingStartedAt(null);
                outboxEvent.setLastError("Unsupported event type: " + outboxEvent.getEventType());

                processingService.saveProcessingResult(outboxEvent);

                log.warn("Unsupported outbox event type | outboxId: {} | eventId: {} | eventType: {}",
                        outboxEvent.getId(), outboxEvent.getEventId(), outboxEvent.getEventType());
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

            metricsService.recordPublishDuration(() ->
                    eventPublisher.publishUserCreatedEvent(event)
            );

            outboxEvent.setStatus(OutboxEventStatus.PUBLISHED);
            metricsService.incrementPublished();
            outboxEvent.setProcessingStartedAt(null);
            outboxEvent.setPublishedAt(LocalDateTime.now());
            outboxEvent.setLastError(null);

            processingService.saveProcessingResult(outboxEvent);

            log.info("Outbox event published successfully | outboxId: {} | eventId: {} | eventType: {}",
                    outboxEvent.getId(), outboxEvent.getEventId(), outboxEvent.getEventType());

        } catch (Exception ex) {
            int nextRetryCount = outboxEvent.getRetryCount() + 1;

            outboxEvent.setRetryCount(nextRetryCount);
            outboxEvent.setLastError(ex.getMessage());
            outboxEvent.setProcessingStartedAt(null);

            if (nextRetryCount >= MAX_RETRY_COUNT) {
                outboxEvent.setStatus(OutboxEventStatus.FAILED);
                metricsService.incrementFailed();

                log.error("Outbox event permanently failed after max retries | outboxId: {} | eventId: {} | retryCount: {}",
                        outboxEvent.getId(), outboxEvent.getEventId(), nextRetryCount);
            } else {
                outboxEvent.setStatus(OutboxEventStatus.PENDING);

                log.warn("Outbox event publish failed, will retry | outboxId: {} | eventId: {} | retryCount: {} | error: {}",
                        outboxEvent.getId(), outboxEvent.getEventId(), nextRetryCount, ex.getMessage());
            }

            processingService.saveProcessingResult(outboxEvent);
        }
    }
}