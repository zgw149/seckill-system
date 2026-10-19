package org.example.productservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    public static final String PROGRAM_CHANGED_TOPIC = "program.changed";

    @Bean
    public NewTopic programChangedTopic() {
        return TopicBuilder.name(PROGRAM_CHANGED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
