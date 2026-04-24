package com.start.getemployed.notification.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.start.getemployed.notification.kafka.EventEnvelope;
import com.start.getemployed.notification.kafka.PasswordResetEmailEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class EmailEventConsumer {

    private final ObjectMapper objectMapper;

    public EmailEventConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "notification.email",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(EventEnvelope<JsonNode> event) {

        switch (event.getType()) {

            case "PASSWORD_RESET" -> {
                PasswordResetEmailEvent payload =
                        objectMapper.convertValue(
                                event.getPayload(),
                                PasswordResetEmailEvent.class
                        );

                handlePasswordReset(payload);
            }

            default -> throw new IllegalArgumentException(
                    "Unknown event type: " + event.getType()
            );
        }
    }

    private void handlePasswordReset(PasswordResetEmailEvent event) {
        System.out.println("Send reset email to: " + event.getEmail());
    }
}