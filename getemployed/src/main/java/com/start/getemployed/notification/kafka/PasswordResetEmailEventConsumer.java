package com.start.getemployed.notification.kafka;

import com.start.getemployed.auth.service.PasswordResetService;
import com.start.getemployed.notification.service.EmailSenderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class PasswordResetEmailEventConsumer {

        private final KafkaTemplate<String, Object> kafkaTemplate;

        public PasswordResetEmailEventConsumer(KafkaTemplate<String, Object> kafkaTemplate) {
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