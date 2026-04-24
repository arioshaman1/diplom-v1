package com.start.getemployed.auth.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;


@Service
@RequiredArgsConstructor
public class CookieService {

    private static final boolean COOKIE_SECURE = false;
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/v1";
    private static final Duration REFRESH_TTL = Duration.ofDays(30);

    public void setRefreshCookie(HttpServletResponse resp, String refreshToken) {
        ResponseCookie cookie =
                ResponseCookie.from(REFRESH_COOKIE, refreshToken)
                        .httpOnly(true)
                        .secure(COOKIE_SECURE)
                        .path(REFRESH_COOKIE_PATH)
                        .maxAge(REFRESH_TTL)
                        .sameSite("Lax")
                        .build();

        resp.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public String readRefreshCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;

        for (Cookie c : cookies) {
            if (REFRESH_COOKIE.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    public void clearRefreshCookie(HttpServletResponse resp) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(COOKIE_SECURE)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .sameSite("Lax")
                .build();

        resp.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

}
