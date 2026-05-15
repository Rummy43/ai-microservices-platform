package com.ramesh.user_service.common;

public final class CorrelationConstants {

    private CorrelationConstants() {
    }

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String TRACE_ID = "traceId";
    public static final String KAFKA_TRACE_ID_HEADER = "traceId";
}