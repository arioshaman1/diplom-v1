package com.start.getemployed.notification.email;

import com.start.getemployed.entity.EmailVerificationToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationTokenRepository
    extends JpaRepository<EmailVerificationToken, Long> {
  Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

  String tokenHash(String token);
}
