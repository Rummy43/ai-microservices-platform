package com.ramesh.notification_service.service;

import com.ramesh.events.UserCreatedEvent;
import com.ramesh.notification_service.ai.AiEnrichmentClient;
import com.ramesh.notification_service.entity.NotificationLog;
import com.ramesh.notification_service.entity.ProcessedEvent;
import com.ramesh.notification_service.identity.IdentityContext;
import com.ramesh.notification_service.identity.IdentityContextHolder;
import com.ramesh.notification_service.metrics.NotificationMetricsService;
import com.ramesh.notification_service.repository.NotificationLogRepository;
import com.ramesh.notification_service.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final NotificationMetricsService notificationMetricsService;
    private final AiEnrichmentClient aiEnrichmentClient;

    private static final String FALLBACK_MESSAGE = "Welcome! Your account has been created successfully.";

    @Transactional
    public boolean sendWelcomeNotification(UserCreatedEvent event,
                                        String topic,
                                        int partition,
                                        long offset,
                                        int attempt) {

        String eventId = event.getEventId().toString();

        // ✅ Idempotency check — skip if already processed
        if (processedEventRepository.existsByEventId(eventId)) {
            log.warn("Duplicate event detected — skipping | eventId: {} | userId: {} | attempt: {}",
                    eventId, event.getId(), attempt);
            notificationMetricsService.incrementDuplicate();
            logNotification(event, attempt, "SKIPPED", null, null);
            return false;
        }

        try {
            // AI enrichment is non-fatal — falls back to FALLBACK_MESSAGE on any failure
            UUID userId = UUID.fromString(event.getId().toString());
            String userName = event.getFirstName().toString() + " " + event.getLastName().toString();
            String message = aiEnrichmentClient.enrich(userId, userName, event.getEmail().toString())
                    .orElse(FALLBACK_MESSAGE);

            log.info("Sending welcome notification | userId: {} | email: {} | attempt: {}",
                    event.getId(), event.getEmail(), attempt);

            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType("USER_CREATED")
                    .processedAt(LocalDateTime.now())
                    .topic(topic)
                    .partitionNumber(partition)
                    .offsetNumber(offset)
                    .build());

            logNotification(event, attempt, "SENT", null, message);
            notificationMetricsService.incrementSent();

            log.info("Welcome notification sent | userId: {} | email: {} | message: {}",
                    event.getId(), event.getEmail(), message);
            return true;

        } catch (DataIntegrityViolationException ex) {
            log.warn("Duplicate event detected at DB level — skipping | eventId: {} | userId: {} | attempt: {}",
                    eventId, event.getId(), attempt);

            notificationMetricsService.incrementDuplicate();
            logNotification(event, attempt, "SKIPPED", "Duplicate event detected at DB constraint level", null);
            return false;

        } catch (Exception ex) {
            log.error("Failed to send welcome notification | userId: {} | email: {} | error: {}",
                    event.getId(), event.getEmail(), ex.getMessage());
            notificationMetricsService.incrementFailed();
            logNotification(event, attempt, "FAILED", ex.getMessage(), null);
            throw ex;
        }
    }

    private void logNotification(UserCreatedEvent event,
                                 int attempt,
                                 String status,
                                 String errorMessage,
                                 String message) {

        // Identity is bound to the consumer thread by KafkaConsumerService from
        // the event's propagated Kafka headers.
        IdentityContext actor = IdentityContextHolder.get().orElse(null);

        notificationLogRepository.save(NotificationLog.builder()
                .userId(event.getId().toString())
                .email(event.getEmail().toString())
                .notificationType("WELCOME")
                .status(status)
                .attemptNumber(attempt)
                .errorMessage(errorMessage)
                .message(message)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .actorUsername(actor != null ? actor.username() : null)
                .actorEmail(actor != null ? actor.email() : null)
                .actorRoles(actor != null && !actor.roles().isEmpty() ? actor.rolesAsString() : null)
                .build());
    }
}
