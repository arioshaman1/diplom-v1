package com.start.getemployed.notification.kafka;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailEventProducer {

    private final KafkaTemplate<String, EventEnvelope<?>> kafkaTemplate;

    @Value("${app.kafka.topics.email-event}")
    private String emailTopic;

    @Value("${app.kafka.topics.password-reset-event}")
    private String passwordResetTopic;

    public void sendVerifyEmail(SendVerifyEmailEvent event) {

        EventEnvelope<SendVerifyEmailEvent> envelope =
                new EventEnvelope<>(EventType.SEND_VERIFY_EMAIL.name(), event);

        kafkaTemplate.send(emailTopic, event.email(), envelope);
    }

    public void sendPasswordResetEmail(PasswordResetEmailEvent event) {

        EventEnvelope<PasswordResetEmailEvent> envelope =
                new EventEnvelope<>(EventType.PASSWORD_RESET_EMAIL.name(), event);

        kafkaTemplate.send(passwordResetTopic, event.email(), envelope);
    }
}