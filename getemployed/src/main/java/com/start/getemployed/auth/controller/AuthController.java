package com.start.getemployed.auth.controller;

import com.start.getemployed.AuthResult;
import com.start.getemployed.auth.LoginRequest;
import com.start.getemployed.auth.dto.AuthDto;
import com.start.getemployed.auth.dto.UserDto;
import com.start.getemployed.auth.jwt.TokenService;
import com.start.getemployed.auth.service.CookieService;
import com.start.getemployed.auth.service.RefreshTokenService;
import com.start.getemployed.entity.User;
import com.start.getemployed.notification.service.EmailSenderService;
import com.start.getemployed.auth.service.EmailVerificationService;
import com.start.getemployed.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthenticationManager authenticationManager;
  private final TokenService tokenService;
  private final RefreshTokenService refreshTokenService;
  private final EmailSenderService emailSenderService;
  private final HttpServletResponse httpServletResponse;
  private final AuthService authService;
  private final EmailVerificationService emailVerificationService;
  private final CookieService cookieService;

  public AuthController(
          AuthenticationManager authenticationManager,
          TokenService tokenService,
          EmailSenderService emailSenderService,
          RefreshTokenService refreshTokenService,
          HttpServletResponse httpServletResponse,
          AuthService authService,
          EmailVerificationService emailVerificationService,
          CookieService cookieService) {
    this.authenticationManager = authenticationManager;
    this.tokenService = tokenService;
    this.refreshTokenService = refreshTokenService;
    this.httpServletResponse = httpServletResponse;
    this.authService = authService;
    this.emailVerificationService = emailVerificationService;
    this.emailSenderService = emailSenderService;
    this.cookieService = cookieService;
  }

  @PostMapping("/login")
  public AuthDto.LoginResponse login(@RequestBody AuthDto.LoginRequest req, HttpServletResponse resp) {

    AuthResult result = authService.login(req);
    cookieService.setRefreshCookie(resp, result.refreshToken());

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    Set<String> roles = auth.getAuthorities()
            .stream()
            .map(a -> a.getAuthority())
            .collect(Collectors.toSet());

    return new AuthDto.LoginResponse(
            result.accessToken(),
            auth.getName(), ////email изза auth context holder
            auth.getName(),
            roles
    );

  }

  @PostMapping("/register")
  public Map<String, String> register(@RequestBody UserDto.CreateRequest req) {
    User user = authService.registerDisabled(req.getEmail(), req.getPassword(), req.getName());
    emailVerificationService.issueAndPublish(user);
    return Map.of("status", "verification_sent");
  }

  @PostMapping("/refresh")
  public Map<String, String> refresh(HttpServletRequest req, HttpServletResponse resp) {
    String oldRefresh = cookieService.readRefreshCookie(req);

    if (oldRefresh == null || oldRefresh.isBlank()) {
      throw new RuntimeException("No refresh token cookie found");
    }
    var rr = refreshTokenService.rotate(oldRefresh);
    String newAccess = tokenService.generateTokenFromSubjectAndRoles(rr.subject(), rr.roles());

    cookieService.setRefreshCookie(resp, rr.newRefreshToken());
    return Map.of("token", newAccess);
  }


  @PostMapping("/logout")
  public Map<String, String> logout(HttpServletRequest req, HttpServletResponse resp) {
    String refresh = cookieService.readRefreshCookie(req);

    if (refresh != null && !refresh.isBlank()) {
      refreshTokenService.revoke(refresh);
    }

    cookieService.clearRefreshCookie(resp);
    return Map.of("status", "ok");


  }
}