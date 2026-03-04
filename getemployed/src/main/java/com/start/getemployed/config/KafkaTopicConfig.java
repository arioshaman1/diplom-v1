package com.start.getemployed.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.requests.OffsetCommitResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic arioShamanTopic(){
        return TopicBuilder.name("sen")
                .build();
    }
}
