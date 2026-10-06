package com.neueda.leap.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.models.Order;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.services.OrderPricingValidator;
import com.neueda.leap.utils.LogMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes ORDER_PLACED events from the trades topic and validates pricing.
 * 
 * For each ORDER_PLACED event:
 * 1. Loads the full Order from database
 * 2. Checks instrument is tradeable
 * 3. Gets live quote from market-data cache (LatestPriceStore)
 * 4. Applies pricing validation rule (pure function)
 * 5. Updates order status: NEW → PENDING (can execute) or NEW → REJECTED (cannot execute)
 * 6. Ensures no order ever stays in NEW status
 * 
 * Uses consumer group "trades-consumer-group" for independent offset tracking.
 */
@Component
public class TradesConsumer {
    
    private static final Logger logger = LoggerFactory.getLogger(TradesConsumer.class);
    
    private final OrderRepository orderRepository;
    private final OrderPricingValidator pricingValidator;
    private final TradeEventPublisher tradeEventPublisher;
    private final ObjectMapper objectMapper;
    
    public TradesConsumer(OrderRepository orderRepository, OrderPricingValidator pricingValidator, TradeEventPublisher tradeEventPublisher) {
        this.orderRepository = orderRepository;
        this.pricingValidator = pricingValidator;
        this.tradeEventPublisher = tradeEventPublisher;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * Listens for ORDER_PLACED events on the trades topic.
     * Consumes EventEnvelope messages and validates pricing.
     * 
     * @param message the JSON string containing EventEnvelope
     * @param partition the Kafka partition the message came from
     * @param offset the offset of the message in the partition
     */
    @KafkaListener(
            topics = "${trading.kafka.topics.trades}",
            groupId = "trades-consumer-group",
            concurrency = "3"
    )
    @Transactional
    public void handleOrderPlaced(
            @Payload String message,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {
        
        try {
            EventEnvelope envelope = deserializeMessage(message);
            
            logger.info("Received EventEnvelope - Event ID: {} | Event Type: {} | Partition: {} | Offset: {}",
                    envelope.eventId(), envelope.eventType(), partition, offset);
            
            if (!isOrderPlacedEvent(envelope)) {
                return;
            }
            
            UUID orderId = extractOrderId(envelope.payload());
            
            logger.info("Processing ORDER_PLACED - Order ID: {} | Partition: {} | Offset: {}",
                    orderId, partition, offset);
            
            Order order = loadAndValidateOrder(orderId);

            // Only validate orders still NEW, so a retried or redelivered event can't overwrite a later status
            if (order.getStatus() != OrderStatus.NEW) {
                logger.info("Order {} is already {}, skipping duplicate ORDER_PLACED",
                        LogMaskingUtil.maskId(orderId), order.getStatus());
                return;
            }

            applyPricingValidationAndUpdateOrder(order, orderId, envelope.payload());
            
        } catch (Exception e) {
            logger.error("Failed to process ORDER_PLACED event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process ORDER_PLACED event", e);
        }
    }

    private EventEnvelope deserializeMessage(String message) throws Exception {
        return objectMapper.readValue(message, EventEnvelope.class);
    }

    private boolean isOrderPlacedEvent(EventEnvelope envelope) {
        if (envelope.eventType() != EventType.ORDER_PLACED) {
            logger.debug("Ignoring event type: {}", envelope.eventType());
            return false;
        }
        return true;
    }

    private Order loadAndValidateOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new PoisonMessageException("Order not found in database: " + orderId));
    }

    private void applyPricingValidationAndUpdateOrder(Order order, UUID orderId, Map<String, Object> payload) {
        OrderPricingValidator.PricingValidationResult result = pricingValidator.validateOrderPricing(order);
        
        order.setStatus(result.newStatus());
        orderRepository.save(order);
        
        if (result.newStatus() == OrderStatus.PENDING) {
            logger.info("Order {} approved for execution: {}", LogMaskingUtil.maskId(orderId), result.reason());
            // Publish ORDER_ACCEPTED event to trade-events topic
            publishOrderAccepted(order, orderId, payload);
        } else if (result.newStatus() == OrderStatus.REJECTED) {
            logger.warn("Order {} rejected: {}", LogMaskingUtil.maskId(orderId), result.reason());
            // Publish ORDER_REJECTED event to trade-events topic
            publishOrderRejected(order, orderId, payload, result.reason());
        }
        
        logger.debug("Order {} status updated to {}", LogMaskingUtil.maskId(orderId), order.getStatus());
    }
    
    /**
     * Publishes ORDER_ACCEPTED event to trade-events topic when order passes pricing validation.
     */
    private void publishOrderAccepted(Order order, UUID orderId, Map<String, Object> payload) {
        try {
            String accountId = (String) payload.get("accountId");
            String symbol = (String) payload.get("symbol");
            
            if (accountId == null || symbol == null) {
                logger.warn("Missing accountId or symbol in ORDER_PLACED payload for order {}", LogMaskingUtil.maskId(orderId));
                return;
            }
            
            String side = payload.get("side").toString();
            BigDecimal price = new BigDecimal((String) payload.get("price"));
            int quantity = ((Number) payload.get("quantity")).intValue();
            
            TradeEvent acceptedEvent = new TradeEvent(
                orderId,
                accountId,
                symbol,
                side,
                price,
                quantity,
                "ACCEPTED",
                "GTC",
                LocalDateTime.now()
            );
            
            tradeEventPublisher.publishTradeEvent(acceptedEvent);
            logger.info("Published ORDER_ACCEPTED event for order: {}", LogMaskingUtil.maskId(orderId));
        } catch (Exception e) {
            logger.error("Failed to publish ORDER_ACCEPTED event for order {}: {}", LogMaskingUtil.maskId(orderId), e.getMessage(), e);
        }
    }
    
    /**
     * Publishes ORDER_REJECTED event to trade-events topic when order fails pricing validation.
     */
    private void publishOrderRejected(Order order, UUID orderId, Map<String, Object> payload, String reason) {
        try {
            String accountId = (String) payload.get("accountId");
            String symbol = (String) payload.get("symbol");
            
            if (accountId == null || symbol == null) {
                logger.warn("Missing accountId or symbol in ORDER_PLACED payload for order {}", LogMaskingUtil.maskId(orderId));
                return;
            }
            
            String side = payload.get("side").toString();
            BigDecimal price = new BigDecimal((String) payload.get("price"));
            int quantity = ((Number) payload.get("quantity")).intValue();
            
            TradeEvent rejectedEvent = new TradeEvent(
                orderId,
                accountId,
                symbol,
                side,
                price,
                quantity,
                "REJECTED",
                "GTC",
                LocalDateTime.now()
            );
            
            tradeEventPublisher.publishTradeEvent(rejectedEvent);
            logger.info("Published ORDER_REJECTED event for order: {} - Reason: {}", LogMaskingUtil.maskId(orderId), reason);
        } catch (Exception e) {
            logger.error("Failed to publish ORDER_REJECTED event for order {}: {}", LogMaskingUtil.maskId(orderId), e.getMessage(), e);
        }
    }

    private UUID extractOrderId(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            throw new PoisonMessageException("Empty payload in ORDER_PLACED event");
        }
        
        Object orderIdObj = payload.get("orderId");
        if (orderIdObj == null) {
            throw new PoisonMessageException("Missing orderId in ORDER_PLACED payload");
        }
        
        if (orderIdObj instanceof String) {
            return UUID.fromString((String) orderIdObj);
        } else if (orderIdObj instanceof UUID) {
            return (UUID) orderIdObj;
        }
        
        throw new PoisonMessageException("Invalid orderId type: " + orderIdObj.getClass());
    }
}
