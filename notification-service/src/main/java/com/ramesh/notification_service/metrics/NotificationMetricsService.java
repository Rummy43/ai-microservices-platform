package com.ramesh.notification_service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class NotificationMetricsService {

    private final Counter sentCounter;
    private final Counter failedCounter;
    private final Counter duplicateCounter;

    public NotificationMetricsService(MeterRegistry meterRegistry) {

        this.sentCounter = Counter.builder("notifications_sent_total")
                .description("Total successfully sent notifications")
                .register(meterRegistry);

        this.failedCounter = Counter.builder("notifications_failed_total")
                .description("Total failed notification attempts")
                .register(meterRegistry);

        this.duplicateCounter = Counter.builder("notifications_duplicate_total")
                .description("Total duplicate events suppressed by the idempotent consumer")
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
}