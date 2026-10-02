package com.neueda.leap.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes accepted orders to the {@code orders} topic. Sending is
 * fire-and-forget from the caller's point of view: a failed send is only
 * logged, because the order is already safely stored as NEW and
 * {@link PendingOrderRepublisher} will send it again.
 * 
 * Also publishes ORDER_PLACED event to trades topic for audit trail.
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaTemplate<String, EventEnvelope> kafkaTemplateEnvelope;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final String tradesTopic;

    public OrderEventPublisher(KafkaTemplate<String, String> kafkaTemplate, 
                               KafkaTemplate<String, EventEnvelope> kafkaTemplateEnvelope,
                               ObjectMapper objectMapper,
                               @Value("${trading.kafka.topics.orders}") String topic,
                               @Value("${trading.kafka.topics.trades}") String tradesTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTemplateEnvelope = kafkaTemplateEnvelope;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.tradesTopic = tradesTopic;
    }

    public void publish(OrderEvent event) {
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise order " + event.orderId(), e);
        }
        
        // Publish to orders topic
        kafkaTemplate.send(topic, event.accountId(), json).whenComplete((result, error) -> {
            if (error != null) {
                log.warn("Failed to publish order {} to {}; it will be retried: {}", event.orderId(), topic, error.getMessage());
            } else {
                log.info("Published order {} to {}-{}@{}", event.orderId(), topic,
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
        
        // Also publish ORDER_PLACED event to trades topic with EventEnvelope
        publishOrderPlacedToTrades(event);
    }
    
    /**
     * Publish ORDER_PLACED event to trades topic with full EventEnvelope metadata.
     */
    private void publishOrderPlacedToTrades(OrderEvent event) {
        try {
            // Build payload with order details
            Map<String, Object> payload = new HashMap<>();
            payload.put("orderId", event.orderId().toString());
            payload.put("accountId", event.accountId());
            payload.put("symbol", event.symbol());
            payload.put("side", event.side());
            payload.put("quantity", event.quantity());
            payload.put("price", event.price().toString());
            payload.put("createdOn", event.createdOn().toString());
            
            // Wrap in EventEnvelope
            EventEnvelope envelope = new EventEnvelope(
                    UUID.randomUUID(),
                    EventType.ORDER_PLACED,
                    Instant.now(),
                    1,
                    payload
            );
            
            kafkaTemplateEnvelope.send(tradesTopic, event.orderId().toString(), envelope)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.warn("Failed to publish ORDER_PLACED {} to trades topic: {}", 
                            event.orderId(), error.getMessage());
                    } else {
                        log.info("Published ORDER_PLACED {} to trades topic partition {} offset {}",
                            event.orderId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                    }
                });
        } catch (Exception e) {
            log.error("Error publishing ORDER_PLACED event for order {}: {}", event.orderId(), e.getMessage());
        }
    }
}
