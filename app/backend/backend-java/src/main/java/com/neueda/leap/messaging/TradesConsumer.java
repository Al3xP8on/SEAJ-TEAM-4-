package com.neueda.leap.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.models.Order;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.services.OrderPricingValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
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
    private final ObjectMapper objectMapper;
    
    public TradesConsumer(OrderRepository orderRepository, OrderPricingValidator pricingValidator) {
        this.orderRepository = orderRepository;
        this.pricingValidator = pricingValidator;
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
            if (orderId == null) {
                return;
            }
            
            logger.info("Processing ORDER_PLACED - Order ID: {} | Partition: {} | Offset: {}",
                    orderId, partition, offset);
            
            Order order = loadAndValidateOrder(orderId);
            if (order == null) {
                return;
            }
            
            applyPricingValidationAndUpdateOrder(order, orderId);
            
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
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            logger.error("Order not found in database: {}", orderId);
            return null;
        }
        return orderOpt.get();
    }

    private void applyPricingValidationAndUpdateOrder(Order order, UUID orderId) {
        OrderPricingValidator.PricingValidationResult result = pricingValidator.validateOrderPricing(order);
        
        order.setStatus(result.newStatus());
        orderRepository.save(order);
        
        if (result.newStatus() == OrderStatus.PENDING) {
            logger.info("Order {} approved for execution: {}", orderId, result.reason());
        } else {
            logger.warn("Order {} rejected: {}", orderId, result.reason());
        }
        
        logger.debug("Order {} status updated to {}", orderId, order.getStatus());
    }

    private UUID extractOrderId(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            logger.error("Empty payload in ORDER_PLACED event");
            return null;
        }
        
        Object orderIdObj = payload.get("orderId");
        if (orderIdObj == null) {
            logger.error("Missing orderId in ORDER_PLACED payload");
            return null;
        }
        
        if (orderIdObj instanceof String) {
            return UUID.fromString((String) orderIdObj);
        } else if (orderIdObj instanceof UUID) {
            return (UUID) orderIdObj;
        }
        
        logger.error("Invalid orderId type: {}", orderIdObj.getClass());
        return null;
    }
}
