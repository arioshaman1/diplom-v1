package com.start.getemployed.auth.repository;

import com.start.getemployed.entity.EmailVerificationToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.start.getemployed.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationTokenRepository
    extends JpaRepository<EmailVerificationToken, Long> {
  Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
  List<EmailVerificationToken> findAllByUserAndUsedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(
          User user, Instant now);
}
