package com.neueda.trading.engine;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import java.time.Clock;
import java.time.Duration;

@Configuration
public class EngineConfig {
    @Bean
    public LiveMarket liveMarket(@Value("${engine.quote-max-age:60s}") Duration maxAge) {
        if (maxAge.isNegative() || maxAge.isZero() || maxAge.compareTo(Duration.ofSeconds(60)) > 0)
            throw new IllegalArgumentException("Quote age must be positive and no more than 60 seconds");
        return new LiveMarket(Clock.systemUTC(), maxAge);
    }
    @Bean
    public NewTopic ordersTopic(@Value("${engine.topics.orders}") String name,
            @Value("${engine.topics.partitions}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }
    @Bean
    public NewTopic executionsTopic(@Value("${engine.topics.executions}") String name,
            @Value("${engine.topics.partitions}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }
    @Bean
    public KafkaAdmin.NewTopics liveTopics(
            @Value("${engine.topics.orders:orders}") String orders,
            @Value("${engine.topics.market-data:market-data}") String marketData,
            @Value("${engine.topics.order-lifecycle:order-lifecycle}") String lifecycle) {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(marketData).partitions(6).replicas(1).build(),
                TopicBuilder.name(lifecycle).partitions(3).replicas(1).build(),
                TopicBuilder.name(orders + ".DLT").partitions(3).replicas(1).build(),
                TopicBuilder.name(marketData + ".DLT").partitions(3).replicas(1).build());
    }
    @Bean
    public DefaultErrorHandler engineErrorHandler(KafkaTemplate<String, String> kafka) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, error) -> new TopicPartition(record.topic() + ".DLT", -1));
        recoverer.setFailIfSendResultIsError(true);
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000, 4));
        handler.addNotRetryableExceptions(com.fasterxml.jackson.core.JsonProcessingException.class,
                IllegalArgumentException.class);
        return handler;
    }
}
