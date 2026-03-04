package com.start.getemployed.service;

import com.start.getemployed.VerificationTokenGen;
import com.start.getemployed.entity.EmailVerificationToken;
import com.start.getemployed.entity.User;
import com.start.getemployed.kafka.EmailEventProducer;
import com.start.getemployed.kafka.SendVerifyEmailEvent;
import com.start.getemployed.repository.EmailVerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;


@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepo;
    private final EmailEventProducer producer;
    private final VerificationTokenGen codec;

    @Value("${app.security.email-token-ttl-minutes}")
    private long ttlMinutes;

    @Transactional
    public void issueAndPublish(User user) {
        String raw = codec.newRawToken();

        EmailVerificationToken t = new EmailVerificationToken();
        t.setUser(user);
        t.setTokenHash(codec.hash(raw));
        t.setCreatedAt(Instant.now());
        t.setExpiresAt(Instant.now().plus(Duration.ofMinutes(ttlMinutes)));
        tokenRepo.save(t);

        producer.sendVerifyEmail(new SendVerifyEmailEvent(
                user.getId(),
                user.getEmail(),
                user.getName(),
                raw,
                ttlMinutes
        ));
    }

    @Transactional
    public void verify(String rawToken) {
        String hash = codec.hash(rawToken);

        EmailVerificationToken t = tokenRepo.findByTokenHash(hash)
                .orElseThrow(() -> new RuntimeException("Invalid verification token"));

        if (t.getUsedAt() != null) throw new RuntimeException("Token already used");
        if (t.getExpiresAt().isBefore(Instant.now())) throw new RuntimeException("Token expired");

        User u = t.getUser();
        u.setEnabled(true);
        t.setUsedAt(Instant.now());
    }
}