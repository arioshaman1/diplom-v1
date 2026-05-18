package com.start.getemployed.auth.service;

import com.start.getemployed.auth.repository.PasswordResetTokenRepository;
import com.start.getemployed.auth.repository.UserRepository;
import com.start.getemployed.entity.PasswordResetToken;
import com.start.getemployed.entity.User;
import com.start.getemployed.notification.service.EmailSenderService;
import com.start.getemployed.notification.service.VerificationTokenGen;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasswordResetTokenService {

  private final PasswordResetTokenRepository repository;
  private final VerificationTokenGen tokenGen;
  private final EmailSenderService emailSenderService;
  private final UserRepository userRepository;

  @Value("${app.frontend.reset-url-pattern}")
  private String resetUrlBase;

  public void createResetToken(String email) {

    User user =
        userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("USER NOT FOUND"));

    String rawToken = tokenGen.newRawToken();
    String hashedToken = tokenGen.hash(rawToken);

    PasswordResetToken token = new PasswordResetToken();
    token.setUserId(user.getId());
    token.setTokenHash(hashedToken);
    token.setExpiresAt(Instant.now().plusSeconds(30 * 60));

    repository.save(token);

    String resetUrl = resetUrlBase + "?token=" + rawToken;

    emailSenderService.sendHtml(
        email,
        "Сброс пароля",
        "password-reset",
        Map.of("name", user.getEmail(), "ttlMinutes", 30, "resetUrl", resetUrl));
  }
}
