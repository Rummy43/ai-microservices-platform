package com.ramesh.notification_service.metrics;

import com.ramesh.notification_service.repository.DeadLetterEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class NotificationMetricsService {

    private final Counter sentCounter;
    private final Counter failedCounter;
    private final Counter duplicateCounter;
    private final Counter dltCounter;
    private final Counter dltReprocessedCounter;
    private final Counter dltReprocessFailedCounter;

    public NotificationMetricsService(MeterRegistry meterRegistry,
                                      DeadLetterEventRepository deadLetterEventRepository) {

        // Live DLT depth (SLI #11) — dead-letters still awaiting the self-healing
        // reprocessor. A gauge, not a counter: it can go down as the reprocessor drains.
        Gauge.builder("dlt.unreprocessed", deadLetterEventRepository,
                        DeadLetterEventRepository::countByReprocessedFalse)
                .description("Dead-letter events awaiting the self-healing reprocessor")
                .register(meterRegistry);

        this.sentCounter = Counter.builder("notifications_sent_total")
                .description("Total successfully sent notifications")
                .register(meterRegistry);

        this.failedCounter = Counter.builder("notifications_failed_total")
                .description("Total failed notification attempts")
                .register(meterRegistry);

        this.duplicateCounter = Counter.builder("notifications_duplicate_total")
                .description("Total duplicate events suppressed by the idempotent consumer")
                .register(meterRegistry);

        this.dltCounter = Counter.builder("notifications_dlt_total")
                .description("Total events routed to the dead letter topic after exhausting retries")
                .register(meterRegistry);

        this.dltReprocessedCounter = Counter.builder("notifications_dlt_reprocessed_total")
                .description("Total dead-letter events successfully drained by the self-healing reprocessor")
                .register(meterRegistry);

        this.dltReprocessFailedCounter = Counter.builder("notifications_dlt_reprocess_failed_total")
                .description("Total dead-letter reprocess attempts that failed (feeds the poison-message cap)")
                .register(meterRegistry);
    }

    public void incrementSent() {
        sentCounter.increment();
    }

    public void incrementFailed() {
        failedCounter.increment();
    }

    public void incrementDuplicate() {
        duplicateCounter.increment();
    }

    public void incrementDlt() {
        dltCounter.increment();
    }

    public void incrementDltReprocessed() {
        dltReprocessedCounter.increment();
    }

    public void incrementDltReprocessFailed() {
        dltReprocessFailedCounter.increment();
    }
}