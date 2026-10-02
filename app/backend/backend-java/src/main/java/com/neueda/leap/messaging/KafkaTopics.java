package com.neueda.leap.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares Kafka topics for the trading platform.
 * Spring's KafkaAdmin creates them on startup if they don't exist yet.
 * 
 * Topics:
 * - trades: Trade placement events (2 partitions, keyed by accountId)
 * - trade-events: Trade status updates (3 partitions, keyed by accountId)
 * - market-data: Market data updates (6 partitions, keyed by symbol)
 */
@Configuration
public class KafkaTopics {

    @Bean
    public NewTopic tradesTopic(@Value("${trading.kafka.topics.trades}") String name,
                               @Value("${trading.kafka.partitions.trades}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic tradeEventsTopic(@Value("${trading.kafka.topics.trade-events}") String name,
                                     @Value("${trading.kafka.partitions.trade-events}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }

    @Bean
    public NewTopic marketDataTopic(@Value("${trading.kafka.topics.market-data}") String name,
                                    @Value("${trading.kafka.partitions.market-data}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }
}
