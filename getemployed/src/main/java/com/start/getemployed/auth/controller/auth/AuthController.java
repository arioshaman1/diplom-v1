package com.start.getemployed.auth.controller.auth;

import com.start.getemployed.AuthResult;
import com.start.getemployed.auth.ApiResponse;
import com.start.getemployed.auth.dto.AuthDto;
import com.start.getemployed.auth.dto.UserDto;
import com.start.getemployed.auth.jwt.TokenService;
import com.start.getemployed.auth.service.AuthService;
import com.start.getemployed.auth.service.CookieService;
import com.start.getemployed.auth.service.RateLimitService;
import com.start.getemployed.auth.service.RefreshTokenService;
import com.start.getemployed.entity.User;
import com.start.getemployed.notification.service.EmailSenderService;
import com.start.getemployed.notification.service.EmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
  private final RateLimitService rateLimitService;

  public AuthController(
      AuthenticationManager authenticationManager,
      TokenService tokenService,
      EmailSenderService emailSenderService,
      RefreshTokenService refreshTokenService,
      HttpServletResponse httpServletResponse,
      AuthService authService,
      EmailVerificationService emailVerificationService,
      CookieService cookieService,
      RateLimitService rateLimitService) {
    this.authenticationManager = authenticationManager;
    this.tokenService = tokenService;
    this.refreshTokenService = refreshTokenService;
    this.httpServletResponse = httpServletResponse;
    this.authService = authService;
    this.emailVerificationService = emailVerificationService;
    this.emailSenderService = emailSenderService;
    this.cookieService = cookieService;
    this.rateLimitService = rateLimitService;
  }

  @PostMapping("/login")
  public ApiResponse<AuthDto.LoginResponse> login(
      @RequestBody AuthDto.LoginRequest req, HttpServletResponse resp) {
    AuthResult result = authService.login(req);
    cookieService.setRefreshCookie(resp, result.refreshToken());

    User user = authService.findByEmail(req.getEmail());

    AuthDto.UserDto userDto =
        new AuthDto.UserDto(user.getId().toString(), user.getEmail(), user.getName());
    AuthDto.LoginResponse response = new AuthDto.LoginResponse(result.accessToken(), userDto);

    return new ApiResponse<>(response);
  }

  @PostMapping("/register")
  public Map<String, String> register(@RequestBody UserDto.CreateRequest req) {
    rateLimitService.check("register:" + req.getEmail());
    User user = authService.registerDisabled(req.getEmail(), req.getPassword(), req.getName());
    emailVerificationService.issueAndPublish(user);
    return Map.of("status", "verification_sent");
  }

  @PostMapping("/refresh")
  public ApiResponse<AuthDto.RefreshResponse> refresh(
      HttpServletRequest req, HttpServletResponse resp) {
    String oldRefresh = cookieService.readRefreshCookie(req);

    if (oldRefresh == null || oldRefresh.isBlank()) {
      throw new RuntimeException("No refresh token found");
    }

    var rr = refreshTokenService.rotate(oldRefresh);

    String newAccess = tokenService.generateTokenFromSubjectAndRoles(rr.subject(), rr.roles());

    cookieService.setRefreshCookie(resp, rr.newRefreshToken());

    AuthDto.RefreshResponse response = new AuthDto.RefreshResponse(newAccess);

    return new ApiResponse<>(response);
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(HttpServletRequest req, HttpServletResponse resp) {
    String refresh = cookieService.readRefreshCookie(req);

    if (refresh != null && !refresh.isBlank()) {
      refreshTokenService.revoke(refresh);
    }

    cookieService.clearRefreshCookie(resp);
  }
}
