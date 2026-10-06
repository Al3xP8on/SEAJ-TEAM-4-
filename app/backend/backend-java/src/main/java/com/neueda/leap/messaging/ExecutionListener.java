package com.neueda.leap.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Reads fills from the {@code executions} topic from the execution-engine.
 * Updates order status to FILLED and adjusts account cash balance (debit for BUY, credit for SELL).
 * A message that can't be settled is sent to the executions dead-letter topic.
 */
@Component
public class ExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ExecutionListener.class);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private TradeEventPublisher tradeEventPublisher;

    @KafkaListener(topics = "${trading.kafka.topics.executions}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onExecution(String message) throws JsonProcessingException {
        ExecutionEvent event = objectMapper.readValue(message, ExecutionEvent.class);
        settle(event);
    }

    private void settle(ExecutionEvent event) {
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> new PoisonMessageException("Order not found for execution: " + event.orderId()));
        Account account = accountRepository.findByAccountId(event.accountId())
                .orElseThrow(() -> new PoisonMessageException("Account not found for execution: " + event.accountId()));

        // Only settle if order is still NEW or PENDING (at-least-once delivery safety)
        // PENDING means it was accepted but not yet filled
        if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.PENDING) {
            log.info("Order {} is already {}, skipping duplicate execution", LogMaskingUtil.maskId(event.orderId()), order.getStatus());
            return;
        }

        // Calculate execution amount
        BigDecimal executionAmount = event.price().multiply(BigDecimal.valueOf(event.quantity()));

        // Update cash balance based on side
        try {
            if ("BUY".equalsIgnoreCase(event.side().toString())) {
                account.debit(executionAmount);
            } else if ("SELL".equalsIgnoreCase(event.side().toString())) {
                account.credit(executionAmount);
            }
        } catch (IllegalArgumentException e) {
            throw new PoisonMessageException("Cannot settle execution for order " + event.orderId(), e);
        }

        accountRepository.save(account);

        // Update order status to FILLED
        order.setStatus(OrderStatus.FILLED);
        orderRepository.save(order);

        log.info("Execution settled: Order {} FILLED | {} {} @ {} | Account {} balance: ${} | Venue: {}",
                LogMaskingUtil.maskId(event.orderId()), event.side(), event.quantity(), event.price(),
                LogMaskingUtil.maskAccountId(event.accountId()), account.getCashBalance(), event.venue());
        
        // Publish TRADE_EXECUTED event first (order matched by execution engine)
        try {
            TradeEvent executedEvent = new TradeEvent(
                event.orderId(),
                event.accountId(),
                order.getInstrument().getSymbol(),
                event.side().toString(),
                event.price(),
                event.quantity(),
                "EXECUTED",
                "GTC",
                LocalDateTime.now()
            );
            tradeEventPublisher.publishTradeEvent(executedEvent);
            log.info("Published TRADE_EXECUTED event for order: {}", LogMaskingUtil.maskId(event.orderId()));
        } catch (Exception e) {
            log.error("Failed to publish TRADE_EXECUTED event for order {}: {}", 
                LogMaskingUtil.maskId(event.orderId()), e.getMessage(), e);
            // Don't fail the execution if event publishing fails
        }
        
        // Publish ORDER_FILLED event to Kafka for complete audit trail
        try {
            TradeEvent filledEvent = new TradeEvent(
                event.orderId(),
                event.accountId(),
                order.getInstrument().getSymbol(),
                event.side().toString(),
                event.price(),
                event.quantity(),
                "FILLED",
                "GTC",
                LocalDateTime.now()
            );
            
            tradeEventPublisher.publishTradeEvent(filledEvent);
            log.info("Published ORDER_FILLED event for order: {}", LogMaskingUtil.maskId(event.orderId()));
        } catch (Exception e) {
            log.error("Failed to publish ORDER_FILLED event for order {}: {}", 
                LogMaskingUtil.maskId(event.orderId()), e.getMessage(), e);
            // Don't fail the execution if event publishing fails
        }
    }
}
