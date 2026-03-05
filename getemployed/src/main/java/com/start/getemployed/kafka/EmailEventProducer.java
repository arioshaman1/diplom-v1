package com.start.getemployed.kafka;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class EmailEventProducer {
    private final KafkaTemplate<String, SendVerifyEmailEvent> kafkaTemplate;

    @Value("${app.kafka.topics.email-event:email-event}")
    private String topic;

    public void sendVerifyEmail(SendVerifyEmailEvent event) {
        Logger log = LoggerFactory.getLogger(EmailEventProducer.class);
        log.info("Sending email event to topic {}", topic);
        kafkaTemplate.send(topic, event.email(), event)
                .whenComplete((res, ex) -> {
                    if (ex != null) {
                        log.error("Kafka send FAILED", ex);
                    } else {
                        var m = res.getRecordMetadata();
                        log.info("Kafka send OK topic={} partition={} offset={}",
                                m.topic(), m.partition(), m.offset());
                    }
                });
    }
}
