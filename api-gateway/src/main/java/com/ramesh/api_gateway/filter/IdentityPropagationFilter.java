package com.ramesh.api_gateway.filter;

import com.ramesh.api_gateway.constants.IdentityHeaders;
import com.ramesh.api_gateway.filter.support.MutableHttpServletRequestWrapper;
import com.ramesh.api_gateway.identity.IdentityContext;
import com.ramesh.api_gateway.identity.IdentityContextExtractor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Propagates the authenticated caller's identity and audit context to downstream
 * services as HTTP headers ({@code X-User-Name}, {@code X-User-Email},
 * {@code X-User-Roles}).
 *
 * <p>Runs after Spring Security (which authenticates the request) and after
 * {@link CorrelationIdFilter} (so the correlation id is available for logging).
 * It reads the already-validated {@link Jwt} from the security context — it does
 * not re-validate the token.
 *
 * <p>The three identity headers are always overwritten, even when a claim is
 * absent, so a client cannot spoof its own identity by setting these headers.
 */
@Component
@Slf4j
@Order(2)
@RequiredArgsConstructor
public class IdentityPropagationFilter extends OncePerRequestFilter {

    private final IdentityContextExtractor identityContextExtractor;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        Jwt jwt = resolveJwt();

        if (jwt == null) {
            // Public/unauthenticated route (e.g. /actuator/**) — nothing to propagate.
            filterChain.doFilter(request, response);
            return;
        }

        IdentityContext identity = identityContextExtractor.extract(jwt);
        String roles = identity.rolesAsString(IdentityHeaders.ROLES_DELIMITER);

        MutableHttpServletRequestWrapper enrichedRequest =
                new MutableHttpServletRequestWrapper(request);

        // Always set all three headers (empty when absent) to mask any spoofed values.
        enrichedRequest.putHeader(IdentityHeaders.USER_NAME, nullToEmpty(identity.username()));
        enrichedRequest.putHeader(IdentityHeaders.USER_EMAIL, nullToEmpty(identity.email()));
        enrichedRequest.putHeader(IdentityHeaders.USER_ROLES, roles);

        log.info("Identity propagated downstream | username: {} | email: {} | roles: {} | correlationId: {}",
                identity.username(),
                identity.email(),
                roles,
                MDC.get(CorrelationIdFilter.TRACE_ID));

        filterChain.doFilter(enrichedRequest, response);
    }

    private Jwt resolveJwt() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken();
        }
        return null;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}