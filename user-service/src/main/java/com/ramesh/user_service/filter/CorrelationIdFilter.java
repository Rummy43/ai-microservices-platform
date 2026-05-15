package com.ramesh.user_service.filter;

import com.ramesh.user_service.common.CorrelationConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String correlationId = request.getHeader(CorrelationConstants.CORRELATION_ID_HEADER);

        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }

        try {
            MDC.put(CorrelationConstants.TRACE_ID, correlationId);
            response.setHeader(CorrelationConstants.CORRELATION_ID_HEADER, correlationId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationConstants.TRACE_ID);
        }
    }
}