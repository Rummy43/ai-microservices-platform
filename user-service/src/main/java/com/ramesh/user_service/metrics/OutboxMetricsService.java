package com.ramesh.user_service.metrics;

import com.ramesh.user_service.enums.OutboxEventStatus;
import com.ramesh.user_service.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class OutboxMetricsService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxMetricsService(OutboxEventRepository outboxEventRepository,
                                MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;

        Gauge.builder("outbox.events.pending.count",
                        () -> outboxEventRepository.countByStatus(OutboxEventStatus.PENDING))
                .description("Number of pending outbox events")
                .register(meterRegistry);

        Gauge.builder("outbox.events.processing.count",
                        () -> outboxEventRepository.countByStatus(OutboxEventStatus.PROCESSING))
                .description("Number of processing outbox events")
                .register(meterRegistry);

        Gauge.builder("outbox.events.failed.count",
                        () -> outboxEventRepository.countByStatus(OutboxEventStatus.FAILED))
                .description("Number of failed outbox events")
                .register(meterRegistry);
    }
}