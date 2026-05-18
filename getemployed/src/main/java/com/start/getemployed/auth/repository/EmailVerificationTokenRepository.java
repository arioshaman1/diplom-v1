package com.start.getemployed.auth.repository;

import com.start.getemployed.entity.EmailVerificationToken;
import com.start.getemployed.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailVerificationTokenRepository
    extends JpaRepository<EmailVerificationToken, Long> {
  Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

  List<EmailVerificationToken> findAllByUserAndUsedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(
      User user, Instant now);
}
