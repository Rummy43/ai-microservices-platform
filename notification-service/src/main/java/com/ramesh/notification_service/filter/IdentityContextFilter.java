package com.ramesh.notification_service.filter;

import com.ramesh.notification_service.common.CorrelationConstants;
import com.ramesh.notification_service.common.IdentityHeaders;
import com.ramesh.notification_service.identity.IdentityContext;
import com.ramesh.notification_service.identity.IdentityContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Reconstructs the caller's identity from the gateway-injected headers
 * ({@code X-User-Name}, {@code X-User-Email}, {@code X-User-Roles}) on every
 * inbound HTTP request.
 *
 * <p>It exposes the identity two ways:
 * <ul>
 *   <li>via {@link IdentityContextHolder} for programmatic access (audit);</li>
 *   <li>via MDC so every log line in the request carries the user fields (the
 *       logback {@code <mdc/>} provider emits them as JSON).</li>
 * </ul>
 *
 * <p>Both the holder and MDC entries are cleared in {@code finally} to prevent
 * cross-request leakage. Note: notification-service has no HTTP correlation
 * filter (its {@code traceId} arrives via Kafka headers), so {@code correlationId}
 * may be absent for direct HTTP calls.
 */
@Component
@Slf4j
@Order(2)
public class IdentityContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        IdentityContext identity = new IdentityContext(
                trimToNull(request.getHeader(IdentityHeaders.USER_NAME)),
                trimToNull(request.getHeader(IdentityHeaders.USER_EMAIL)),
                parseRoles(request.getHeader(IdentityHeaders.USER_ROLES))
        );

        try {
            IdentityContextHolder.set(identity);
            populateMdc(identity);

            if (identity.isAuthenticated()) {
                log.info("Inbound request identity | username: {} | email: {} | roles: {} | correlationId: {}",
                        identity.username(),
                        identity.email(),
                        identity.rolesAsString(),
                        MDC.get(CorrelationConstants.TRACE_ID));
            }

            filterChain.doFilter(request, response);
        } finally {
            clearMdc();
            IdentityContextHolder.clear();
        }
    }

    private void populateMdc(IdentityContext identity) {
        if (identity.username() != null) {
            MDC.put(IdentityHeaders.MDC_USERNAME, identity.username());
        }
        if (identity.email() != null) {
            MDC.put(IdentityHeaders.MDC_EMAIL, identity.email());
        }
        if (!identity.roles().isEmpty()) {
            MDC.put(IdentityHeaders.MDC_ROLES, identity.rolesAsString());
        }
    }

    private void clearMdc() {
        MDC.remove(IdentityHeaders.MDC_USERNAME);
        MDC.remove(IdentityHeaders.MDC_EMAIL);
        MDC.remove(IdentityHeaders.MDC_ROLES);
    }

    private List<String> parseRoles(String rolesHeader) {
        if (!StringUtils.hasText(rolesHeader)) {
            return List.of();
        }
        return Arrays.stream(rolesHeader.split(IdentityHeaders.ROLES_DELIMITER))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}