package com.ramesh.user_service.identity;

import java.util.Optional;

/**
 * Thread-bound access to the current request's {@link IdentityContext}.
 *
 * <p>Lets business and audit code obtain "who is acting" without threading the
 * {@code HttpServletRequest} through every layer (e.g. stamping the outbox /
 * audit columns). Populated and cleared by the inbound identity filter.
 *
 * <p>Each request runs on its own (virtual) thread, so a {@link ThreadLocal} is
 * the correct scope. The filter MUST clear it in a {@code finally} block to
 * avoid leaking context across pooled or reused threads.
 */
public final class IdentityContextHolder {

    private static final ThreadLocal<IdentityContext> CONTEXT = new ThreadLocal<>();

    private IdentityContextHolder() {
    }

    public static void set(IdentityContext context) {
        CONTEXT.set(context);
    }

    public static Optional<IdentityContext> get() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static void clear() {
        CONTEXT.remove();
    }
}