package com.neueda.trading.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.trading.engine.ExecutionEvent;
import com.neueda.trading.enums.EventType;
import com.neueda.trading.engine.EventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class TradesEventPublisher {
    
    private static final Logger log = LoggerFactory.getLogger(TradesEventPublisher.class);
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String tradesTopicName;
    
    public TradesEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                ObjectMapper objectMapper,
                                @Value("${engine.topics.trades}") String tradesTopicName) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.tradesTopicName = tradesTopicName;
    }
    
    public void publishExecutionEvent(ExecutionEvent executionEvent) {
        try {
            EventEnvelope envelope = createEventEnvelope(executionEvent);
            String eventJson = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(tradesTopicName, envelope.eventId().toString(), eventJson);
            log.info("Published execution event {} for order {} to trades topic", 
                    envelope.eventId(), executionEvent.orderId());
        } catch (Exception e) {
            log.error("Failed to publish execution event for order {} to trades topic", 
                    executionEvent.orderId(), e);
            throw new RuntimeException("Failed to publish execution event to trades topic", e);
        }
    }
    
    private EventEnvelope createEventEnvelope(ExecutionEvent executionEvent) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("executionId", executionEvent.executionId());
        payload.put("orderId", executionEvent.orderId());
        payload.put("accountId", executionEvent.accountId());
        payload.put("symbol", executionEvent.symbol());
        payload.put("side", executionEvent.side());
        payload.put("quantity", executionEvent.quantity());
        payload.put("price", executionEvent.price());
        payload.put("limitPrice", executionEvent.limitPrice());
        payload.put("venue", executionEvent.venue());
        payload.put("executedOn", executionEvent.executedOn());
        
        return new EventEnvelope(
            UUID.randomUUID(),
            EventType.TRADE_EXECUTED,
            Instant.now(),
            1,
            payload
        );
    }
}
