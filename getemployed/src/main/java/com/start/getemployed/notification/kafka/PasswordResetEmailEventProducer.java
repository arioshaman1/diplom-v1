package com.start.getemployed.notification.kafka;


import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasswordResetEmailEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "notification.email";

    public void sendPasswordResetEmail(PasswordResetEmailEvent event ) {
        kafkaTemplate.send(TOPIC, event);
    }
}
