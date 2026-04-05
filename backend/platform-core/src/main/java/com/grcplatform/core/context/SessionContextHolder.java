package com.grcplatform.core.context;

/**
 * Central holder for the request-scoped session context.
 *
 * Usage (in filter):
 * 
 * <pre>
 * ScopedValue.where(SessionContextHolder.SESSION, context)
 *         .run(() -> filterChain.doFilter(request, response));
 * </pre>
 *
 * Usage (in service):
 * 
 * <pre>
 * SessionContext ctx = SessionContextHolder.SESSION.get();
 * UUID orgId = ctx.orgId();
 * </pre>
 *
 * See ADR-008 — never use ThreadLocal for context propagation.
 */
@SuppressWarnings("preview")
public final class SessionContextHolder {

    public static final ScopedValue<SessionContext> SESSION = ScopedValue.newInstance();

    private SessionContextHolder() {}

    public static SessionContext current() {
        if (!SESSION.isBound()) {
            throw new IllegalStateException(
                    "SessionContext is not bound. Ensure SessionContextFilter is in the filter chain.");
        }
        return SESSION.get();
    }
}
