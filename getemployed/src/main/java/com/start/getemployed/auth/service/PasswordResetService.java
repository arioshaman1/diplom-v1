package com.start.getemployed.auth.service;


import com.start.getemployed.auth.repository.PasswordResetTokenRepository;
import com.start.getemployed.entity.PasswordResetToken;
import com.start.getemployed.entity.User;
import com.start.getemployed.notification.kafka.events.PasswordResetEmailEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final KafkaTemplate<String, PasswordResetEmailEvent> kafkaTemplate;
    public PasswordResetToken createToken(Long userId) {
        PasswordResetToken t = new PasswordResetToken();
        t.setToken(UUID.randomUUID().toString());
        t.setUserId(userId);
        t.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));

        return passwordResetTokenRepository.save(t);

    }

    public PasswordResetToken validate(String token) {

        PasswordResetToken t = passwordResetTokenRepository.findByToken(token)
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

        PasswordResetEmailEvent event = new PasswordResetEmailEvent(
                user.getEmail(),
                user.getName(),
                token.getToken(),
                60

        );

        kafkaTemplate.send("password-reset-event", event);
    }

    public void markUsed(PasswordResetToken token) {
        token.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(token);
    }

}
