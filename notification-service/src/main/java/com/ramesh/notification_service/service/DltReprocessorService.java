package com.ramesh.notification_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ramesh.events.UserCreatedEvent;
import com.ramesh.notification_service.entity.DeadLetterEvent;
import com.ramesh.notification_service.identity.IdentityContext;
import com.ramesh.notification_service.identity.IdentityContextHolder;
import com.ramesh.notification_service.metrics.NotificationMetricsService;
import com.ramesh.notification_service.repository.DeadLetterEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Self-healing dead-letter reprocessor (ADR-016).
 *
 * <p>On a fixed schedule, replays persisted {@code dead_letter_events} back through the
 * idempotent notification path. Successful replays are marked {@code reprocessed}; failed
 * replays increment an attempt counter that both drives an <b>exponential backoff</b> gate
 * (so a still-failing dependency isn't hammered) and enforces a <b>poison-message cap</b>
 * (so a genuinely bad event stops being retried instead of looping forever).
 *
 * <p>Reprocessing runs through {@link NotificationService#sendWelcomeNotification}, which is
 * idempotent (guarded by {@code processed_events}), so a replay of an event that actually did
 * succeed earlier is a safe no-op. The originating actor identity is restored from the row so
 * the replayed audit log keeps its attribution, and cleared in a {@code finally} block
 * (Virtual Threads discipline).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DltReprocessorService {

    /** Poison-message cap: stop retrying an event after this many failed replays. */
    private static final int MAX_REPROCESS_ATTEMPTS = 5;
    /** Max rows examined per cycle (bounds DB + processing load). */
    private static final int BATCH_SIZE = 50;
    /** Backoff base; effective wait = base * 2^attempts (exponential). */
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(30);

    private final DeadLetterEventRepository deadLetterEventRepository;
    private final NotificationService notificationService;
    private final NotificationMetricsService metricsService;

    // Constructed locally (this service has no auto-configured ObjectMapper bean).
    // Initialized inline → excluded from Lombok's @RequiredArgsConstructor.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Scheduled(fixedDelayString = "${notification.dlt.reprocess-interval-ms:30000}")
    public void reprocessDeadLetters() {
        List<DeadLetterEvent> candidates = deadLetterEventRepository
                .findByReprocessedFalseAndReprocessAttemptsLessThanOrderByFailedAtAsc(
                        MAX_REPROCESS_ATTEMPTS, PageRequest.of(0, BATCH_SIZE));
        if (candidates.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int drained = 0, backoffSkipped = 0, failed = 0;
        for (DeadLetterEvent dle : candidates) {
            if (!backoffElapsed(dle, now)) {
                backoffSkipped++;
                continue;
            }
            if (reprocessOne(dle)) {
                drained++;
            } else {
                failed++;
            }
        }
        log.info("DLT reprocess cycle | candidates: {} | drained: {} | backoff-skipped: {} | failed: {}",
                candidates.size(), drained, backoffSkipped, failed);
    }

    /** Exponential backoff gate: never-attempted rows run immediately; retried rows wait longer each time. */
    private boolean backoffElapsed(DeadLetterEvent dle, LocalDateTime now) {
        if (dle.getLastReprocessAttemptAt() == null) {
            return true;
        }
        Duration wait = BASE_BACKOFF.multipliedBy(1L << dle.getReprocessAttempts());
        return !now.isBefore(dle.getLastReprocessAttemptAt().plus(wait));
    }

    private boolean reprocessOne(DeadLetterEvent dle) {
        // Restore the originating actor so the replayed notification keeps its audit attribution.
        IdentityContextHolder.set(IdentityContext.fromCsvRoles(
                dle.getActorUsername(), dle.getActorEmail(), dle.getActorRoles()));
        try {
            UserCreatedEvent event = toEvent(dle.getPayload());

            // Idempotent: no exception => resolved (freshly sent, or already-processed no-op).
            notificationService.sendWelcomeNotification(
                    event, "dlt-reprocess", 0, 0, dle.getReprocessAttempts() + 1);

            dle.setReprocessed(true);
            dle.setReprocessedAt(LocalDateTime.now());
            dle.setLastReprocessAttemptAt(LocalDateTime.now());
            deadLetterEventRepository.save(dle);
            metricsService.incrementDltReprocessed();
            log.info("DLT event reprocessed | eventId: {} | actor: {}",
                    dle.getEventId(), dle.getActorUsername());
            return true;

        } catch (Exception ex) {
            int attempts = dle.getReprocessAttempts() + 1;
            dle.setReprocessAttempts(attempts);
            dle.setLastReprocessAttemptAt(LocalDateTime.now());
            deadLetterEventRepository.save(dle);
            metricsService.incrementDltReprocessFailed();
            if (attempts >= MAX_REPROCESS_ATTEMPTS) {
                log.error("DLT event POISON-CAPPED after {} attempts | eventId: {} | error: {}",
                        attempts, dle.getEventId(), ex.getMessage());
            } else {
                log.warn("DLT reprocess failed (attempt {}/{}) | eventId: {} | error: {}",
                        attempts, MAX_REPROCESS_ATTEMPTS, dle.getEventId(), ex.getMessage());
            }
            return false;
        } finally {
            IdentityContextHolder.clear();
        }
    }

    /** Rebuild the Avro event from the JSON payload persisted on the dead-letter row. */
    private UserCreatedEvent toEvent(String payload) throws Exception {
        JsonNode n = objectMapper.readTree(payload);
        return UserCreatedEvent.newBuilder()
                .setEventId(n.path("eventId").asText())
                .setId(n.path("userId").asText())     // payload key is "userId" → Avro field "id"
                .setEmail(n.path("email").asText())
                .setFirstName(n.path("firstName").asText())
                .setLastName(n.path("lastName").asText())
                .build();
    }
}