package com.morrow.wallet.auth;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import com.morrow.wallet.api.ApiException;

public record SessionUser(long userId, String fullName, String email, String role) {
    public static final String USER_ID = "MORROW_AUTH_USER_ID";
    public static final String FULL_NAME = "MORROW_AUTH_FULL_NAME";
    public static final String EMAIL = "MORROW_AUTH_EMAIL";
    public static final String ROLE = "MORROW_AUTH_ROLE";

    public static SessionUser require(HttpSession session) {
        if (session == null || session.getAttribute(USER_ID) == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in to continue.");
        }
        Object role = session.getAttribute(ROLE);
        return new SessionUser(
                ((Number) session.getAttribute(USER_ID)).longValue(),
                String.valueOf(session.getAttribute(FULL_NAME)),
                String.valueOf(session.getAttribute(EMAIL)),
                role == null ? "USER" : String.valueOf(role));
    }

    public void store(HttpSession session) {
        session.setAttribute(USER_ID, userId);
        session.setAttribute(FULL_NAME, fullName);
        session.setAttribute(EMAIL, email);
        session.setAttribute(ROLE, role);
    }

    public void requireAdmin() {
        if (!"ADMIN".equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Administrator access is required.");
        }
    }
}
