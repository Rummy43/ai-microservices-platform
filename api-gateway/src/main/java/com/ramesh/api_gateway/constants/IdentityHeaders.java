package com.ramesh.api_gateway.constants;

/**
 * Canonical header names used to propagate the authenticated identity and audit
 * context from the API Gateway to downstream services.
 *
 * <p>Centralising these names prevents magic strings from leaking across the
 * gateway and downstream modules and gives us a single contract that can later
 * be reused for Kafka header propagation.
 */
public final class IdentityHeaders {

    private IdentityHeaders() {
        throw new AssertionError("Constants class must not be instantiated");
    }

    /** Keycloak {@code preferred_username}. */
    public static final String USER_NAME = "X-User-Name";

    /** Keycloak {@code email}. */
    public static final String USER_EMAIL = "X-User-Email";

    /** Comma-separated realm roles extracted from {@code realm_access.roles}. */
    public static final String USER_ROLES = "X-User-Roles";

    /** Delimiter used when serialising the role collection into a single header value. */
    public static final String ROLES_DELIMITER = ",";
}