package com.ramesh.user_service.common;

/**
 * Inbound identity/audit headers injected by the API Gateway, plus the MDC keys
 * under which they are exposed to structured logging.
 *
 * <p>These names mirror the gateway's outbound contract. Once a shared module
 * exists they should be promoted there so producer and consumer share one source
 * of truth.
 */
public final class IdentityHeaders {

    private IdentityHeaders() {
    }

    // ---- HTTP headers (set by api-gateway) ----
    public static final String USER_NAME = "X-User-Name";
    public static final String USER_EMAIL = "X-User-Email";
    public static final String USER_ROLES = "X-User-Roles";
    public static final String ROLES_DELIMITER = ",";

    // ---- MDC keys (surfaced as JSON fields via logback <mdc/>) ----
    public static final String MDC_USERNAME = "username";
    public static final String MDC_EMAIL = "userEmail";
    public static final String MDC_ROLES = "roles";

    // ---- Kafka header keys (identity carried across the async boundary) ----
    // Reuse the same names as the HTTP contract so there is a single vocabulary.
    public static final String KAFKA_USER_NAME = USER_NAME;
    public static final String KAFKA_USER_EMAIL = USER_EMAIL;
    public static final String KAFKA_USER_ROLES = USER_ROLES;
}