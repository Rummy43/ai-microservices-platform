package com.ramesh.user_service.identity;

import com.ramesh.user_service.common.IdentityHeaders;

import java.util.Arrays;
import java.util.List;

/**
 * Immutable view of the authenticated caller, reconstructed from the gateway's
 * identity headers.
 *
 * <p>Transport-agnostic on purpose: the same shape can later be rehydrated from
 * Kafka headers so asynchronous consumers carry the originating user's audit
 * context.
 */
public record IdentityContext(
        String username,
        String email,
        List<String> roles
) {

    public IdentityContext {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    /**
     * Rebuilds a context from a delimited roles string, e.g. when restoring it
     * from a persisted outbox row or an inbound Kafka header.
     */
    public static IdentityContext fromCsvRoles(String username, String email, String csvRoles) {
        List<String> roles = (csvRoles == null || csvRoles.isBlank())
                ? List.of()
                : Arrays.stream(csvRoles.split(IdentityHeaders.ROLES_DELIMITER))
                        .map(String::trim)
                        .filter(role -> !role.isEmpty())
                        .toList();
        return new IdentityContext(username, email, roles);
    }

    /** True when the request carried a propagated identity (i.e. came through the gateway). */
    public boolean isAuthenticated() {
        return username != null && !username.isBlank();
    }

    public String rolesAsString() {
        return String.join(IdentityHeaders.ROLES_DELIMITER, roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}