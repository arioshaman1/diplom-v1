package com.start.getemployed.auth.service;

import com.start.getemployed.entity.EmailVerificationToken;
import com.start.getemployed.entity.User;
import com.start.getemployed.auth.repository.EmailVerificationTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.start.getemployed.notification.kafka.EmailEventProducer;
import com.start.getemployed.notification.kafka.events.SendVerifyEmailEvent;
import com.start.getemployed.notification.service.VerificationTokenGen;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

  private final EmailVerificationTokenRepository tokenRepo;
  private final VerificationTokenGen codec;
  private final EmailEventProducer producer;

  @Value("${app.security.email-token-ttl-minutes}")
  private long ttlMinutes;

  @Transactional
  public void issueAndPublish(User user) {
    String raw = codec.newRawToken();
    Instant now = Instant.now();

    EmailVerificationToken t = new EmailVerificationToken();
    t.setUser(user);
    t.setTokenHash(codec.hash(raw));
    t.setCreatedAt(Instant.now());
    t.setExpiresAt(now.plus(Duration.ofMinutes(ttlMinutes)));
    t.setUsedAt(null);

    tokenRepo.save(t);

    producer.sendVerifyEmail(
        new SendVerifyEmailEvent(user.getEmail(), user.getName(), raw, ttlMinutes));
  }


  @Transactional
  public void resendVerification(User user) {
    if (user.isEnabled()) {
      throw new IllegalArgumentException("account already verified");
    }

    revokeActiveTokens(user);
    issueAndPublish(user);
  }

  @Transactional
  public void verify(String rawToken) {
    String hash = codec.hash(rawToken);

    EmailVerificationToken t =
        tokenRepo
            .findByTokenHash(hash)
            .orElseThrow(() -> new RuntimeException("Invalid verification token"));

    if (t.getUsedAt() != null) throw new RuntimeException("Token already used");
    if (t.getRevokedAt() != null) throw new RuntimeException("Token revoked");
    if (t.getExpiresAt().isBefore(Instant.now())) throw new RuntimeException("Token expired");

    User u = t.getUser();
    u.setEnabled(true);
    t.setUsedAt(Instant.now());
    revokeOtherActiveTokens(u, t);
  }

  private void revokeActiveTokens(User user) {
    List<EmailVerificationToken> activeTokens =
            tokenRepo.findAllByUserAndUsedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(user, Instant.now());

    Instant now = Instant.now();
    for (EmailVerificationToken token : activeTokens) {
      token.setRevokedAt(now);
    }
  }

  private void revokeOtherActiveTokens(User u, EmailVerificationToken currentToken) {
    List<EmailVerificationToken> activeTokens =
            tokenRepo.findAllByUserAndUsedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(u, Instant.now());

    Instant now = Instant.now();
    for (EmailVerificationToken token : activeTokens) {
      if(!token.getId().equals(currentToken.getId())) {
        token.setUsedAt(now);
      }
    }
  }
}
