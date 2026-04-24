package com.ramesh.notification_service.service;

import com.ramesh.events.UserCreatedEvent;
import com.ramesh.notification_service.entity.NotificationLog;
import com.ramesh.notification_service.entity.ProcessedEvent;
import com.ramesh.notification_service.repository.NotificationLogRepository;
import com.ramesh.notification_service.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationLogRepository notificationLogRepository;

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
            logNotification(event, attempt, "SKIPPED", null);
            return false;
        }

        try {
            // TODO: plug in real email provider (SendGrid, AWS SES, etc.)
            // emailClient.sendWelcome(event.getEmail(), event.getFirstName());
            log.info("Sending welcome notification | userId: {} | email: {} | attempt: {}",
                    event.getId(), event.getEmail(), attempt);

            // ✅ Mark as processed BEFORE logging success (atomic with @Transactional)
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType("USER_CREATED")
                    .processedAt(LocalDateTime.now())
                    .topic(topic)
                    .partitionNumber(partition)
                    .offsetNumber(offset)
                    .build());

            logNotification(event, attempt, "SENT", null);

            log.info("Welcome notification sent successfully | userId: {} | email: {}",
                    event.getId(), event.getEmail());
            return true;

        } catch (Exception ex) {
            log.error("Failed to send welcome notification | userId: {} | email: {} | error: {}",
                    event.getId(), event.getEmail(), ex.getMessage());
            logNotification(event, attempt, "FAILED", ex.getMessage());
            throw ex; // rethrow to trigger Kafka retry
        }
    }

    private void logNotification(UserCreatedEvent event,
                                 int attempt,
                                 String status,
                                 String errorMessage) {
        notificationLogRepository.save(NotificationLog.builder()
                .userId(event.getId().toString())
                .email(event.getEmail().toString())
                .notificationType("WELCOME")
                .status(status)
                .attemptNumber(attempt)
                .errorMessage(errorMessage)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
    }
}