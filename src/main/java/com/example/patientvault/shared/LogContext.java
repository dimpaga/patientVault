package com.example.patientvault.shared;

import java.util.UUID;

import org.slf4j.MDC;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Names of the per-request logging context. Everything here is an identifier or technical value: log
 * lines must never carry personal data, so only ids (UUIDs), the client address and route templates go in.
 */
public final class LogContext {

    public static final String REQUEST_ID = "requestId";
    public static final String USER_ID = "userId";
    public static final String REMOTE_IP = "remoteIp";
    public static final String ACTION = "action";
    public static final String MODULE = "module";

    /** Request attributes read by the access log and the error pages. */
    public static final String REQUEST_ID_ATTRIBUTE = "requestId";
    public static final String USER_ID_ATTRIBUTE = "pv.userId";

    private LogContext() {
    }

    /** Marks the current request as belonging to a user (id only) so logs and the access line carry it. */
    public static void userAuthenticated(HttpServletRequest request, UUID userId) {
        request.setAttribute(USER_ID_ATTRIBUTE, userId.toString());
        MDC.put(USER_ID, userId.toString());
    }
}
