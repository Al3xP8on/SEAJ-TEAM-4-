package com.neueda.leap.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Listens for market data events from Kafka and processes them.
 * Updates the LatestPriceStore with incoming market data.
 */
@Component
public class MarketDataConsumer {
    private static final Logger logger = LoggerFactory.getLogger(MarketDataConsumer.class);
    
    private final LatestPriceStore latestPriceStore;
    private final ObjectMapper objectMapper;
    
    public MarketDataConsumer(LatestPriceStore latestPriceStore) {
        this.latestPriceStore = latestPriceStore;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    @KafkaListener(topics = "market-data", groupId = "market-data-consumer-group")
    public void consumeMarketData(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_KEY) String symbol) {
        
        if (message == null || message.isEmpty()) {
            logger.error("Received empty message from Kafka");
            return;
        }
        
        try {
            // Deserialize JSON string to EventEnvelope
            EventEnvelope eventEnvelope = objectMapper.readValue(message, EventEnvelope.class);
            
            if (eventEnvelope == null) {
                logger.error("Failed to deserialize EventEnvelope from message");
                return;
            }
            
            // Extract payload data
            Map<String, Object> payload = eventEnvelope.payload();
            
            if (payload == null || payload.isEmpty()) {
                logger.error("Empty payload in EventEnvelope");
                return;
            }
            
            // Create MarketPrice from payload
            String payloadSymbol = (String) payload.get("symbol");
            Object bidPrice = payload.get("bidPrice");
            Object askPrice = payload.get("askPrice");
            Object lastPrice = payload.get("lastPrice");
            Object timestamp = payload.get("priceTimeStamp");
            
            if (payloadSymbol == null || bidPrice == null || askPrice == null || lastPrice == null) {
                logger.error("Missing required fields in market data payload");
                return;
            }
            
            // Convert objects to appropriate types
            MarketPrice marketPrice = new MarketPrice(
                payloadSymbol,
                toBigDecimal(bidPrice),
                toBigDecimal(askPrice),
                toBigDecimal(lastPrice),
                toInstant(timestamp)
            );
            
            // Store the latest price
            latestPriceStore.updatePrice(marketPrice);
            
            logger.debug("Consumed market data event {} for symbol {}",
                eventEnvelope.eventId(), payloadSymbol);
                
        } catch (Exception e) {
            logger.error("Error processing market data event", e);
        }
    }
    
    private java.math.BigDecimal toBigDecimal(Object value) {
        if (value instanceof java.math.BigDecimal bd) {
            return bd;
        } else if (value instanceof Number num) {
            return java.math.BigDecimal.valueOf(num.doubleValue());
        } else if (value instanceof String str) {
            return new java.math.BigDecimal(str);
        }
        throw new IllegalArgumentException("Cannot convert to BigDecimal: " + value);
    }
    
    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        } else if (value instanceof Long timestamp) {
            return Instant.ofEpochMilli(timestamp);
        } else if (value instanceof Double dbl) {
            return Instant.ofEpochMilli(dbl.longValue());
        } else if (value instanceof String str) {
            return Instant.parse(str);
        }
        return Instant.now();
    }
}