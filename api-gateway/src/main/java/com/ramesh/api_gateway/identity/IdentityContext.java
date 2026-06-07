package com.ramesh.api_gateway.identity;

import java.util.List;

/**
 * Immutable snapshot of the authenticated caller's identity and audit context.
 *
 * <p>This is the transport-agnostic representation of "who is making this call".
 * It is currently serialised into HTTP headers by the gateway, but it is
 * deliberately decoupled from HTTP so the same context can later be propagated
 * through Kafka headers without re-deriving it from the JWT.
 */
public record IdentityContext(
        String username,
        String email,
        List<String> roles
) {

    public IdentityContext {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    /** Serialise roles into a single delimited header value. */
    public String rolesAsString(String delimiter) {
        return String.join(delimiter, roles);
    }
}