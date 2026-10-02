package com.spc.fixedasset.auth;

import com.spc.fixedasset.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.time.Duration;

/** The logged-in account lives in the HttpSession under ACCOUNT. */
public final class AuthSession {

    public static final String ACCOUNT = "AUTH_ACCOUNT";
    public static final String USER = "AUTH_USER";
    public static final Duration TIMEOUT = Duration.ofHours(8);
    public static final String NOT_LOGGED_IN = "Chưa đăng nhập";

    private AuthSession() {}

    /** Account of the current session, or null. */
    public static String account(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        return s != null && s.getAttribute(ACCOUNT) instanceof String a ? a : null;
    }

    /** User of the current session; account only (HR fields null) when the session has no CurrentUser. */
    public static CurrentUser requireUser(HttpServletRequest request) {
        String a = requireAccount(request);
        Object u = request.getSession(false).getAttribute(USER);
        return u instanceof CurrentUser c && a.equals(c.account()) ? c : new CurrentUser(a, null, null, null);
    }

    public static String requireAccount(HttpServletRequest request) {
        String a = account(request);
        if (a == null) throw new UnauthorizedException(NOT_LOGGED_IN);
        return a;
    }
}
