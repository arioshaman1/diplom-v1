package com.start.getemployed.auth.service;

import com.start.getemployed.auth.repository.PasswordResetTokenRepository;
import com.start.getemployed.auth.repository.UserRepository;
import com.start.getemployed.entity.PasswordResetToken;
import com.start.getemployed.entity.User;
import com.start.getemployed.notification.kafka.EventEnvelope;
import com.start.getemployed.notification.kafka.EventType;
import com.start.getemployed.notification.kafka.events.PasswordResetEmailEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final PasswordEncoder passwordEncoder;
  private final UserRepository userRepository;

  public PasswordResetToken createToken(Long userId) {
    PasswordResetToken t = new PasswordResetToken();
    t.setToken(UUID.randomUUID().toString());
    t.setUserId(userId);
    t.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));

    return passwordResetTokenRepository.save(t);
  }

  public PasswordResetToken validate(String token) {

    PasswordResetToken t =
        passwordResetTokenRepository
            .findByToken(token)
            .orElseThrow(() -> new RuntimeException("INVALID TOKEN"));

    if (t.getUsedAt() != null) {
      throw new RuntimeException("TOKEN ALREADY USED");
    }
    if (t.getExpiresAt().isBefore(Instant.now())) {
      throw new RuntimeException("token EXPIRED");
    }
    return t;
  }

  public void createAndSend(User user) {

    PasswordResetToken token = createToken(user.getId());

    PasswordResetEmailEvent event =
        new PasswordResetEmailEvent(user.getEmail(), user.getName(), token.getToken(), 60);

    EventEnvelope<PasswordResetEmailEvent> envelope =
        new EventEnvelope<>(EventType.PASSWORD_RESET_EMAIL.name(), event);

    kafkaTemplate.send("password-reset-event", envelope);
  }

  public void markUsed(PasswordResetToken token) {
    token.setUsedAt(Instant.now());
    passwordResetTokenRepository.save(token);
  }

  public void resetPassword(String rawToken, String newPassword) {

    if (newPassword == null || newPassword.isBlank()) {
      throw new RuntimeException("new password is empty");
    }

    PasswordResetToken token =
        passwordResetTokenRepository
            .findByToken(rawToken)
            .orElseThrow(() -> new RuntimeException("Invalid Token"));

    if (token.getUsedAt() != null) {
      throw new RuntimeException("TOKEN ALREADY USED");
    }

    if (token.getExpiresAt().isBefore(Instant.now())) {
      throw new RuntimeException("reset token EXPIRED");
    }

    User user =
        userRepository
            .findById(token.getUserId())
            .orElseThrow(() -> new RuntimeException("USER NOT FOUND"));

    String encodedPassword = passwordEncoder.encode(newPassword);

    user.setPasswordHash(encodedPassword);
    userRepository.save(user);

    token.setUsedAt(Instant.now());
    passwordResetTokenRepository.save(token);
  }
}
