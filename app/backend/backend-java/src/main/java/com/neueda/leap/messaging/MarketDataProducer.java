package com.neueda.leap.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publishes market data events to the Kafka "market-data" topic.
 * Wraps market data in EventEnvelope format for consistent event handling across the platform.
 */
@Component
public class MarketDataProducer {
    private static final Logger logger = LoggerFactory.getLogger(MarketDataProducer.class);
    private static final String MARKET_DATA_TOPIC = "market-data";
    
    private final KafkaTemplate<String, EventEnvelope> kafkaTemplate;
    
    public MarketDataProducer(KafkaTemplate<String, EventEnvelope> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishMarketData(EventEnvelope eventEnvelope) {
        if (eventEnvelope == null) {
            logger.error("Cannot publish null EventEnvelope");
            throw new IllegalArgumentException("EventEnvelope cannot be null");
        }
        
        try {
            String key = extractSymbolFromPayload(eventEnvelope);
            
            kafkaTemplate.send(MARKET_DATA_TOPIC, key, eventEnvelope);
            
            logger.debug("Published market data event {} for symbol {} to topic {}", 
                eventEnvelope.eventId(), key, MARKET_DATA_TOPIC);
                
        } catch (Exception e) {
            logger.error("Error publishing market data event to Kafka", e);
            throw new RuntimeException("Failed to publish market data event", e);
        }
    }
    
    private String extractSymbolFromPayload(EventEnvelope eventEnvelope) {
        Object symbol = eventEnvelope.payload().get("symbol");
        if (symbol == null) {
            return UUID.randomUUID().toString();
        }
        return symbol.toString();
    }
}