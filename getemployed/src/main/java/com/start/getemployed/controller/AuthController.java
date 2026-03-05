package com.start.getemployed.controller;

import com.start.getemployed.auth.LoginRequest;
import com.start.getemployed.dto.UserDto;
import com.start.getemployed.entity.User;
import com.start.getemployed.jwt.TokenService;
import com.start.getemployed.service.EmailSenderService;
import com.start.getemployed.service.RefreshTokenService;
import com.start.getemployed.service.UserService;
import com.start.getemployed.service.EmailVerificationService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Map;


@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final EmailSenderService emailSenderService;

    private static final boolean COOKIE_SECURE = false;

    private static final String REFRESH_COOKIE =  "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";
    private static final Duration REFRESH_TTL = Duration.ofDays(30);
    private final HttpServletResponse httpServletResponse;
    private final UserService userService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(AuthenticationManager authenticationManager,
                          TokenService tokenService,
                          EmailSenderService emailSenderService,
                          RefreshTokenService refreshTokenService, HttpServletResponse httpServletResponse, UserService userService, EmailVerificationService emailVerificationService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.refreshTokenService = refreshTokenService;
        this.httpServletResponse = httpServletResponse;
        this.userService = userService;
        this.emailVerificationService = emailVerificationService;
        this.emailSenderService = emailSenderService;

    }

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody LoginRequest req,
                                     HttpServletRequest httpReq,
                                     HttpServletResponse httpResp) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password())
        );
        String accessToken = tokenService.generateToken(auth);
        String refreshToken = refreshTokenService.issue(auth.getName());

        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(COOKIE_SECURE)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(REFRESH_TTL)
                .sameSite("Lax")
                .build();
        httpResp.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return Map.of("token", accessToken);
    }
    @PostMapping("/register")
    public Map<String, String> register(@RequestBody UserDto.CreateRequest req) {
        User user = userService.registerDisabled(req.getEmail(), req.getPassword(), req.getName());
        emailVerificationService.issueAndPublish(user);
        return Map.of("status", "verification_sent");
    }

    @PostMapping("/refresh")
    public Map<String, String> refresh(HttpServletRequest httpReq,
                                       HttpServletResponse httpResp) {
        String oldRefresh = readCookie(httpReq, REFRESH_COOKIE);
        if (oldRefresh == null || oldRefresh.isBlank()) {
            throw new RuntimeException("No refresh token cookie found");
        }
        RefreshTokenService.RefreshResult rr = refreshTokenService.rotate(oldRefresh);
        String newAccess = tokenService.generateTokenFromSubjectAndRoles(rr.subject(), rr.roles());

        setRefreshCookie(httpResp, rr.newRefreshToken());
        return Map.of("token", newAccess);
    }
    @PostMapping("/send-email")
    public String send(@RequestParam String to) {
        emailSenderService.sendHtml(
                to,
                "GetEmployed — завершите регистрацию ✅",
                "mail/verify",
                Map.of(
                        "subject", "GetEmployed — тестовое письмо ✅",
                        "appName", "GetEmployed",
                        "name", "User",
                        "sentAt", ZonedDateTime.now().toString(),
                        "env", "local",
                        "actionUrl", "http://localhost:8080"
                )
        );
        return "sent";
    }

    @PostMapping("/verify")
    public ResponseEntity<Void> verify(@RequestParam("token") String token) {
        emailVerificationService.verify(token);
        return ResponseEntity.ok().build();
    }


    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest httpReq,
                                      HttpServletResponse httpResp) {
        String refresh = readCookie(httpReq, REFRESH_COOKIE);
        if (refresh == null || refresh.isBlank()) {
            refreshTokenService.revoke(refresh);
        }
        clearRefreshCookie(httpResp);
        return Map.of("status", "ok");
    }

    private static String readCookie(HttpServletRequest req, String name) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) {return c.getValue();}
        }
        return null;
    }

    private void setRefreshCookie(HttpServletResponse resp, String refreshToken) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(COOKIE_SECURE);
        cookie.setPath(REFRESH_COOKIE_PATH);
        cookie.setMaxAge((int) REFRESH_TTL.getSeconds());
        resp.addCookie(cookie);
    }
    private void clearRefreshCookie(HttpServletResponse resp) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(COOKIE_SECURE);
        cookie.setPath(REFRESH_COOKIE_PATH);
        cookie.setMaxAge(0);
        resp.addCookie(cookie);
    }
}

