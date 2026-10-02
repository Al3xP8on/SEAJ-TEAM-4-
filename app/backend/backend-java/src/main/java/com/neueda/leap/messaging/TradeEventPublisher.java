package com.neueda.leap.messaging;


import com.neueda.leap.utils.LogMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for publishing trade events to the trade-events Kafka topic.
 * Events are wrapped in EventEnvelope and keyed by accountId to ensure 
 * all trades for an account go to the same partition, maintaining order.
 */
@Service
public class TradeEventPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(TradeEventPublisher.class);
    
    private final KafkaTemplate<String, EventEnvelope> kafkaTemplate;
    
    @Value("${trading.kafka.topics.trade-events}")
    private String tradeEventsTopic;
    
    public TradeEventPublisher(KafkaTemplate<String, EventEnvelope> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    
    /**
     * Publishes a trade event to the trade-events topic wrapped in EventEnvelope.
     * The event is keyed by accountId to ensure ordering per account.
     * 
     * @param event the TradeEvent to publish
     */
    public void publishTradeEvent(TradeEvent event) {
        logger.info("Publishing trade event for account: {} - Trade ID: {} - Status: {}",
                LogMaskingUtil.maskAccountId(event.getAccountId()), LogMaskingUtil.maskId(event.getTradeId()), event.getStatus());
        
        try {
            // Map TradeEvent status to EventType
            EventType eventType = mapStatusToEventType(event.getStatus());
            
            // Create payload map from TradeEvent
            Map<String, Object> payload = new HashMap<>();
            payload.put("tradeId", event.getTradeId());
            payload.put("accountId", event.getAccountId());
            payload.put("symbol", event.getSymbol());
            payload.put("side", event.getSide());
            payload.put("price", event.getPrice());
            payload.put("quantity", event.getQuantity());
            payload.put("status", event.getStatus());
            payload.put("timeInForce", event.getTimeInForce());
            payload.put("timestamp", event.getTimestamp());
            payload.put("reason", event.getReason());
            
            // Wrap in EventEnvelope
            EventEnvelope envelope = new EventEnvelope(
                    UUID.randomUUID(),
                    eventType,
                    Instant.now(),
                    1,
                    payload
            );
            
            // Send with accountId as Kafka message key to ensure ordering per account
            kafkaTemplate.send(tradeEventsTopic, event.getAccountId(), envelope);
            
            logger.debug("Trade event published successfully in EventEnvelope: {}", envelope);
        } catch (Exception e) {
            logger.error("Failed to publish trade event for account {}: {}", 
                    event.getAccountId(), e.getMessage(), e);
            throw new RuntimeException("Failed to publish trade event", e);
        }
    }
    
    /**
     * Maps TradeEvent status string to EventType enum.
     */
    private EventType mapStatusToEventType(String status) {
        return switch (status) {
            case "INITIATED" -> EventType.ORDER_PLACED;
            case "ACCEPTED" -> EventType.ORDER_PLACED;
            case "EXECUTED" -> EventType.TRADE_EXECUTED;
            case "FILLED" -> EventType.ORDER_FILLED;
            case "REJECTED" -> EventType.ORDER_REJECTED;
            case "CANCELLED" -> EventType.ORDER_CANCELLED;
            default -> EventType.ORDER_PLACED;
        };
    }
}
