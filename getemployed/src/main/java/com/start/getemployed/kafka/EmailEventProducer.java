package com.start.getemployed.kafka;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class EmailEventProducer {
    private final KafkaTemplate<String, SendVerifyEmailEvent> kafkaTemplate;

    public void sendVerifyEmail(SendVerifyEmailEvent event) {
        kafkaTemplate.send("email-event", event.userId().toString(), event);
    }
}
