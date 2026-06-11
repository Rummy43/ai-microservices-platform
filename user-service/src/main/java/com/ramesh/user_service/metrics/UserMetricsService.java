package com.ramesh.user_service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class UserMetricsService {

    public static final String REASON_DUPLICATE_EMAIL = "duplicate_email";
    public static final String REASON_ERROR = "error";

    private final Counter createdCounter;
    private final Counter failedDuplicateEmailCounter;
    private final Counter failedErrorCounter;

    public UserMetricsService(MeterRegistry meterRegistry) {

        // "users_created_total" is intentionally avoided: the Prometheus client
        // strips the reserved OpenMetrics "_created" suffix and would expose it
        // as "users_total".
        this.createdCounter = Counter.builder("users_registered_total")
                .description("Total successfully created users")
                .register(meterRegistry);

        this.failedDuplicateEmailCounter = Counter.builder("users_creation_failed_total")
                .description("Total failed user creation attempts")
                .tag("reason", REASON_DUPLICATE_EMAIL)
                .register(meterRegistry);

        this.failedErrorCounter = Counter.builder("users_creation_failed_total")
                .description("Total failed user creation attempts")
                .tag("reason", REASON_ERROR)
                .register(meterRegistry);
    }

    public void incrementCreated() {
        createdCounter.increment();
    }

    public void incrementFailedDuplicateEmail() {
        failedDuplicateEmailCounter.increment();
    }

    public void incrementFailedError() {
        failedErrorCounter.increment();
    }
}