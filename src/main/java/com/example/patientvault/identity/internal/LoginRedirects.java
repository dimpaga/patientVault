package com.example.patientvault.identity.internal;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Redirects to the login page; htmx requests get a 401 with {@code HX-Redirect} so the whole page is replaced. */
final class LoginRedirects {

    private LoginRedirects() {
    }

    static boolean isHtmx(HttpServletRequest request) {
        return "true".equalsIgnoreCase(request.getHeader("HX-Request"));
    }

    /** True when the browser sent a session cookie that the server no longer knows (idle/absolute timeout). */
    static boolean sessionExpired(HttpServletRequest request) {
        return request.getRequestedSessionId() != null && !request.isRequestedSessionIdValid();
    }

    static void toLogin(HttpServletRequest request, HttpServletResponse response, boolean expired) throws IOException {
        String url = request.getContextPath() + "/login" + (expired ? "?expired" : "");
        if (isHtmx(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("HX-Redirect", url);
        } else {
            response.sendRedirect(url);
        }
    }
}
