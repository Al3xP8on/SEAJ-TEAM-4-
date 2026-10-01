package com.neueda.leap.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Writes accepted orders to the {@code orders} topic. Sending is
 * fire-and-forget from the caller's point of view: a failed send is only
 * logged, because the order is already safely stored as NEW and
 * {@link PendingOrderRepublisher} will send it again.
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public OrderEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                               @Value("${trading.kafka.topics.orders}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void publish(OrderEvent event) {
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise order " + event.orderId(), e);
        }
        kafkaTemplate.send(topic, event.accountId(), json).whenComplete((result, error) -> {
            if (error != null) {
                log.warn("Failed to publish order {} to {}; it will be retried: {}", event.orderId(), topic, error.getMessage());
            } else {
                log.info("Published order {} to {}-{}@{}", event.orderId(), topic,
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }
}
