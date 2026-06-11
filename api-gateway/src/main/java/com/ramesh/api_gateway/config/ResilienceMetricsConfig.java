package com.ramesh.api_gateway.config;

import io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetrics;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Cloud CircuitBreaker auto-binds circuit breaker and time limiter
 * metrics, but rate limiters sit outside its abstraction — bind them
 * explicitly so {@code resilience4j_ratelimiter_*} reaches Prometheus.
 */
@Configuration
public class ResilienceMetricsConfig {

    @Bean
    TaggedRateLimiterMetrics rateLimiterMetrics(RateLimiterRegistry rateLimiterRegistry,
                                                MeterRegistry meterRegistry) {
        TaggedRateLimiterMetrics metrics =
                TaggedRateLimiterMetrics.ofRateLimiterRegistry(rateLimiterRegistry);
        metrics.bindTo(meterRegistry);
        return metrics;
    }
}
