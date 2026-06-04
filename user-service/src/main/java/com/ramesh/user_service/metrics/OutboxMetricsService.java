package com.ramesh.user_service.metrics;

import com.ramesh.user_service.enums.OutboxEventStatus;
import com.ramesh.user_service.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class OutboxMetricsService {

    private final Counter publishedCounter;
    private final Counter failedCounter;
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

        this.publishedCounter = Counter.builder("outbox_published_total")
                .description("Total successfully published outbox events")
                .register(meterRegistry);

        this.failedCounter = Counter.builder("outbox_failed_total")
                .description("Total permanently failed outbox events")
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

    public <T> T recordPublishDuration(Supplier<T> supplier) {
        return publishDurationTimer.record(supplier);
    }

    public void recordPublishDuration(Runnable runnable) {
        publishDurationTimer.record(runnable);
    }
}