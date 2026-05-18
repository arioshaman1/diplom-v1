package com.start.getemployed.auth.controller.auth;

import com.start.getemployed.auth.service.AuthService;
import com.start.getemployed.entity.User;
import com.start.getemployed.notification.service.EmailSenderService;
import com.start.getemployed.notification.service.EmailVerificationService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping("/api/v1/")
public class VerificationController {
  private final EmailSenderService emailSenderService;
  private final EmailVerificationService emailVerificationService;
  private final AuthService authService;

  public VerificationController(
      EmailVerificationService emailVerificationService,
      EmailSenderService emailSenderService,
      AuthService authService) {
    this.emailVerificationService = emailVerificationService;
    this.emailSenderService = emailSenderService;
    this.authService = authService;
  }

  @Value("${app.frontend.home-page-url}")
  private String homePageUrl;

  @Value("${app.frontend.verify-failed-url}")
  private String verifyFailedUrl;

  @PostMapping("/resend-verification")
  public Map<String, String> resendEmailVerification(@RequestParam String email) {
    User user = authService.findByEmailOrNull(email);

    if (user != null && !user.isEnabled()) {
      emailVerificationService.resendVerification(user);
    }
    return Map.of("status", "if_exists_sent");
  }

  @PostMapping("/verify")
  public RedirectView verify(@RequestParam("token") String token) {
    return verifyToken(token);
  }

  @GetMapping("/verify")
  public RedirectView verifyFromEmail(@RequestParam("token") String token) {
    return verifyToken(token);
  }

  private RedirectView verifyToken(String token) {
    try {
      emailVerificationService.verify(token);
      return new RedirectView(homePageUrl);
    } catch (Exception e) {
      return new RedirectView(verifyFailedUrl);
    }
  }
}
