package com.ramesh.user_service.metrics;

import com.ramesh.user_service.enums.OutboxEventStatus;
import com.ramesh.user_service.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Supplier;

@Service
public class OutboxMetricsService {

    private final Counter publishedCounter;
    private final Counter failedCounter;
    private final Counter retriedCounter;
    private final Timer publishDurationTimer;

    public OutboxMetricsService(
            OutboxEventRepository repository,
            MeterRegistry meterRegistry) {

        Gauge.builder(
                        "outbox.pending",
                        repository,
                        r -> r.countByStatus(OutboxEventStatus.PENDING)
                )
                .description("Current number of pending outbox events")
                .register(meterRegistry);

        Gauge.builder(
                        "outbox.processing",
                        repository,
                        r -> r.countByStatus(OutboxEventStatus.PROCESSING)
                )
                .description("Current number of processing outbox events")
                .register(meterRegistry);

        Gauge.builder(
                        "outbox.failed",
                        repository,
                        r -> r.countByStatus(OutboxEventStatus.FAILED)
                )
                .description("Current number of failed outbox events")
                .register(meterRegistry);

        // Backlog AGE, not just count (SLI #7): a small number of events stuck for a
        // long time is a slow-drain failure that a pending-count gauge alone misses.
        Gauge.builder(
                        "outbox.oldest.pending.age.seconds",
                        repository,
                        r -> r.findOldestCreatedAtByStatus(OutboxEventStatus.PENDING)
                                .map(oldest -> (double) Duration.between(oldest, LocalDateTime.now()).getSeconds())
                                .orElse(0d)
                )
                .description("Age in seconds of the oldest unpublished (PENDING) outbox event")
                .register(meterRegistry);

        this.publishedCounter = Counter.builder("outbox_published_total")
                .description("Total successfully published outbox events")
                .register(meterRegistry);

        this.failedCounter = Counter.builder("outbox_failed_total")
                .description("Total permanently failed outbox events")
                .register(meterRegistry);

        this.retriedCounter = Counter.builder("outbox_retried_total")
                .description("Total transient outbox publish failures scheduled for retry")
                .register(meterRegistry);

        this.publishDurationTimer = Timer.builder("outbox_publish_duration")
                .description("Outbox publish duration")
                .register(meterRegistry);
    }

    public void incrementPublished() {
        publishedCounter.increment();
    }

    public void incrementFailed() {
        failedCounter.increment();
    }

    public void incrementRetried() {
        retriedCounter.increment();
    }

    public <T> T recordPublishDuration(Supplier<T> supplier) {
        return publishDurationTimer.record(supplier);
    }

    public void recordPublishDuration(Runnable runnable) {
        publishDurationTimer.record(runnable);
    }
}