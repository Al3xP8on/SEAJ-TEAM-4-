package com.neueda.leap.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.neueda.leap.messaging.PoisonMessageException;
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
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.backoff.FixedBackOff;

import java.time.DateTimeException;
import java.time.Duration;

@Configuration
public class KafkaErrorHandlingConfig {

    private static final String DLT_SUFFIX = ".DLT";
    private static final int KEY_BASED_PARTITION = -1;
    private static final int DLT_PARTITIONS = 1;
    private static final int DLT_REPLICAS = 1;

    private static final int MAX_RETRIES = 4;
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(1);
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final FixedBackOff NO_RETRIES = new FixedBackOff(0, 0);

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplateString,
            @Value("${trading.kafka.topics.market-data}") String marketData) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplateString,
                (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, KEY_BASED_PARTITION));

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(INITIAL_BACKOFF.toMillis());
        backOff.setMultiplier(BACKOFF_MULTIPLIER);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // A newer price arrives within seconds, so retrying a stale one only delays it
        handler.setBackOffFunction((record, ex) -> marketData.equals(record.topic()) ? NO_RETRIES : null);
        handler.addNotRetryableExceptions(
                PoisonMessageException.class,
                JsonProcessingException.class,
                IllegalArgumentException.class,
                NullPointerException.class,
                DateTimeException.class);
        return handler;
    }

    @Bean
    public KafkaAdmin.NewTopics deadLetterTopics(
            @Value("${trading.kafka.topics.trades}") String trades,
            @Value("${trading.kafka.topics.trade-events}") String tradeEvents,
            @Value("${trading.kafka.topics.market-data}") String marketData,
            @Value("${trading.kafka.topics.executions}") String executions) {
        return new KafkaAdmin.NewTopics(
                deadLetterTopic(trades),
                deadLetterTopic(tradeEvents),
                deadLetterTopic(marketData),
                deadLetterTopic(executions));
    }

    private static NewTopic deadLetterTopic(String sourceTopic) {
        return TopicBuilder.name(sourceTopic + DLT_SUFFIX)
                .partitions(DLT_PARTITIONS)
                .replicas(DLT_REPLICAS)
                .build();
    }
}
