package com.start.getemployed.notification.kafka;


import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public NewTopic notificationEmailTopic() {
        return TopicBuilder.name("notification.email")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
