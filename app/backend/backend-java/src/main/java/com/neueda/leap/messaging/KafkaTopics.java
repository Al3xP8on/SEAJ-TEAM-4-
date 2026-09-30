package com.neueda.trading.app.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the two topics. Spring's KafkaAdmin creates them on startup if
 * they don't exist yet (the broker's own auto-create is switched off), so
 * whichever service starts first sets them up.
 */
@Configuration
public class KafkaTopics {

    @Bean
    public NewTopic ordersTopic(@Value("${trading.kafka.topics.orders}") String name,
                                @Value("${trading.kafka.partitions}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic executionsTopic(@Value("${trading.kafka.topics.executions}") String name,
                                    @Value("${trading.kafka.partitions}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }
}
