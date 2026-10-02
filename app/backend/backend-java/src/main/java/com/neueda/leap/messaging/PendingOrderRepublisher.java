package com.neueda.leap.messaging;

import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.enums.TimeInForce;
import com.neueda.leap.models.Order;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.utils.LogMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Safety net for the gap between committing an order and publishing it to Kafka:
 * any order still in NEW status after {@code trading.orders.republish-after} duration
 * is republished to Kafka (e.g., Kafka was down when order was created, or the
 * initial event was lost). The orders table itself acts as the outbox pattern.
 * 
 * Duplicates are harmless because:
 * - Each event has a unique eventId
 * - Listeners use idempotency keys or timestamps to deduplicate
 * - Order status transitions are idempotent (re-applying same status is safe)
 */
@Component
public class PendingOrderRepublisher {

    private static final Logger logger = LoggerFactory.getLogger(PendingOrderRepublisher.class);
    private static final int BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final TradeEventPublisher tradeEventPublisher;
    private final Duration republishAfter;
    private final Duration republishInterval;

    public PendingOrderRepublisher(
            OrderRepository orderRepository,
            TradeEventPublisher tradeEventPublisher,
            @Value("${trading.orders.republish-after:PT5M}") Duration republishAfter,
            @Value("${trading.orders.republish-interval:PT1M}") Duration republishInterval) {
        this.orderRepository = orderRepository;
        this.tradeEventPublisher = tradeEventPublisher;
        this.republishAfter = republishAfter;
        this.republishInterval = republishInterval;
    }

    /**
     * Periodically republish orders that are still in NEW status after the configured delay.
     * This is a safety net for Kafka outages or lost events.
     */
    @Scheduled(fixedDelayString = "${trading.orders.republish-interval:PT1M}",
               initialDelayString = "${trading.orders.republish-interval:PT1M}")
    public void republishStaleOrders() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minus(republishAfter);
            List<Order> staleOrders = orderRepository.findByStatusAndCreatedBefore(OrderStatus.NEW, threshold);

            if (!staleOrders.isEmpty()) {
                logger.warn("Found {} order(s) still NEW after {} - republishing to Kafka", 
                    staleOrders.size(), republishAfter);

                // Republish in batches
                for (int i = 0; i < staleOrders.size(); i += BATCH_SIZE) {
                    int end = Math.min(i + BATCH_SIZE, staleOrders.size());
                    List<Order> batch = staleOrders.subList(i, end);
                    republishBatch(batch);
                }
            }
        } catch (Exception e) {
            logger.error("Error republishing stale orders", e);
            // Don't throw - scheduler should continue running even if one cycle fails
        }
    }

    /**
     * Republish a batch of orders to Kafka.
     */
    private void republishBatch(List<Order> orders) {
        for (Order order : orders) {
            try {
                // Convert Order JPA entity to TradeEvent
                TradeEvent tradeEvent = new TradeEvent(
                    order.getId(),
                    order.getAccount().getAccountId(),
                    order.getInstrument().getSymbol(),
                    order.getSide().toString(),
                    order.getPrice(),
                    order.getQuantity(),
                    "INITIATED",  // Republishing as initial event
                    TimeInForce.GTC.toString(),  // Default to GTC if not specified in Order
                    order.getCreatedAt()
                );

                // Publish via TradeEventPublisher (which wraps in EventEnvelope)
                tradeEventPublisher.publishTradeEvent(tradeEvent);
                
                logger.debug("Republished order {} for account {}", 
                    LogMaskingUtil.maskId(order.getId()), LogMaskingUtil.maskAccountId(order.getAccount().getAccountId()));
            } catch (Exception e) {
                logger.error("Failed to republish order {}", LogMaskingUtil.maskId(order.getId()), e);
                // Continue with next order even if one fails
            }
        }
    }
}
