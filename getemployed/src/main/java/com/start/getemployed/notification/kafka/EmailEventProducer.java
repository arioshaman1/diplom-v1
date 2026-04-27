package com.start.getemployed.notification.kafka;

import com.start.getemployed.notification.kafka.events.PasswordResetEmailEvent;
import com.start.getemployed.notification.kafka.events.SendVerifyEmailEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topics.email-event}")
    private String emailTopic;

    @Value("${spring.kafka.topics.password-reset-event}")
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