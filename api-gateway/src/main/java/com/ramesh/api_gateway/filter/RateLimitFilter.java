package com.ramesh.api_gateway.filter;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Edge rate limiting for the public API surface ({@code /api/**}) using the
 * provisioned Resilience4j {@code public-api} limiter. Runs before the
 * security chain so a flood of unauthenticated traffic is shed at the door
 * instead of burning JWT validation cycles. Actuator endpoints are exempt so
 * probes and Prometheus scrapes are never throttled.
 */
@Slf4j
@Component
@Order(-200) // before Spring Security's filter chain (order -100)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;

    public RateLimitFilter(RateLimiterRegistry rateLimiterRegistry) {
        this.rateLimiter = rateLimiterRegistry.rateLimiter("public-api");
        log.info("Public API rate limiter initialised | limitForPeriod: {} | refreshPeriod: {} | timeout: {}",
                rateLimiter.getRateLimiterConfig().getLimitForPeriod(),
                rateLimiter.getRateLimiterConfig().getLimitRefreshPeriod(),
                rateLimiter.getRateLimiterConfig().getTimeoutDuration());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (rateLimiter.acquirePermission()) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Request rate limited | path: {} | remote: {}",
                request.getRequestURI(), request.getRemoteAddr());

        response.setStatus(429);
        response.setHeader("Retry-After", "1");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"Rate limit exceeded, please retry later\"}");
    }
}