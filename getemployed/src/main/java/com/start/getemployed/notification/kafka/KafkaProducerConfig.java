package com.start.getemployed.notification.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;


@Configuration
public class KafkaProducerConfig {

    @Bean
    public KafkaTemplate<String, EventEnvelope<?>> kafkaTemplate(
            ProducerFactory<String, EventEnvelope<?>> pf) {
        return new KafkaTemplate<>(pf);
    }
}