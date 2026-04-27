package com.start.getemployed.notification.kafka;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.start.getemployed.notification.kafka.events.PasswordResetEmailEvent;
import com.start.getemployed.notification.kafka.events.SendVerifyEmailEvent;
import com.start.getemployed.notification.service.EmailSenderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class EmailEventConsumer {

    private final ObjectMapper objectMapper;
    private final EmailSenderService emailSenderService;

    @Value("${app.frontend.verify-url-pattern}")
    private String verifyUrlPattern;

    @Value("${app.frontend.reset-url-pattern}")
    private String resetUrlPattern;

    @KafkaListener(topics = {"email-event", "password-reset-event"})
    public void consume(String message) throws Exception {

        JavaType type = objectMapper.getTypeFactory()
                .constructParametricType(EventEnvelope.class, Object.class);

        EventEnvelope<?> envelope = objectMapper.readValue(message, type);

        switch (envelope.type()) {

            case "SEND_VERIFY_EMAIL" -> handleVerify(envelope);

            case "PASSWORD_RESET_EMAIL" -> handleReset(envelope);

            default -> throw new IllegalStateException("Unknown type: " + envelope.type());
        }
    }

    private void handleVerify(EventEnvelope<?> envelope) {

        SendVerifyEmailEvent event =
                objectMapper.convertValue(envelope.payload(), SendVerifyEmailEvent.class);

        String link = String.format(verifyUrlPattern, event.rawToken());

        emailSenderService.sendHtml(
                event.email(),
                "Подтверждение почты",
                "mail/verify",
                Map.of(
                        "name", event.name(),
                        "actionUrl", link,
                        "ttlMinutes", event.ttlMinutes()
                )
        );
    }

    private void handleReset(EventEnvelope<?> envelope) {

        PasswordResetEmailEvent event =
                objectMapper.convertValue(envelope.payload(), PasswordResetEmailEvent.class);

        String link = String.format(resetUrlPattern, event.resetToken());

        emailSenderService.sendHtml(
                event.email(),
                "Сброс пароля",
                "mail/password-reset",
                Map.of(
                        "name", event.name(),
                        "actionUrl", link,
                        "ttlMinutes", event.ttlMinutes()
                )
        );
    }
}
