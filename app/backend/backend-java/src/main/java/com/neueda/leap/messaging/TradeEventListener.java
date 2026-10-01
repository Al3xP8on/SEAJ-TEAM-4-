package com.neueda.leap.messaging;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.models.Account;
import com.neueda.leap.models.Order;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.utils.LogMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for consuming trade events from the trade-events Kafka topic.
 * 
 * Consumes EventEnvelope messages and processes them based on EventType.
 * Uses consumer group "trade-event-processor" to track offset independently.
 * Processes events in order per accountId (due to partitioning strategy).
 */
@Service
public class TradeEventListener {
    
    private static final Logger logger = LoggerFactory.getLogger(TradeEventListener.class);
    
    private final ObjectMapper objectMapper;
    
    public TradeEventListener() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private AccountRepository accountRepository;
    
    /**
     * Listens for trade events on the trade-events topic.
     * Consumes EventEnvelope as JSON String and deserializes manually.
     * 
     * @param message the JSON string containing EventEnvelope
     * @param partition the Kafka partition the message came from
     * @param offset the offset of the message in the partition
     */
    @KafkaListener(
            topics = "${trading.kafka.topics.trade-events}",
            groupId = "trade-event-processor",
            concurrency = "3"
    )
    public void handleTradeEvent(
            @Payload String message,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {
        
        try {
            // Deserialize JSON string to EventEnvelope
            EventEnvelope envelope = objectMapper.readValue(message, EventEnvelope.class);
            
            logger.info("Received EventEnvelope - Event ID: {} | Event Type: {} | Partition: {} | Offset: {}",
                    LogMaskingUtil.maskId(envelope.eventId().toString()), envelope.eventType(), partition, offset);
            
            // Extract TradeEvent from payload
            TradeEvent event = extractTradeEvent(envelope.payload());
            
            logger.info("Processing trade event - Account: {} | Trade ID: {} | Status: {} | Partition: {} | Offset: {}",
                    LogMaskingUtil.maskAccountId(event.getAccountId()), LogMaskingUtil.maskId(event.getTradeId()), event.getStatus(), partition, offset);
            
            // Process the trade event based on EventType
            switch (envelope.eventType()) {
                case ORDER_PLACED:
                    handleInitiatedTrade(event);
                    break;
                case ORDER_CANCELLED:
                    handleCancelledTrade(event);
                    break;
                case PRICE_UPDATE:
                    logger.debug("Price update event received - not processed here");
                    break;
                default:
                    logger.warn("Unknown event type: {}", envelope.eventType());
            }
            
            logger.debug("Trade event processed successfully: {}", event);
        } catch (Exception e) {
            logger.error("Failed to process trade event: {}",
                    e.getMessage(), e);
            // Consider implementing a dead-letter queue for failed events
            throw new RuntimeException("Failed to process trade event", e);
        }
    }
    
    /**
     * Extracts TradeEvent from EventEnvelope payload map.
     */
    private TradeEvent extractTradeEvent(Map<String, Object> payload) {
        // Parse tradeId from String UUID
        Object tradeIdObj = payload.get("tradeId");
        UUID tradeId = tradeIdObj instanceof UUID 
            ? (UUID) tradeIdObj 
            : UUID.fromString((String) tradeIdObj);
        
        // Parse price - Jackson may deserialize as Double, Long, or BigDecimal
        Object priceObj = payload.get("price");
        BigDecimal price = null;
        if (priceObj instanceof BigDecimal) {
            price = (BigDecimal) priceObj;
        } else if (priceObj instanceof Number) {
            price = new BigDecimal(((Number) priceObj).doubleValue());
        } else if (priceObj instanceof String) {
            price = new BigDecimal((String) priceObj);
        }
        
        // Parse timestamp - Jackson may deserialize as List (nanosecond array), String, or LocalDateTime
        Object timestampObj = payload.get("timestamp");
        LocalDateTime timestamp = null;
        if (timestampObj instanceof LocalDateTime) {
            timestamp = (LocalDateTime) timestampObj;
        } else if (timestampObj instanceof String) {
            timestamp = LocalDateTime.parse((String) timestampObj);
        } else if (timestampObj instanceof List) {
            // Handle Jackson's nanosecond array format: [year, month, day, hour, minute, second, nanoOfSecond]
            List<?> components = (List<?>) timestampObj;
            if (components.size() >= 6) {
                timestamp = LocalDateTime.of(
                    ((Number) components.get(0)).intValue(),  // year
                    ((Number) components.get(1)).intValue(),  // month
                    ((Number) components.get(2)).intValue(),  // day
                    ((Number) components.get(3)).intValue(),  // hour
                    ((Number) components.get(4)).intValue(),  // minute
                    ((Number) components.get(5)).intValue()   // second
                );
            }
        }
        
        TradeEvent event = new TradeEvent(
                tradeId,
                (String) payload.get("accountId"),
                (String) payload.get("symbol"),
                (String) payload.get("side"),
                price,
                ((Number) payload.get("quantity")).longValue(),
                (String) payload.get("status"),
                (String) payload.get("timeInForce"),
                timestamp
        );
        event.setReason((String) payload.get("reason"));
        return event;
    }
    
    @Transactional
    private void handleInitiatedTrade(TradeEvent event) {
        logger.info("Processing INITIATED trade: {} for account {}", 
                event.getTradeId(), event.getAccountId());
        
        try {
            // Fetch order and account
            Optional<Order> orderOpt = orderRepository.findById(event.getTradeId());
            Optional<Account> accountOpt = accountRepository.findByAccountId(event.getAccountId());
            
            if (orderOpt.isEmpty() || accountOpt.isEmpty()) {
                logger.error("Order or Account not found for trade {}", event.getTradeId());
                return;
            }
            
            Order order = orderOpt.get();
            Account account = accountOpt.get();
            
            // Validate account is active
            if (!account.isValidForTrading()) {
                logger.warn("Account {} is not valid for trading", LogMaskingUtil.maskAccountId(event.getAccountId()));
                return;
            }
            
            // Check if account has sufficient cash for BUY orders
            BigDecimal requiredCash = event.getPrice().multiply(BigDecimal.valueOf(event.getQuantity()));
            if ("BUY".equals(event.getSide()) && account.getCashBalance().compareTo(requiredCash) < 0) {
                logger.warn("Insufficient cash for order {}. Required: {}, Available: {}", 
                        event.getTradeId(), requiredCash, account.getCashBalance());
                // Could publish REJECTED event here, but for now just log
                return;
            }
            
            // Update order status to PENDING (accepted by system)
            order.setStatus(OrderStatus.PENDING);
            orderRepository.save(order);
            
            logger.info("Order {} transitioned to PENDING status", event.getTradeId());
            
        } catch (Exception e) {
            logger.error("Error processing INITIATED trade {}", event.getTradeId(), e);
        }
    }

    @Transactional
    private void handleCancelledTrade(TradeEvent event) {
        logger.info("Processing CANCELLED trade: {} for account {} - Reason: {}", 
                event.getTradeId(), event.getAccountId(), event.getReason());
        
        try {
            Optional<Order> orderOpt = orderRepository.findById(event.getTradeId());
            if (orderOpt.isEmpty()) {
                logger.error("Order not found: {}", event.getTradeId());
                return;
            }
            
            Order order = orderOpt.get();
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            
            logger.info("Order {} marked as CANCELLED. Reason: {}", event.getTradeId(), event.getReason());
            
        } catch (Exception e) {
            logger.error("Error processing CANCELLED trade {}", event.getTradeId(), e);
        }
    }
}
