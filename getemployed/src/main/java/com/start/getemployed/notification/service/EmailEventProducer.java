package com.start.getemployed.notification.service;

import com.start.getemployed.notification.kafka.EventEnvelope;
import com.start.getemployed.notification.kafka.EventType;
import com.start.getemployed.notification.kafka.PasswordResetEmailEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class EmailEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public EmailEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendPasswordReset(String email, String token) {

        EventEnvelope<PasswordResetEmailEvent> event =
                new EventEnvelope<>(
                        EventType.PASSWORD_RESET.name(),
                        new PasswordResetEmailEvent(email, token)
                );

        kafkaTemplate.send("notification.email", event);
    }
}